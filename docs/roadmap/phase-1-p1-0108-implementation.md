# P1-0108 — Matrice negativa cross-tenant e cross-facility

| Campo | Valore |
|---|---|
| Work package | WP1-01 — Tenancy, identità e Platform DB |
| Stato | IMPLEMENTED |
| Data baseline | 16 settembre 2026 |
| Perimetro qualificato | vertical slice Endpoint e secret reference facility-scoped |
| Ambienti | test sintetici; PostgreSQL 18.6 fissato per digest |
| Owner | Platform Engineering; sign-off Security richiesto al Gate G2 |

## 1. Outcome e confine della consegna

P1-0108 rende eseguibile e verificabile la matrice di isolamento del vertical slice realizzato da P1-0101…P1-0107. La suite costruisce un chiamante autorizzato nella Facility A1, una Facility A2 appartenente allo stesso Tenant A e una Facility B1 appartenente al Tenant B. Ogni operazione implementata viene esercitata contro A2 e B1, attraversando i boundary disponibili.

Sono consegnati:

1. una matrice machine-readable di 43 casi in [`phase-1-tenant-isolation-matrix.yml`](../testing/phase-1-tenant-isolation-matrix.yml);
2. test strutturali che rendono `VerifiedFacilityScope` il primo parametro di repository, service e API;
3. test OIDC/RBAC e confused-deputy che provano l'autorità dello scope firmato;
4. test PostgreSQL reali su read, list, create, update, secret reference, binding e ancestry;
5. oracle di non enumerazione e di assenza di side effect;
6. verifica che i deny di lettura siano registrati soltanto nella catena audit del chiamante;
7. un gate PowerShell cross-platform che controlla completezza e drift della matrice;
8. un job CI dedicato e un report JSON conservabile come evidence.

Il perimetro è deliberatamente limitato alle superfici implementate. Non esistono ancora API amministrative per Tenant, Organization, Facility, Application o Runtime Cell; non viene quindi simulata una copertura inesistente. Non sono introdotti RLS PostgreSQL, cache distribuite, resolver dei provider segreti o un sink di security decision indipendente. Questi elementi restano work item espliciti, non prerequisiti implicitamente dati per acquisiti.

P1-0108 è completata; WP1-01 e Gate G2 restano aperti per P1-0109, P1-0110 e per le ulteriori prove di gate descritte nella roadmap.

## 2. Modello di minaccia

La matrice copre i seguenti tentativi:

- **BOLA orizzontale cross-facility:** un operatore autenticato nella Facility A1 sostituisce Endpoint o Application ID con un ID valido della Facility A2;
- **BOLA cross-tenant:** lo stesso operatore usa un ID valido appartenente al Tenant B;
- **scope spoofing:** il client invia `X-Tenant-ID`, `X-Facility-ID` o request attribute diversi dalle claim firmate;
- **confused deputy:** l'identità autenticata e lo scope passato al controller non coincidono;
- **filter oracle:** una collection usa un `applicationId` esterno per dedurre esistenza o numerosità;
- **mutation oracle:** POST/PATCH sfruttano errori, ETag o idempotency claim per distinguere risorsa assente e risorsa esterna;
- **secret reference pivot:** Endpoint e reference di scope diversi vengono combinati in find, export, register o bind;
- **persistence bypass:** insert SQL tenta ancestry Tenant/Organization/Facility/Application incoerente;
- **audit contamination:** il tentativo contro una risorsa esterna crea eventi o head nello scope bersaglio;
- **future cache bypass:** una cache viene aggiunta senza includere Tenant e Facility nella chiave.

Non sono usati ID sequenziali o prevedibili, ma questo non è considerato un controllo autorizzativo. Gli UUID canonici riducono collisioni e guessing; la decisione rimane sempre basata sullo scope verificato.

## 3. Invarianti di isolamento

### 3.1 Scope obbligatorio

Ogni metodo pubblico del repository Endpoint, del repository secret reference, del service e del controller Endpoint riceve `VerifiedFacilityScope` come primo parametro. I test di architettura falliscono alla build se una nuova operazione non mantiene questa forma.

Lo scope è un value object composto da Tenant ID e Facility ID. Non viene ricostruito da parametri di query, path o header applicativi. Il filtro OIDC valida firma, issuer, lifetime, audience, `azp`, principal type, role namespace e claim scope, quindi sovrascrive qualsiasi request attribute non affidabile.

### 3.2 Predicate SQL

Ogni query applicativa usa contemporaneamente `tenant_id = ?` e `facility_id = ?` prima dell'ID risorsa o del filtro. Sono coperti:

