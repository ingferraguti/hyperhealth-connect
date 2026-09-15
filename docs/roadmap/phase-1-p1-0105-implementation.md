# P1-0105 — Secret reference senza valore segreto nel database o negli export

| Campo | Valore |
|---|---|
| Work package | WP1-01 |
| Stato | IMPLEMENTED |
| Data di completamento | 15 settembre 2026 |
| Ambito | Platform DB e persistence boundary del Control Plane |
| Owner | Platform Engineering / Product Security |
| Requisiti | FR-TEN-001, NFR-SEC-003, NFR-BC-001, NFR-AUD-001 |
| Decisioni | ADR-003, ADR-007, ADR-008, ADR-009, ADR-025, ADR-028 |
| Dati di test | esclusivamente sintetici |

## 1. Obiettivo e risultato

P1-0105 introduce una reference tipizzata e scoped verso credenziali conservate fuori da HyperHealth Connect. Il Platform DB non è un vault e il Control Plane non riceve, serializza o esporta materiale segreto. La soluzione consegnata:

1. assegna a ogni reference un ID logico immutabile `sref-<uuid>`;
2. deriva Tenant, Organization e Facility da una Facility già verificata, senza accettare ancestry dal chiamante;
3. ammette soltanto provider, purpose e stati appartenenti a vocabolari chiusi;
4. conserva nel DB un `backend_binding_id` UUID opaco, risolvibile esclusivamente dalla configurazione locale dell'ambiente;
5. non conserva path del provider, URI, nome del secret, versione, ciphertext, hash del valore, password, token, chiave o certificato;
6. lega una reference a un Endpoint usando l'intera ancestry e consente overlap durante una rotazione;
7. esclude reference revocate dalle letture utilizzabili e dagli export runtime;
8. esporta soltanto metadata portabili e impone `requiresRebinding=true`;
9. applica gli stessi predicati Tenant/Facility di P1-0103 e rende indistinguibili reference assente e non visibile;
10. protegge reference e binding da modifica di identità e hard delete.

La reference non equivale al secret e non permette di recuperarlo senza tre condizioni esterne: binding locale, workload identity autorizzata e policy del secret manager. Un dump del Platform DB o un export di configurazione non contiene né il valore né un locator direttamente utilizzabile contro il provider.

P1-0105 non completa WP1-01 o Gate G2. L'API amministrativa completa, il journal audit e la matrice negativa estesa restano rispettivamente P1-0106, P1-0107 e P1-0108. Il resolver del Data Plane e gli adapter dei provider saranno qualificati nei work package connector/runtime e deployment.

## 2. Confini di fiducia

```text
Control Plane/API futura
        │ metadata scoped, mai secret value
        ▼
Platform DB
  sref UUID + provider enum + purpose enum + binding UUID locale
        │ export: binding UUID omesso, requiresRebinding=true
        ▼
bundle/runtime metadata firmati
        │ sref logico
        ▼
Runtime Cell della Facility
        │ binding locale + workload identity
        ▼
secret manager qualificato
        │ valore solo al consumer autorizzato, per il tempo minimo
        ▼
connector/processo isolato
```

Il Platform DB conosce l'esistenza e la destinazione funzionale della reference, ma non il valore. La mappa `backend_binding_id → locator provider` appartiene al deployment locale ed è esclusa dal DB applicativo, dai bundle portabili, dagli export, dai log e dagli artifact CI. Il runtime non trasmette il materiale al Control Plane.

### 2.1 Dati ammessi

| Campo | Tipo | Finalità | Portabile |
|---|---|---|---:|
| `secret_reference_id` | UUID non nil | identità logica immutabile | sì, come `sref-<uuid>` |
| ancestry | UUID Tenant/Organization/Facility | isolamento e policy | solo nel contesto scoped, non nell'export P1-0105 |
| `provider_kind` | enum chiuso | selezione adapter locale | sì |
| `backend_binding_id` | UUID non nil | lookup indiretto nella configurazione locale | no |
| `purpose` | enum chiuso | impedire uso della reference per uno scopo diverso | sì |
| `reference_state` | enum chiuso | lifecycle operativo | sì |
| `row_version` | intero non negativo | optimistic concurrency futuro | sì |
| timestamp tecnici | UTC/timestamptz | lifecycle e riconciliazione | non nell'export P1-0105 |

