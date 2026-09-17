# P1-0107 — Audit transazionale dell'inventory amministrativo

| Campo | Valore |
|---|---|
| Work package | WP1-01 — Tenancy, identità e Platform DB |
| Stato | IMPLEMENTED |
| Data baseline | 16 settembre 2026 |
| Perimetro consegnato | journal tamper-evident per Endpoint facility-scoped |
| Classificazione dati | metadata di sicurezza; PHI, payload e secret vietati |
| Owner | Platform Engineering con Security & Observability Engineering |

## 1. Outcome e confine

P1-0107 rende auditabili le operazioni amministrative Endpoint consegnate da P1-0106. Ogni create, replay idempotente, read, list, update e decommission genera un evento append-only nello stesso Platform DB e nello stesso scope Tenant/Facility. Le mutazioni di business e l'evento sono nella medesima transazione: se il journal non può accettare l'evento, la mutazione non viene committata.

Sono consegnati:

1. migration additiva `V007__expand__inventory_audit_journal.sql`;
2. sequenza monotona e catena HMAC-SHA-256 indipendente per Tenant/Facility;
3. pseudonimizzazione HMAC di subject e resource ID, con chiave separata dalla chiave di integrità;
4. actor type `HUMAN|WORKLOAD` derivato dall'identità OIDC già validata;
5. correlation ID per richiesta e trace ID compatibile W3C Trace Context;
6. digest della policy e dell'artefatto eseguito, con key ID esplicito;
7. trigger PostgreSQL che rifiutano `UPDATE`, `DELETE` e `TRUNCATE` degli eventi;
8. verifier deterministico della sequenza, dei link, dell'HMAC e del chain head;
9. rollback dell'operazione di business quando l'append fallisce;
10. test su PostgreSQL 18.6 per schema, azioni, concorrenza, minimizzazione e immutabilità.

Questo incremento è la fondazione audit del vertical slice inventory, non il completamento del servizio audit enterprise. WP1-10 resta responsabile di sink amministrativamente indipendente, buffering/outbox, inoltro mTLS, retention/WORM, SIEM, allarmi continui, checkpoint esterni, key rotation e audit di tutte le superfici. P1-0108 ha successivamente qualificato la matrice di deny e failure, inclusi quelli prima del controller, senza attribuire loro un evento inventory inesistente. Gate G2 resta aperto fino a P1-0110.

## 2. Eventi coperti

| Azione | Quando | Outcome/reason |
|---|---|---|
| `ENDPOINT_CREATE` | primo commit di un nuovo Endpoint | `SUCCESS`; reason assente |
| `ENDPOINT_CREATE` | replay idempotente dello snapshot | `SUCCESS`; `IDEMPOTENT_REPLAY` |
| `ENDPOINT_READ` | lettura corrente nello scope | `SUCCESS` |
| `ENDPOINT_READ` | ID assente, dismesso o non visibile | `FAILURE`; `NOT_FOUND_OR_NOT_VISIBLE` |
| `ENDPOINT_LIST` | collection bounded e filtrata | `SUCCESS` |
| `ENDPOINT_UPDATE` | rename o transizione non terminale | `SUCCESS` |
| `ENDPOINT_DECOMMISSION` | transizione terminale | `SUCCESS` |

Il repository non duplica un evento read durante i controlli interni della mutation. Un retry idempotente è un accesso amministrativo reale e produce un nuovo evento, pur non creando una seconda risorsa. Invalid input, token assente/non valido, capability deny e collision/stale failure avvengono oggi prima dell'append committabile o causano rollback. P1-0108 ne verifica l'esito fail-closed; la relativa security decision deve essere catturata dal boundary di sicurezza/outbox in WP1-10. Non si dichiara quindi una completezza audit generale inesistente.

## 3. Modello persistente

### 3.1 Event store

`platform_core.inventory_audit_event` è insert-only. La chiave primaria globale è `event_id`; `(tenant_id, facility_id, scope_sequence)` è univoca. I campi persistiti sono:

- tempi UTC `occurred_at` e `recorded_at`;
- action, outcome e reason controllati da constraint;
- actor type, actor hash e `authorized_party` allowlisted;
- correlation UUID e trace ID W3C di 16 byte espresso in hex;
- resource type e resource hash;
- digest dei dettagli minimizzati, policy e artefatto;
- logical source/destination, schema version e integrity key ID;
- previous hash e record hash, entrambi 32 byte.