- `findEndpoint`;
- `listEndpoints`, incluso filtro Application e keyset cursor;
- claim/replay/completamento idempotente;
- insert e update Endpoint;
- lookup Application padre;
- find/export/register/bind delle secret reference.

I constraint composti impediscono di persistere ancestry incoerenti anche se un bug applicativo costruisse una riga errata. Il trigger audit controlla che la Facility appartenga al Tenant prima di creare head o evento.

### 3.3 Non enumerazione

Un Endpoint assente, dismesso, della facility sorella o di un altro tenant produce lo stesso problema RFC 9457:

- HTTP `404`;
- code `HHC-INV-404-001`;
- detail: `The resource does not exist or is not visible in the current scope.`;
- nessun Endpoint ID, Application ID, display name, ETag o row version esterno;
- `Cache-Control: no-store` e correlation ID.

Per una collection con filtro Application esterno il risultato è una pagina valida e vuota. La risposta non restituisce count globale, owner, facility o indicazioni sull'esistenza del filtro.

### 3.4 Assenza di effetti collaterali

Create e update cross-scope devono terminare senza:

- nuova riga Endpoint;
- nuova identity Endpoint;
- claim idempotente residuo;
- modifica di display name, lifecycle o row version del bersaglio;
- binding secret aggiuntivo;
- evento audit nello scope bersaglio.

Il POST crea inizialmente il claim idempotente nella stessa transazione, ma il lookup scoped dell'Application fallisce e PostgreSQL esegue rollback completo. Il test confronta i conteggi prima/dopo per Endpoint, identity e claim. PATCH include Tenant, Facility, Endpoint e row version nel predicato; una riga esterna non viene aggiornata e il successivo lookup scoped produce il 404 uniforme.

## 4. Matrice per layer

| Layer | Sibling Facility | Altro Tenant | Oracle principale |
|---|---|---|---|
| OIDC/security filter | header e attribute spoofati sovrascritti | identico | scope firmato prevale; token invalido 401 |
| RBAC | capability read/write minima | identico | 403 sicuro prima del controller |
| Controller/API | GET/list/POST/PATCH | GET/list/POST/PATCH | 404 indistinguibile o lista vuota |
| Service | get/create/update | get/create/update | eccezione non enumerabile, nessun side effect |
| Endpoint repository | find/list/create/update | find/list/create/update | predicate Tenant+Facility |
| Secret repository | find/export/register/bind | find/export/bind | vuoto o errore opaco, nessun binding |
| PostgreSQL | ancestry Facility/Application/Endpoint | ancestry Tenant/Facility e audit | SQLSTATE `23503` |
| Audit | read deny nel chiamante | read deny nel chiamante | nessun evento nello scope bersaglio |
| Cache | non applicabile: nessuna cache presente | non applicabile | gate blocca introduzione non qualificata |

La matrice YAML è canonica per l'elenco dei casi, il test che li automatizza e l'oracle. Il gate richiede almeno 40 casi e tutte le dimensioni; una nuova operazione deve aggiornare matrice e test nello stesso change.

## 5. Audit e monitoraggio

Ogni read cross-scope raggiunto dal repository registra nel solo scope del chiamante:

- action `ENDPOINT_READ`;
- outcome `FAILURE`;
- reason `NOT_FOUND_OR_NOT_VISIBLE`;
- actor e resource ID pseudonimizzati;
- correlation e trace context;
- catena HMAC verificabile.

Il test genera tre read denial per bersaglio (repository, service e API), quindi verifica sei eventi failure nello scope autorizzato, zero eventi in A2/B1 e validità della catena. List con filtro esterno produce un evento di collection nel chiamante e non espone il filtro in chiaro nello storage.

Una distinzione importante rimane: token invalido e deny RBAC avvengono prima del controller e non sono ancora nel journal transazionale dell'inventory. La suite verifica il deny HTTP, ma l'evento di security decision dovrà essere prodotto dal boundary IAM e inoltrato al sink indipendente di WP1-10. P1-0108 non dichiara risolto questo requisito.

Monitoraggio enterprise richiesto:

1. allarme su aumento dei `NOT_FOUND_OR_NOT_VISIBLE` per actor/client/scope;
2. detection di sequenze di ID o filtri cross-scope senza registrare gli ID raw;
3. allarme su failure del journal, gap o HMAC mismatch;
4. metrica separata per 401, 403 e 404, con cardinalità bounded;
5. correlazione SIEM tramite correlation ID, trace ID e hash keyed;
6. nessun body, token, secret locator, display name o payload sanitario nei log.

## 6. Alta disponibilità, resilienza e disaster recovery

I controlli di isolamento non vengono degradati durante failover. Se il Platform DB o il journal non sono disponibili, le operazioni amministrative falliscono chiuse; non esiste un percorso “temporaneamente unscoped”. Business mutation, claim idempotente e audit di successo condividono la transazione PostgreSQL.