### 2.2 Dati vietati

Sono vietati in entity, colonne, DTO di export, eccezioni e diagnostica:

- password, PIN, passphrase e connection string con credenziali;
- bearer token, API key, refresh token e client secret;
- private key, seed, key-encryption-key e materiale HSM;
- certificato client o bundle PKCS#12 quando contiene materialmente la credenziale;
- valore plaintext, cifrato, codificato Base64 o frammentato;
- hash, digest o fingerprint calcolato dal secret quando può aiutare correlazione o attacco offline;
- path, ARN, resource name, vault URI, namespace o version locator del provider;
- varianti del valore in metadata liberi, display name o label;
- placeholder che possa essere interpretato come credenziale funzionante.

Un UUID casuale non è un contenitore generico: `backend_binding_id` è validato come UUID non nil e non può essere aggiornato. Non esistono campi testuali liberi nel modello di reference capaci di aggirare questo limite.

## 3. Modello tipizzato

### 3.1 Identità

`SecretReferenceId` è un value object UUID con forma esterna canonica `sref-<uuid-lowercase>`. Il parser rifiuta UUID nudo, prefisso errato, forma non canonica e nil UUID. Rename del provider o rotazione della credenziale non cambiano l'identità logica.

### 3.2 Provider

Il vocabolario P1-0105 ammette:

- `HASHICORP_VAULT`;
- `AWS_SECRETS_MANAGER`;
- `AZURE_KEY_VAULT`;
- `GCP_SECRET_MANAGER`;
- `KUBERNETES_SECRETS_STORE_CSI`;
- `EXTERNAL_BROKER`.

L'enum indica una capability family, non dichiara che ogni prodotto sia già integrato o qualificato. L'aggiunta di un provider richiede nuova migration forward, adapter, threat review, test contract, HA/DR qualification e aggiornamento della matrice di supporto.

### 3.3 Purpose

Il purpose chiuso separa almeno:

- `ENDPOINT_BASIC_AUTH`;
- `ENDPOINT_API_TOKEN`;
- `OAUTH_CLIENT_CREDENTIAL`;
- `TLS_CLIENT_PRIVATE_KEY`;
- `TLS_CLIENT_CERTIFICATE`;
- `DATABASE_CREDENTIAL`.

Una reference per token API non può essere collegata come certificato senza creare una nuova reference conforme. Il purpose fa parte della foreign key del binding Endpoint e riduce confused-deputy e riuso involontario.

### 3.4 Stato

| Stato | Semantica | Lettura runtime |
|---|---|---:|
| `ACTIVE` | reference corrente e utilizzabile | sì |
| `ROTATING` | reference utilizzabile nella finestra controllata di transizione | sì |
| `REVOKED` | reference non più risolvibile | no |

`REVOKED` richiede `revoked_at`; gli altri stati lo vietano. Il passaggio è accompagnato da incremento `row_version`. L'hard delete è rifiutato per conservare integrità referenziale e possibilità di audit; l'evento audit applicativo sarà aggiunto da P1-0107.

## 4. Schema SQL V004

La migration additiva `V004__expand__scoped_secret_references.sql` crea:

### 4.1 `platform_core.secret_reference`

La tabella conserva esclusivamente colonne tipizzate indicate nella sezione 2.1. La foreign key composita verso Facility verifica insieme `tenant_id`, `organization_id` e `facility_id`; non è quindi possibile attribuire la reference a una Facility con ancestry diversa.

I constraint verificano:

- ID e binding UUID non nil;
- provider, purpose e stato entro allowlist;
- `row_version >= 0`;
- ordine temporale coerente;
- timestamp di revoca presente soltanto per `REVOKED`;
- unicità della reference nell'intero scope e purpose;
- unicità del binding locale per scope, provider e purpose.

Il trigger di immutabilità copre ID, ancestry, provider, binding, purpose e data di creazione. Un cambio di locator, provider o purpose produce una nuova reference: non riscrive retroattivamente quella esistente.

### 4.2 `platform_core.endpoint_secret_binding`