Non esistono colonne per subject raw, resource ID raw, body, display name, token, secret, locator, hostname o dato clinico. `authorized_party` è il client ID OIDC allowlisted, non un'identità personale. Tenant e Facility sono ID tecnici del perimetro e sono obbligatori per consentire segregazione, retention e investigazione autorizzata.

### 3.2 Chain head

`platform_core.inventory_audit_chain_head` contiene l'ultima sequenza e l'ultimo hash di ciascun Tenant/Facility. Prima dell'append il writer crea il head se assente e acquisisce un row lock `FOR UPDATE`. Questo serializza soltanto gli eventi amministrativi dello stesso scope; facility differenti avanzano in parallelo.

Il verifier acquisisce `FOR SHARE` sul head prima della scansione: un append concorrente non può produrre un falso positivo di corruzione. Per ogni riga ricostruisce l'HMAC sul formato canonico length-prefixed e controlla sequenza, previous hash, key ID e head finale.

## 4. Integrità crittografica e gestione chiavi

La catena usa HMAC-SHA-256 con domain separation `hhc:audit:record:v1`. Subject e resource key usano HMAC-SHA-256 con una seconda chiave e domini distinti; il detail fingerprint usa SHA-256. La separazione evita che una chiave destinata alla ricerca pseudonima consenta di forgiare la catena.

Le chiavi:

- sono iniettate soltanto a runtime in base64url non padded;
- devono contenere almeno 256 bit ed essere differenti;
- non sono salvate nel database, export, manifest o log;
- hanno un `integrity_key_id` non segreto in ogni evento;
- devono essere disponibili anche nel sito DR e nel processo autorizzato di verifica.

P1-0107 supporta una chiave attiva. La rotazione multi-key e la conservazione delle vecchie chiavi di verifica sono requisiti di WP1-10; cambiare unilateralmente chiave rende non verificabili gli eventi storici con l'istanza corrente. Il key ID rende esplicita questa condizione e impedisce una falsa attestazione.

## 5. Atomicità, failure e consistenza

### 5.1 Write path

Create, update e decommission eseguono business mutation, append dell'evento e avanzamento del head nella stessa transazione PostgreSQL. Un timeout, constraint, errore HMAC/JDBC o mancato avanzamento del head causa rollback completo. Il test di integrazione inietta un audit sink fallito e verifica l'assenza sia dell'Endpoint sia del claim idempotente.

### 5.2 Read path

Read e list aprono una transazione perché producono un evento. L'operazione è quindi fail-closed: nessuna risposta amministrativa considerata riuscita viene restituita se l'audit non è persistibile. Questo è deliberato per il Control Plane; non riguarda il Data Plane clinico, che deve mantenere autonomia e disponibilità secondo ADR-028.

### 5.3 Concorrenza

Il row lock del head impedisce fork e sequence collision. La granularità per Facility limita il blast radius di un hot scope, ma la capacità è quella di un journal amministrativo, non di telemetria clinica ad alto volume. Non si devono inviare al journal messaggi HL7/FHIR, payload o eventi per singolo paziente.

## 6. Alta disponibilità, business continuity e disaster recovery

Il journal è parte del Platform DB e segue replica, backup cifrato, PITR, restore drill, RPO e RTO definiti per tale tier. Un failover conserva business row ed evento nello stesso recovery point; non può risultare una mutation committata senza il relativo evento nello stesso database. Un restore con RPO maggiore di zero può perdere insieme entrambe le scritture recenti e richiede riconciliazione con checkpoint e sink indipendente quando WP1-10 sarà operativo.

Requisiti operativi:

1. distribuire le due chiavi audit e il key ID tramite secret manager in ogni sito autorizzato;
2. includere entrambe le tabelle nel backup/PITR e provarne il restore;
3. eseguire il verifier dopo restore, failover e prima della riapertura delle mutation;
4. allarmare su append failure, sequence gap, hash mismatch, clock skew e crescita anomala;
5. vietare DDL e ownership delle tabelle al ruolo applicativo di runtime;
6. spedire eventi/checkpoint a un dominio separato in WP1-10 per resistere a un amministratore database privilegiato.