La qualification DR completa resta WP1-11, ma l'oracle di isolamento deve essere rieseguito:

- dopo restore logico o PITR;
- dopo promozione replica;
- dopo rotazione delle chiavi OIDC/audit;
- dopo variazione di pool, proxy SQL o topologia multi-region;
- prima della riapertura delle mutation del Control Plane.

Un restore incoerente che separi Endpoint, idempotency record e journal non è accettabile. Il Data Plane clinico già distribuito mantiene la propria autonomia secondo ADR-028; il blocco fail-closed riguarda le operazioni amministrative, non deve interrompere flussi clinici già configurati.

## 7. Scalabilità e prestazioni

I test negativi non aggiungono query di pre-autorizzazione non indicizzate. L'isolamento è nel predicato della stessa query e usa gli indici left-prefix `(tenant_id, facility_id, ...)`. Questo evita un lookup globale seguito da un controllo in memoria, che aumenterebbe sia il rischio BOLA sia il costo sui tenant più grandi.

Le risposte non eseguono count globale. La pagination è keyset e il filtro esterno restituisce al massimo `limit + 1` righe nello scope del chiamante. Le transazioni fallite rilasciano idempotency claim e identity, impedendo accumulo di artefatti da attacchi ripetuti.

Non esiste una cache dell'inventory. Il gate cerca annotazioni e client cache noti nel codice production. L'introduzione futura richiede:

1. chiave almeno `(tenant_id, facility_id, resource_type, resource_id/query_digest)`;
2. namespace e invalidazione scoped;
3. nessun negative cache condiviso fra tenant;
4. cifratura e minimizzazione del valore;
5. ripetizione dell'intera matrice per hit, miss, stale entry e failover;
6. protezione da timing oracle e cache poisoning.

Benchmark, noisy-neighbor e piani `EXPLAIN (ANALYZE, BUFFERS)` con seed dimensionale sono responsabilità di P1-0110/G2.

## 8. Casi d'uso sanitari europei 2026

### ISO-UC-01 — gruppo ospedaliero multi-presidio

Un Facility Operator dell'ospedale A gestisce l'Endpoint del LIS locale. Sostituendo l'ID con quello dell'ospedale B dello stesso gruppo riceve lo stesso 404 di un ID inesistente. Nessun hostname, vendor, versione o stato operativo del presidio B attraversa l'API.

### ISO-UC-02 — piattaforma SaaS multi-azienda

Un operatore dell'azienda sanitaria A usa un Application ID valido dell'azienda B nel POST. Il lookup padre è scoped, la transazione fa rollback e non restano Endpoint, identity o idempotency claim. Il tenant B non riceve eventi audit contaminanti.

### ISO-UC-03 — auditor con privilegi read-only

Un Auditor può leggere soltanto nello scope firmato e non acquisisce capability write. Un token `FlowDeveloper` non ottiene accesso all'inventory; un workload usa audience, client e role namespace separati. Gli header di tenancy non ampliano i privilegi.

### ISO-UC-04 — rotazione credenziali di un gateway FHIR

Una secret reference del presidio A non può essere esportata o collegata a un Endpoint del presidio B. L'export contiene solo metadata portabile e richiede rebinding; il binding locale del provider e il valore segreto non attraversano database export o error response.

### ISO-UC-05 — tentativo di enumerazione durante un incidente

Un account compromesso prova una serie di Endpoint ID. Gli oggetti assenti e cross-scope hanno payload e status uniformi. Il journal del chiamante registra failure pseudonime per detection; le facility bersaglio non vengono modificate né popolate con eventi artefatti.

### ISO-UC-06 — failover del Control Plane

Dopo promozione della replica, l'SRE esegue migration validation, verifier audit e matrice P1-0108 prima di riabilitare le mutation. Qualsiasi query unscoped, side effect esterno o chain mismatch blocca la riapertura, mentre i flussi clinici già distribuiti restano indipendenti.

### ISO-UC-07 — ricerca applicativa senza data count

Un client prova a filtrare gli Endpoint con un Application ID di un altro presidio. Riceve una pagina vuota senza total count, nome dell'applicazione o differenze di errore. La query usa comunque Tenant e Facility del token e non esegue un lookup globale.

## 9. Esecuzione ed evidence

Comandi canonici:

```powershell
./scripts/phase1-p1-0108-gate.ps1
./mvnw -B -ntp -pl platform/control-plane -am test
```

Il primo comando valida artefatti, dimensioni, layer, oracle, collegamento ai metodi di test, firme scope-first e assenza di cache non qualificata. Produce:

`target/phase1-p1-0108-evidence/tenant-isolation-gate-report.json`