Il binding include ancestry completa fino a Endpoint e una foreign key composita verso Endpoint. Una seconda foreign key composita lega la reference nello stesso Tenant, Organization e Facility e con lo stesso purpose.

Il modello è append-oriented:

- il binding attivo ha `retired_at IS NULL`;
- il retirement valorizza `retired_at` senza modificare identità o timestamp di attivazione;
- hard delete e cambio in-place di scope/endpoint/reference/purpose sono rifiutati;
- due reference differenti dello stesso purpose possono restare attive sul medesimo Endpoint durante overlap;
- il duplicato esatto attivo è rifiutato dall'indice univoco parziale.

### 4.3 Indici

| Indice | Query supportata |
|---|---|
| `ix_secret_reference_scope_state` | lookup corrente per Tenant/Facility/stato/reference con provider, purpose e versione inclusi |
| `uq_endpoint_secret_binding_active` | unicità del binding attivo e overlap soltanto fra reference differenti |
| `ix_endpoint_secret_binding_reference_active` | risoluzione e revoca dei binding attivi per scope/reference/endpoint |

Le tabelle sono nuove e vuote al momento della migration: la creazione degli indici non riscrive relazioni popolate. Correzioni future sono nuove migration forward; V004 non deve essere modificata dopo il rilascio.

## 5. Persistence boundary scoped

`SecretReferenceRepository` richiede `VerifiedFacilityScope` come primo argomento per ogni operazione. L'implementazione JDBC applica lo scope nella stessa statement che legge o modifica:

- `register`: usa `INSERT … SELECT` dalla Facility corrispondente al Tenant e alla Facility verificati, derivando Organization dal DB;
- `findUsable`: filtra Tenant, Facility, ID e stato utilizzabile;
- `bindToEndpoint`: unisce Endpoint e reference sull'intera ancestry e inserisce solo se entrambi appartengono allo scope;
- `exportEndpointReferences`: unisce nuovamente l'Endpoint scoped e filtra Tenant, Facility, Endpoint non dismesso, binding attivi e reference non revocate.

Il chiamante non può fornire `organization_id`, cambiare scope tramite ID di un altro Endpoint o ottenere indicazioni sull'esistenza di una reference fuori scope. Un insert/bind che non seleziona esattamente una riga o incontra una violazione d'integrità produce `SecretReferenceUnavailableException` con messaggio uniforme, senza ID e senza concatenare la causa JDBC. In questo modo un conflitto univoco non porta `backend_binding_id` nei diagnostici applicativi.

La repository non ha metodi unscoped, lookup globale o fallback amministrativo. L'accesso package-private al binding locale evita che normali consumer lo serializzino accidentalmente.

## 6. Contratto di export

`SecretReferenceExport` contiene soltanto:

```json
{
  "secretReferenceId": "sref-11111111-1111-4111-8111-111111111111",
  "provider": "AZURE_KEY_VAULT",
  "purpose": "OAUTH_CLIENT_CREDENTIAL",
  "state": "ACTIVE",
  "rowVersion": 0,
  "requiresRebinding": true
}
```

Il DTO valida ID canonico, enum non null, versione non negativa e `requiresRebinding=true`. Non contiene ancestry, `backend_binding_id`, path, versione provider o materiale. Un import in un altro ambiente deve creare una nuova mappa locale dopo policy check: non può riutilizzare implicitamente il backend di origine.

Questa projection è la fondazione per bundle e API successive; P1-0105 non espone ancora un endpoint HTTP di gestione. P1-0106 dovrà riusare questo tipo o un contratto equivalente e applicare property-level authorization, idempotenza ed ETag senza aggiungere locator o campi liberi.

## 7. Rotazione, revoca e continuità

### 7.1 Rotazione senza interruzione

La sequenza enterprise è:

1. creare il nuovo valore nel secret manager senza trasferirlo al Control Plane;
2. creare un nuovo binding locale UUID nella Runtime Cell;
3. registrare una nuova `SecretReference` scoped con lo stesso purpose;
4. collegarla allo stesso Endpoint mantenendo ancora quella precedente;
5. distribuire metadata firmati e verificare risoluzione/autenticazione su tutte le repliche;
6. portare il traffico al nuovo valore e osservare error rate, expiry e adoption;
7. revocare la reference precedente nel Platform DB e il valore precedente nel provider;
8. ritirare il vecchio binding dopo la finestra di sicurezza e conservare audit/evidence.