I trigger rendono gli eventi immutabili per i ruoli ordinari ma non sono una barriera assoluta contro owner/superuser. La catena rende la manomissione rilevabile solo se chi verifica conserva chiavi e checkpoint fuori dal dominio compromesso. Questa distinzione fra tamper-resistant e tamper-evident è parte del modello di minaccia.

## 7. Scalabilità e prestazioni

Gli indici supportano ricerca per scope/tempo, resource hash/tempo e actor hash/tempo. La tabella non ha un indice globale per subject o resource in chiaro. Le query applicative hanno timeout di 5 secondi e il pool rimane bounded.

La sequenza è per Facility, quindi:

- non esiste un contatore globale multi-tenant;
- un hot tenant non blocca facility di altri tenant;
- l'ordine è totale nello scope amministrativo e non globale;
- il costo di append cresce con la contesa della singola Facility, non con il numero totale di tenant.

Prima della produzione sono ancora necessari benchmark con seed P1-0110, retention/partitioning, vacuum sizing e capacity model. Il full scan del verifier è una funzione controllata/offline: non va invocato sul request path. WP1-10 introdurrà checkpoint/batch verification per tempi bounded su retention pluriennale.

## 8. Privacy, GDPR ed EHDS 2026

Il journal applica minimizzazione by design: identifica attore e risorsa mediante HMAC keyed, conserva solo fingerprint di dettagli tecnici e vieta PHI. La pseudonimizzazione riduce il rischio ma non rende i dati anonimi; scope e hash restano dati potenzialmente personali quando correlabili e sono soggetti a controllo accessi, cifratura, retention e accountability GDPR.

Nel 2026 il Regolamento EHDS (UE) 2025/327 è in vigore, mentre la maggior parte degli obblighi applicativi decorre dal 26 marzo 2027 o successivamente. Il progetto adotta quindi readiness anticipata senza dichiarare applicabilità completa nel 2026. I log per gli ambienti sicuri e le funzioni di logging EHDS sono requisiti di design; profilo e scadenze vanno rivalutati prima di ogni go-live nazionale.

## 9. Casi d'uso sanitari europei 2026

### AUD-UC-01 — onboarding LIS multi-facility

Un operatore configura un Endpoint del LIS nel presidio A. La transazione crea Endpoint e `ENDPOINT_CREATE` nello scope del presidio A. Un auditor del presidio B non può usare cursor o token del presidio A; i suoi accessi restano in una catena distinta.

### AUD-UC-02 — manutenzione e change accountability

Durante una finestra approvata l'operatore sospende e rinomina un Endpoint. Gli eventi registrano actor pseudonimo, client autorizzato, policy/artifact digest, versioni precedente/risultante nel detail digest e correlation ID. Il journal non registra il body né il display name.

### AUD-UC-03 — dismissione del legacy

La migrazione dal vecchio LIS termina con `ENDPOINT_DECOMMISSION`. Endpoint, identity ledger ed evento sono atomici. L'hard delete del journal è rifiutato e il verifier prova che la sequenza non presenta buchi.

### AUD-UC-04 — investigazione di accesso non visibile

Un operatore richiede un Endpoint inesistente o di un'altra Facility. La risposta non distingue i casi e il journal registra `ENDPOINT_READ/FAILURE/NOT_FOUND_OR_NOT_VISIBLE`, senza salvare l'ID raw richiesto. L'auditor autorizzato può correlare per correlation ID e actor hash.

### AUD-UC-05 — risposta persa e retry

Un client perde il `201` e ripete il POST con la stessa idempotency key. Non nasce una seconda risorsa; un secondo evento `ENDPOINT_CREATE` con reason `IDEMPOTENT_REPLAY` dimostra l'accesso e consente di distinguere effetto di business e tentativo.

### AUD-UC-06 — failover e verifica post-DR

Dopo un failover PostgreSQL l'SRE esegue il verifier per ogni scope riattivato. Una replica coerente presenta stessa business mutation e stesso chain head. Un mismatch blocca le mutation privilegiate e apre incident response; il Data Plane già distribuito non viene arrestato automaticamente.