Il secondo esegue test unitari, web security e PostgreSQL Testcontainers. Il job CI `tenant-isolation-gate` pubblica il report JSON; `build-and-test` pubblica i report Surefire.

Baseline locale del 16 settembre 2026:

- 32 test mirati eseguiti;
- 0 failure, 0 error, 0 skipped;
- 13 test `JdbcScopedEndpointRepositoryTest`;
- 6 test `PlatformCoreMigrationTest`;
- 10 test `ScopedEndpointControllerTest`;
- 3 test `ScopedInventoryServiceTest`;
- PostgreSQL 18.6 digest-pinned;
- gate statico da rieseguire dopo il completamento di questo record e in CI.

Il conteggio locale è una fotografia, non un criterio hard-coded. Il criterio durevole è l'esito PASS dei gate e dei test associati alla matrice.

## 10. Criteri di completamento P1-0108

| Criterio | Evidenza | Esito |
|---|---|---|
| sibling Facility e altro Tenant | fixture A1/A2/B1 | PASS |
| API/service/repository/SQL | matrice e test integrato | PASS |
| OIDC scope non spoofabile | web integration test | PASS |
| RBAC read/write minimo | web integration test | PASS |
| assente e cross-scope indistinguibili | RFC 9457 assertions | PASS |
| zero business side effect | conteggi e row version prima/dopo | PASS |
| secret reference non pivotabile | find/export/register/bind | PASS |
| audit nello scope chiamante | 6 failure, 0 eventi target, chain valida | PASS |
| nessuna cache unscoped | gate source scan | PASS |
| execution ripetibile in CI | job ed evidence JSON | PASS |

## 11. Rischi residui e attività successive

| Tema | Stato dopo P1-0108 | Chiusura |
|---|---|---|
| RLS PostgreSQL defense-in-depth | non implementata; access role e grant da hardenizzare | security hardening/G2 |
| audit di 401/403 pre-controller | deny verificato, evento indipendente assente | WP1-10 |
| audit create/bind/rotate/revoke secret | API non esposta e journal non implementato | secret management/WP1-10 |
| cache inventory | assente; introduzione bloccata dal gate | work item dedicato se necessaria |
| API gerarchia superiore | non pubblicate | policy/scope design dedicato |
| IdP reale, revoca e rollover remoto | validator sintetico locale | IAM qualification/G2 |
| restore/failover end-to-end | requisito definito, drill non eseguito | WP1-11/M5 |
| scala/noisy-neighbor | query indicizzate, benchmark pendente | P1-0110/G2 |
| Unicode/locale/timezone | non parte di questo incremento | P1-0109 |
| security review indipendente | richiesta ma non sostituibile dal maintainer | staffing checkpoint G2 |

## 12. Fonti ufficiali e data di verifica

Fonti verificate il **16 settembre 2026**:

- OWASP API Security Top 10 2023, API1 Broken Object Level Authorization: <https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/>
- OWASP Web Security Testing Guide 4.2, Testing for Bypassing Authorization Schema: <https://wstg.owasp.org/v4.2/4-Web_Application_Security_Testing/05-Authorization_Testing/02-Testing_for_Bypassing_Authorization_Schema/>
- OWASP Web Security Testing Guide, Testing for Bypassing Authentication Schema: <https://wstg.owasp.org/latest/4-Web_Application_Security_Testing/04-Authentication_Testing/04-Testing_for_Bypassing_Authentication_Schema/>
- Spring Security, Servlet Applications: <https://docs.spring.io/spring-security/reference/servlet/index.html>
- Spring Security, method/security testing: <https://docs.spring.io/spring-security/reference/servlet/test/method.html>
- PostgreSQL 18, Row Security Policies: <https://www.postgresql.org/docs/18/ddl-rowsecurity.html>
- PostgreSQL 18, `pg_policy`: <https://www.postgresql.org/docs/18/catalog-pg-policy.html>
- PostgreSQL 18, `CREATE ROLE` e `BYPASSRLS`: <https://www.postgresql.org/docs/18/sql-createrole.html>
- Regolamento (UE) 2016/679 (GDPR): <https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32016R0679>
- Direttiva (UE) 2022/2555 (NIS2): <https://eur-lex.europa.eu/eli/dir/2022/2555/oj/eng>
- Regolamento (UE) 2025/327 (EHDS): <https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32025R0327>

La presenza delle fonti PostgreSQL RLS non implica che RLS sia attiva: serve a fissare il modello ufficiale per il successivo hardening, inclusi owner e ruoli `BYPASSRLS`. La documentazione normativa descrive readiness tecnica e non è un parere legale; applicabilità e retention vanno validate per paese e trattamento.