Durante i passi 4–6 l'export runtime contiene entrambe le reference. Dopo la revoca, le query P1-0105 escludono immediatamente la precedente. Ordine, finestra di overlap e rollback sono specifici del protocollo: TLS, OAuth e password database non devono condividere una procedura cieca.

### 7.2 Outage del Control Plane

Le Runtime Cell continuano a usare la configurazione locale firmata già approvata secondo ADR-001 e ADR-028. Non chiedono il secret al Control Plane per ogni messaggio. Una reference nuova o revocata non può essere promossa finché il plane non torna coerente; il runtime applica freshness/expiry e safe-stop selettivo definiti dalla policy del flow.

### 7.3 Outage del secret manager

Il resolver usa timeout, retry bounded con jitter, circuit breaker e cache soltanto se il provider e la threat model lo consentono. Nessun fallback usa secret statici in config o DB. I connector che possono continuare con una credenziale già materializzata rispettano TTL massimo e revocation policy; quelli che non possono provarne la validità entrano in stato degradato o safe-stop senza ACK ingannevoli.

### 7.4 HA, backup e disaster recovery

- il secret manager è un servizio Tier-0/Tier-1 secondo i flow dipendenti, con topologia HA e quota/capacity testata;
- backup del Platform DB conserva reference e binding UUID, non il valore; il backup del secret manager segue procedure separate e più restrittive;
- il restore in un nuovo sito non rende utilizzabili automaticamente i binding: `requiresRebinding` impedisce falsa portabilità;
- ogni sito/facility usa identity e binding locali per limitare blast radius;
- failover e failback verificano access policy, versione, revoca, clock, DNS, TLS trust e assenza di cache obsolete;
- drill M5/G8 deve includere perdita del provider, perdita del mapping locale, credenziale scaduta, quota exhaustion e rotazione interrotta.

## 8. Auditabilità e monitoraggio

P1-0105 prepara dati tecnici e stati, ma non dichiara implementato il journal di P1-0107. In produzione sono obbligatori eventi tamper-evident per create, bind, rotate-start, validate, revoke, retire, denied cross-scope e rebinding. Gli eventi contengono reference ID logico, scope pseudonimo, purpose, actor/workload, decision, correlation ID, policy/versione e timestamp; non contengono binding locale, locator o valore.

Metriche senza label ad alta cardinalità o segrete:

- success/failure/latency del resolver per provider family, cella e purpose aggregato;
- reference attive, in rotazione, revocate e prossime a scadenza;
- propagation age e replica adoption durante overlap;
- denied access, missing binding e purpose mismatch;
- errori di refresh, throttling, circuit-breaker open e cache age;
- scostamento clock e ritardo audit;
- numero di export/rebinding e outcome delle validazioni.

Alert minimi: credenziale prossima a expiry senza replacement, reference revocata ancora usata, failure rate sostenuto, binding mancante dopo restore, rotazione oltre finestra, accesso anomalo multi-facility, audit gap e quota provider prossima all'esaurimento. Dashboard e alert completi sono WP1-10; le specifiche qui definite ne costituiscono l'oracle.

## 9. Sicurezza multi-azienda e multi-facility

- Reference e binding appartengono a una singola Facility; non esiste reference globale condivisa per default.
- Organization è derivata dal DB e parte delle foreign key, quindi una Facility non può essere trasposta tra aziende tramite input.
- Un Tenant con più Facility usa reference distinte anche quando la destinazione esterna è comune; l'eventuale condivisione deve essere un'eccezione governata fuori da P1-0105.
- Identity runtime, policy provider e binding locale devono avere lo stesso o un più stretto scope della reference.
- DB administrator e secret-manager administrator sono ruoli separati; il dump del primo non abilita accesso al secondo.
- Export e DR non ricostruiscono locator o identity: richiedono rebinding esplicito e validazione nel sito target.
- Il messaggio di errore uniforme evita l'enumerazione di Endpoint e reference cross-scope.

## 10. Casi d'uso sanitari europei 2026

### UC-SECREF-01 — LIS on-premise con mTLS