## 10. Verifica ed evidence

La suite automatica verifica:

- fresh migration V001–V007 e upgrade N-1 senza perdita;
- tabelle, constraint e indici validi su PostgreSQL 18.6 fissato per digest;
- eventi esatti per create/read/list/update/decommission;
- catena valida e sequenze concorrenti serializzate;
- assenza di subject e resource ID raw nello storage;
- rifiuto SQLSTATE `23000` per update/delete del journal;
- rollback di Endpoint e idempotency claim su audit failure;
- parsing conservativo del `traceparent` W3C e rigenerazione su input invalido;
- `X-Correlation-ID` nelle risposte API riuscite;
- chiavi distinte, key ID e digest di supply chain canonicali.

La qualification non comprende ancora pen test indipendente, SIEM end-to-end, checkpoint esterno, outage prolungato del sink, DLP canary, retention/WORM o restore drill: restano evidence M4/M5.

## 11. Configurazione operativa

| Variabile | Default | Regola |
|---|---|---|
| `HHC_INVENTORY_AUDIT_INTEGRITY_KEY` | nessuno | obbligatoria; base64url non padded, almeno 32 byte |
| `HHC_INVENTORY_AUDIT_PSEUDONYMIZATION_KEY` | nessuno | obbligatoria, almeno 32 byte e diversa dalla precedente |
| `HHC_INVENTORY_AUDIT_KEY_ID` | nessuno | ID canonico e non segreto, massimo 128 caratteri |
| `HHC_INVENTORY_AUDIT_POLICY_DIGEST` | nessuno | `sha256:<64 hex lowercase>` della policy autorizzativa |
| `HHC_CONTROL_PLANE_ARTIFACT_DIGEST` | nessuno | `sha256:<64 hex lowercase>` dell'artefatto/SBOM attestato |

L'inventory abilitato senza uno di questi valori fallisce lo startup. Le chiavi non devono comparire in CLI, ConfigMap, Helm values committati, crash dump o evidence. I digest devono provenire dalla release pipeline e non essere calcolati dall'applicazione sul proprio filesystem a runtime.

## 12. Rischi residui e prossimi incrementi

| Tema | Stato P1-0107 | Chiusura |
|---|---|---|
| deny OIDC/RBAC e invalid request | esito qualificato da P1-0108; non ancora nel journal transazionale | WP1-10 |
| audit secret bind/rotate/revoke | modello pronto, operazioni HTTP non esposte | secret management increment/WP1-10 |
| sink indipendente e SIEM | non consegnati | WP1-10/M4 |
| checkpoint esterno/WORM | non consegnato | WP1-10/M4 |
| key rotation multi-key | singola chiave attiva | WP1-10 |
| partitioning/retention purge governato | schema iniziale non partizionato | WP1-10/capacity qualification |
| performance hot Facility | isolamento progettato, benchmark pendente | P1-0110/G2 |
| restore/failover reale | atomicità testata, drill pendente | WP1-11/M5 |

## 13. Fonti ufficiali e data di verifica

Fonti verificate il **16 settembre 2026**:

- PostgreSQL 18, advisory e explicit locking: <https://www.postgresql.org/docs/18/functions-admin.html>
- PostgreSQL 18, trigger: <https://www.postgresql.org/docs/18/trigger-definition.html>
- PostgreSQL 18, function security: <https://www.postgresql.org/docs/18/perm-functions.html>
- PostgreSQL 18, MVCC: <https://www.postgresql.org/docs/18/mvcc-intro.html>
- W3C Trace Context Recommendation: <https://www.w3.org/TR/trace-context/>
- OWASP Logging Cheat Sheet: <https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html>
- Regolamento (UE) 2016/679 (GDPR): <https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32016R0679>
- Direttiva (UE) 2022/2555 (NIS2): <https://eur-lex.europa.eu/eli/dir/2022/2555/oj/eng>
- Regolamento (UE) 2025/327 (EHDS), testo ufficiale: <https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32025R0327>

La documentazione normativa descrive requisiti tecnici e readiness, non costituisce parere legale. Applicabilità, basi giuridiche, retention e ruoli titolare/responsabile devono essere confermati per paese, organizzazione e trattamento prima del go-live.