Una Facility ospedaliera italiana usa un certificato client per inviare risultati di laboratorio a un LIS. Private key e certificato restano nel vault/HSM locale; HHC registra due reference distinte per purpose. Durante rinnovo, vecchia e nuova coppia convivono finché tutte le repliche hanno validato handshake e catena. L'export di configurazione per disaster recovery contiene soltanto `sref`, provider family e purpose e impone rebinding nel sito secondario.

### UC-SECREF-02 — Gruppo sanitario con Facility in più Stati UE

Un Tenant rappresenta il gruppo, le Organization le aziende legali e le Facility gli ospedali. Una reference della Facility francese non è leggibile né collegabile a un Endpoint tedesco, anche conoscendone l'ID. Il valore resta nel secret manager della regione ammessa dalla policy locale; il Platform DB centrale non contiene locator né materiale capace di eludere la residency.

### UC-SECREF-03 — OAuth verso piattaforma nazionale

Un connector FHIR usa client credential OAuth. La reference dichiara `OAUTH_CLIENT_CREDENTIAL`, mentre client secret o private key sono emessi e ruotati nel provider. Il runtime non può riusare la stessa reference per Basic Auth perché il purpose è parte del binding. Una rotazione fallita conserva temporaneamente la reference precedente; la revoca la elimina poi dalla vista utilizzabile.

### UC-SECREF-04 — Ripristino della Facility in sito DR

Dopo perdita del sito primario, Platform DB e bundle vengono ripristinati nel sito secondario. Nessun secret appare nel backup. L'operatore crea binding locali tramite identity DR, valida connettività e policy e solo allora abilita i flow. L'assenza di rebinding blocca il connector in modo esplicito anziché tentare credenziali inesistenti o di produzione primaria.

### UC-SECREF-05 — Revoca urgente dopo compromissione

Il SOC revoca una reference API token di una Facility. Le nuove projection non la includono; il provider revoca il valore e il runtime invalida cache secondo SLA. Altre Facility continuano perché hanno reference, identity e binding separati. Audit registra ID logico e decisione, senza copiare il token compromesso.

### UC-SECREF-06 — Secret manager temporaneamente indisponibile

Un outage del provider non induce HHC a leggere una copia dal DB o da un export. I flow con credenziale ancora valida possono operare entro TTL e policy; quelli che richiedono fetch o rinnovo entrano in degraded/safe-stop. Code e durability point proteggono i messaggi, mentre alert e runbook guidano il recovery senza ACK falsi.

### UC-SECREF-07 — Esportazione configuration-as-code tra ambienti

Una configurazione da preproduzione viene promossa in produzione. L'export porta l'ID logico e `requiresRebinding=true`, ma omette il binding UUID locale. La pipeline non può contattare il secret di preproduzione dalla produzione: un security administrator deve creare e validare il binding target con identity dedicata prima della promozione.

## 11. Verifica consegnata

| Scenario | Oracle |
|---|---|
| ID reference | forma canonica, round-trip e rifiuto nil/non canonico |
| modello Java | nessun campo capace di contenere materiale o path; binding solo UUID |
| export JSON | nessun binding UUID o campo sensibile; `requiresRebinding=true` |
| costruzione export | enum chiusi, ID canonico, versione non negativa, rebinding obbligatorio |
| fresh/N-1 migration | V004 applicata su PostgreSQL reale e Flyway validate verde |
| catalogo | colonne esattamente allowlisted, tabelle e indici presenti |
| provider arbitrario | rifiutato dal check constraint |
| immutabilità/hard delete | rifiutati dai trigger |
| register | Organization derivata dalla Facility scoped |
| cross-facility/cross-tenant | lookup ed export vuoti; bind negato con errore uniforme |
| collisione binding | violazione univoca tradotta senza causa o UUID nei diagnostici |
| overlap | due reference stesso purpose sullo stesso Endpoint ammesse |
| revoca | reference revocata assente da lookup ed export, replacement disponibile |
| Endpoint dismesso | nessuna reference esportata anche se il binding storico è ancora attivo |
| digest migration | checksum V004 e digest aggregato verificati dal gate |

La suite usa Testcontainers con PostgreSQL 18.6 fissato per digest. Non usa un database emulato e non contiene credenziali reali. `mvn verify`, gate schema e gate WP1-00 sono gli ingressi CI; test provider reali, cache/resolver, fault injection e performance fanno parte della qualification successiva e non sono simulati da P1-0105.

## 12. Artifact ed evidenze

Implementazione:

- `platform/control-plane/src/main/java/io/hyperhealth/connect/controlplane/secret/`;
- `platform/control-plane/src/main/resources/db/migration/V004__expand__scoped_secret_references.sql`;
- configurazione repository in `ScopedInventoryConfiguration`.

Test:

- `SecretReferenceTest`;
- `JdbcScopedEndpointRepositoryTest`;
- `PlatformCoreMigrationTest`.

Governance:

- `governance/platform-db-schema.yml`;
- `governance/controls.yml`;
- `governance/traceability.yml`;
- `governance/phase-1-delivery-tracker.yml`;
- `scripts/phase1-p1-0102-gate.ps1`.

## 13. Attività residue e criteri per G2

| Tema | Stato dopo P1-0105 | Chiusura |
|---|---|---|
| valore segreto in Platform DB/export | strutturalmente escluso | regressione continua |
| API CRUD/ETag/idempotenza | non inclusa | P1-0106 |
| audit tamper-evident | contratto definito, journal non incluso | P1-0107/WP1-10 |
| matrice negativa completa | subset reference/Endpoint incluso | P1-0108/G2 |
| adapter provider e resolver Data Plane | non incluso | WP1-04/WP1-11 |
| workload identity verso provider | boundary richiesto, provisioning specifico non incluso | deployment qualification |
| rotazione automatica end-to-end | overlap DB provato, provider/consumer non ancora | WP1-11/G8 |
| cache, thundering herd e quota | requisiti definiti, benchmark non incluso | WP1-12/G8 |
| HA/DR secret manager | contratto definito, topologia non qualificata | WP1-11/M5 |
| performance su corpus multi-facility | indici presenti, envelope non misurato | P1-0110/G2 |

G2 richiede inoltre grant DB minimi, piani query su scala, backup/restore logico, test reali di provider almeno sul profilo dichiarato, leakage scan e chiusura dei finding. Nessun provider è dichiarato production-ready per il solo fatto di essere presente nell'enum.

## 14. Fonti ufficiali

Fonti verificate il **15 settembre 2026**:

- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html): HA, standardizzazione multi-provider, least privilege, automazione, audit, lifecycle, downtime e minimizzazione del tempo in memoria;
- [HashiCorp Vault — KV v2](https://developer.hashicorp.com/vault/docs/secrets/kv/kv-v2): versioning, check-and-set, delete/undelete e destroy nel backend provider; HHC non replica queste funzioni nel Platform DB;
- [Secrets Store CSI Driver — Secret Auto Rotation](https://secrets-store-csi-driver.sigs.k8s.io/topics/secret-auto-rotation): rotazione periodica opt-in e aggiornamento dei mount; la capacità va qualificata con il workload consumer;
- [AWS Secrets Manager — Rotate secrets](https://docs.aws.amazon.com/secretsmanager/latest/userguide/rotating-secrets.html): rotazione gestita o tramite funzione e coordinamento con la risorsa downstream;
- [Microsoft Azure Key Vault — Secure your secrets](https://learn.microsoft.com/en-us/azure/key-vault/secrets/secure-secrets): storage esterno, accesso least-privilege, rotazione, doppia credenziale, caching e monitoring;
- [Google Cloud Secret Manager — Best practices](https://docs.cloud.google.com/secret-manager/docs/best-practices): IAM, audit Data Access, version pinning, regionalità, quota e mitigazione del thundering herd;
- [PostgreSQL 18 — Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html): foreign key, unique e check constraint composite;
- [PostgreSQL 18 — Partial indexes](https://www.postgresql.org/docs/18/indexes-partial.html): enforcement e access path sulle sole associazioni attive;
- [Flyway — Versioned migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations): ordinamento, checksum e principio forward-only delle migration versionate.

Le fonti dei provider descrivono le capability esterne, non una compatibilità automaticamente certificata. Il contratto HHC resta provider-neutral: soltanto reference logiche entrano nel modello portabile, mentre risoluzione, identity e valore rimangono nel deployment qualificato.
