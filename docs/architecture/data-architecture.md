# Architettura dei dati

Stato: baseline architetturale 1.0  
Data di riferimento: 1 settembre 2026  
Target: dati sanitari enterprise, multi-tenant e multi-CDM

## 1. Obiettivo

Questo documento definisce domini dati, datastore, ownership, isolamento, consistenza, cifratura, retention, backup, lineage e lifecycle. Il principio centrale è usare storage specializzati senza creare copie incontrollate di dati sanitari.

## 2. Principi

1. **Raw immutabile**: il payload ricevuto non viene modificato; correzioni e reprocessing generano nuovi derivati.
2. **Metadata separati dal contenuto**: ricerca e monitoraggio usano metadata minimizzati, non copie del payload.
3. **Operational e analytical separation**: OMOP e query di ricerca non competono con il data path clinico.
4. **Scope esplicito**: tenant, organization, facility, purpose e classification accompagnano ogni record/oggetto.
5. **Encryption context per confine**: chiavi e namespace possono essere dedicati per tenant/organization.
6. **Least retention**: ogni categoria ha retention e legal basis; “conservare tutto” non è una default policy.
7. **Rebuildability dichiarata**: cache e indici sono ricostruibili; raw, audit e mapping approvati sono autorevoli secondo ruolo.
8. **No hidden state**: configurazioni logiche sono esportabili e firmate; il DB non è l'unica copia.
9. **Versioned truth**: schema, mapping, terminology e dataset version sono parte del dato.
10. **Evidence before deletion**: lifecycle e cancellazioni producono prova, senza conservare il dato cancellato nell'evidenza.

## 3. Domini dati e ownership

| Dominio | Owner logico | Autorevolezza | Mutabilità |
|---|---|---|---|
| Organization/Tenancy | Control Plane | registry HHC | temporal/versioned |
| Configuration Assets | Asset Registry + Git | release bundle approvato | versioni immutabili |
| Runtime Desired State | Deployment Orchestrator | Control Plane | versioned |
| Runtime Observed State | Runtime Agent | cella di origine | append/update tecnico |
| Raw Events | Raw Event Service | payload ricevuto HHC | immutabile |
| Processing/Delivery Ledger | Flow Runtime | outcome HHC | append-only state transitions |
| Parsed/Canonical/Transformed | rispettivo engine | derivati HHC | immutabili per versione |
| Semantic Mapping | Mapping Registry | mapping approvato | versioni immutabili |
| Vocabulary Snapshot | Vocabulary Service | package verificato | immutabile |
| Lineage | Lineage Service | grafo HHC | append-only |
| Audit | Audit Service | journal HHC | append-only/tamper-evident |
| OMOP Dataset | Dataset Orchestrator | dataset version `READY` | immutable publication |
| Metrics/Logs/Traces | Observability | evidenza operativa non clinica | lifecycle breve |

Il system of record clinico resta esterno. Il raw dimostra cosa HHC ha ricevuto, non che il contenuto fosse clinicamente corretto.

## 4. Classificazione dati

| Classe | Esempi | Controlli minimi |
|---|---|---|
| `PUBLIC` | documentazione pubblica, schema standard | integrità e provenance |
| `INTERNAL` | config non sensibile, topology metadata | autenticazione e least privilege |
| `CONFIDENTIAL` | endpoint, mapping proprietari, capacity | cifratura e accesso scoped |
| `PERSONAL` | account, audit utente | GDPR controls e retention |
| `HEALTH_DATA` | payload clinico e derivati identificabili | special category controls, encryption, strict audit |
| `PSEUDONYMISED_HEALTH` | token paziente e dataset OMOP | key separation, purpose limitation, re-identification control |
| `ANONYMISED` | output con rischio ragionevolmente escluso | assessment documentato; riesame del rischio |
| `SECRET` | password, token, private key | solo vault/HSM; mai nei DB applicativi |

Classificazione e caveat seguono l'oggetto. I dati pseudonimizzati restano dati personali quando re-identificabili.

## 5. Schema fisico di riferimento

### 5.1 Platform PostgreSQL

```text
platform_core      tenant, organization, facility, application, endpoint
asset_registry     flow, connector, mapping, contract, versions, dependencies
deployment         desired/observed state, rollout, release manifest
policy             policy metadata and decisions
runtime_metadata   envelope index, processing and delivery ledger
semantic_registry  mapping metadata, canonical schema metadata
lineage            nodes/edges and provenance references
audit_index        query index; non-authoritative copy of audit metadata
outbox              transactional events pending publication
```

Gli schemi sono ownership boundary logici. L'accesso avviene tramite service role; migration user e runtime user sono distinti. Row-level security può aggiungere difesa, ma non sostituisce authorization applicativa e isolamento fisico quando richiesto.

### 5.2 Object storage

```text
hhc-raw-<scope>          immutable raw payload
hhc-derived-<scope>      optional large parsed/transformed artifacts
hhc-release-bundles      signed configuration artifacts
hhc-evidence             signed audit/quality evidence packages
hhc-backup-export        encrypted exports for cyber-vault
```

Bucket/prefix policy vieta listing cross-scope. Object key non contiene identificativi paziente. Metadata sensibili sono cifrati o conservati nel Platform DB, non come tag visibili al provider.

### 5.3 Event broker

Topic naming include environment e dominio, mentre tenant/facility sono header autenticati e ACL/partition policy. Per tenant ad alto isolamento si usano cluster/topic dedicati. Il broker conserva eventi per la finestra operativa e di replay, non per retention clinica di lungo termine.

### 5.4 Search

L'indice contiene message ID, correlation ID, scope, flow, standard, type, time, state, error category, endpoint e token di ricerca autorizzato. Payload, note e valori clinici non sono indicizzati per default. L'indice è rebuildable e può avere retention più breve del raw.

### 5.5 OMOP

```text
omop_cdm        tabelle CDM della dataset version pubblicata
omop_vocab      vocabulary snapshot compatibile
omop_results    risultati di quality e characterization
omop_cohort     cohort e cohort definitions
omop_work       staging e temporary workspace isolati
omop_metadata   manifest, lineage, release e quality status HHC
```

Gli utenti analitici hanno read-only su `omop_cdm` e `omop_vocab`; i worker scrivono soltanto negli schemi di competenza. Una build non aggiorna in-place la dataset version consultata.

## 6. Multi-tenancy e data locality

### 6.1 Modelli supportati

| Modello | Uso | Isolamento |
|---|---|---|
| Shared DB/shared schema | sviluppo o basso rischio | tenant key + RLS + app authorization |
| Shared DB/separate schema | organization standard | schema/role separati |
| Separate DB | organization sensibile | database, backup e key separati |
| Separate cluster/account | tenant regolato o critical | failure/admin domain dedicato |
| Facility-local payload | data residency | raw/spool nel sito; metadata minimizzati centrali |

La scelta è registrata come `IsolationProfile` e non può essere abbassata senza change e risk approval.

### 6.2 Identificativi

Gli ID HHC sono opachi e globalmente univoci. Identificativi locali, MRN e codici nazionali non vengono usati come primary key o object key. Il crosswalk è conservato nel boundary autorizzato e cifrato; un token diverso per tenant/purpose riduce correlazioni indebite.

### 6.3 Cifratura

- TLS/mTLS in transito;
- encryption at rest per DB, object, broker e backup;
- envelope encryption con data encryption key e key encryption key in KMS/HSM;
- encryption context vincolato a tenant/organization/dataset;
- key rotation senza riscrittura immediata tramite rewrapping quando supportato;
- crypto-shredding solo se compatibile con legal hold e backup lifecycle;
- recovery key separata e testata.

## 7. Raw Event Store

### 7.1 Write protocol

1. stream verso temporary object;
2. checksum e size validation;
3. server-side encryption con context;
4. commit di envelope/metadata con outbox;
5. finalize/rename logico a object immutabile;
6. object lock/retention applicati;
7. reconciliation degli oggetti orfani.

### 7.2 Access protocol

Il raw è restituito solo tramite servizio, con authorization, purpose, reason, masking/preview e audit. URL prefirmati hanno TTL minimo, audience/scope quando possibile e non sono scritti nei log. Download massivo è disabilitato per default.

### 7.3 Retention

La retention è definita per contratto, dato, flow, facility e giurisdizione. Il sistema supporta:

- retention minima/massima;
- legal hold con owner e scadenza;
- lifecycle hot/warm/cold;
- cancellazione verificata e propagazione alle repliche/backups secondo schedule;
- sospensione delle proiezioni quando il purpose scade;
- evidence di cancellazione priva del contenuto eliminato.

## 8. Processing ledger e delivery ledger

Il ledger è partizionato per tempo/scope e contiene transizioni, non payload. Usa optimistic concurrency o append sequence per impedire update persi. Ogni attempt ha:

- attempt ID;
- step/destination;
- started/finished time;
- input/output reference e checksum;
- versioni applicate;
- outcome/error category/retryable;
- receipt o stato unknown;
- worker/build identity.

Le query operative recenti usano indice; lo storico passa a storage più economico mantenendo le esigenze di audit e reconciliation.

## 9. Audit architecture

### 9.1 Event schema

Ogni record comprende:

```text
auditEventId, sequence, occurredAt, recordedAt
actorId, actorType, authenticationContext
action, resourceType, resourceId, scope
purpose, reason, change/ticket/approval reference
requestId, traceId, sourceIp/networkZone
policyId/version/decision
outcome, errorCode
previousHash, hash/signatureBatchRef
```

### 9.2 Tamper evidence

Gli eventi sono hash-chained per stream/partition e firmati in batch con timestamp affidabile. Checkpoint sono copiati in un trust domain separato. Gap, reorder o firma invalida generano security incident.

### 9.3 Separazione dei ruoli

- applicazioni possono appendere, non modificare;
- auditor può leggere scope autorizzato, non amministrare retention;
- platform admin non può cancellare audit;
- key custodian e audit admin sono distinti;
- export richiede motivo e viene auditato.

### 9.4 Audit vs log

Audit prova un'azione rilevante ed è conservato secondo policy dedicata. Log serve diagnosi ed è più volatile. Non si usa il log applicativo come unico audit trail.

## 10. Semantic e lineage data

Mapping, canonical schema e vocabulary snapshot sono immutabili dopo pubblicazione. Il lineage è un grafo di riferimenti:

```text
Raw Event
  ├── parsedWith → Parser/Profile Version
  ├── transformedWith → Technical Mapping Version
  ├── mappedWith → Semantic Mapping + Vocabulary Snapshot
  ├── deliveredAs → Target Artifact + Receipt
  └── projectedInto → Dataset Version + OMOP Domain
```

Il grafo deve rispondere sia upstream (“da dove viene questa riga?”) sia downstream (“dove è stato usato questo evento?”), rispettando autorizzazioni e cancellazioni.

## 11. OMOP data lifecycle

1. `BUILDING`: staging isolato con input watermark e manifest.
2. `VALIDATING`: scritture sospese salvo job di correzione controllato.
3. `FAILED`: non esposto; findings e evidence conservati.
4. `READY`: pubblicato atomicamente tramite catalog/view/connection alias.
5. `SUPERSEDED`: read-only per riproducibilità entro retention.
6. `ARCHIVED`: rimosso dal servizio standard e conservato/cancellato secondo policy.

Il manifest include CDM version, vocabulary snapshot, mapping versions, source periods, record counts, checksums aggregati, quality results e software build. Le tabelle OMOP richieste esistono anche se vuote secondo la versione adottata.

## 12. Uso secondario e secure processing environment

L'accesso è mediato da `DataUseGrant` con:

- permit/authority reference;
- purpose e prohibited use;
- dataset e campi consentiti;
- subject/organization autorizzati;
- start/end e revocation state;
- pseudonymisation profile;
- query/compute/output budget;
- cell suppression e disclosure rule;
- audit e retention.

Il secure processing environment vieta internet/egress non autorizzato, clipboard/download se richiesto, monta dataset read-only, registra command/job e sottopone output a review. Le chiavi di re-identificazione non sono presenti nell'ambiente.

## 13. Consistenza e transazioni

- Platform DB: ACID per registry e state transition locali.
- DB→broker: transactional outbox/inbox.
- Object→metadata: staged commit + reconciliation.
- Broker→consumer: at-least-once + idempotent ledger.
- Delivery esterna: receipt + business reconciliation.
- Dataset publication: immutable build + atomic pointer switch.
- Cross-site: consistenza eventuale entro RPO dichiarato; fencing evita writer concorrenti.

Non si usa XA fra sistemi sanitari eterogenei. Le saghe sono esplicite, con compensazione solo se clinicamente e tecnicamente valida.

## 14. Backup e recovery per classe

| Dato | Backup | Recovery validation |
|---|---|---|
| Platform DB | continuous log/PITR + full | restore automatico, FK/count/checksum |
| Raw store | replica + versioning + immutable backup | sample/full checksum e object manifest |
| Audit | replica immutabile separata | chain/signature verification |
| Broker | replica + checkpoint; raw come recovery source | offset/ledger reconciliation |
| Search | snapshot opzionale | rebuild e query parity |
| Config bundle | Git + signed object store + cyber vault | firma e deploy dry-run |
| OMOP | DB backup/replica o deterministic rebuild | manifest, DQD e count parity |
| Vocabulary | package immutabili e checksum | signature/license/version validation |
| Secrets/keys | vault replication/escrow controllato | recovery ceremony periodica |

Backup è cifrato, catalogato, con owner, expiry e restore runbook. “Job riuscito” non prova recuperabilità: restore e applicative validation sono obbligatori.

## 15. Data deletion e diritti

La richiesta viene trasformata in un case autorizzato, non in una cancellazione diretta. Il processo:

1. identifica scope e sistemi autorevoli;
2. verifica base giuridica, obblighi di conservazione e legal hold;
3. localizza raw, derivati, indici, dataset e export tramite lineage;
4. corregge, restringe, cancella o rende inaccessibile secondo decisione;
5. propaga tombstone/restriction dove previsto;
6. aggiorna backup expiry senza riscrivere backup immutabili non scaduti;
7. produce evidence e segnala elementi non raggiungibili.

Dataset anonimi non sono modificati come dati personali solo dopo assessment documentato di anonimizzazione effettiva.

## 16. Data quality

Quality metrics sono dati versionati e includono:

- conformità a schema/profilo;
- completezza per campo e dominio;
- plausibilità temporale/clinica;
- duplicati e collisioni;
- mapping coverage e ambiguity;
- source-to-canonical e canonical-to-target reconciliation;
- freshness e lag;
- DQD/Achilles results per OMOP.

Le soglie sono definite per data product e use case. Una media enterprise non deve nascondere regressioni di una facility.

## 17. Osservabilità dello storage

- latency/error/throttle per DB, object e broker;
- capacity, growth rate e forecast;
- replication lag e last successful backup;
- last verified restore;
- orphan object/outbox/inbox backlog;
- index freshness e rebuild status;
- encryption/key/certificate expiry;
- retention/deletion backlog;
- audit chain validation status;
- OMOP dataset freshness e quality state.

## 18. Anti-pattern vietati

- payload clinici nei log, trace tag o metric label;
- patient ID come object key o primary key enterprise;
- stesso DB user per migration, runtime, analytics e supporto;
- query analytics sul ledger operativo;
- modifica in-place del raw, mapping pubblicato o dataset `READY`;
- replica scambiata per backup;
- backup nello stesso account e security domain del primario;
- indici non ricostruibili trattati come system of record;
- cancellazioni manuali senza workflow e lineage;
- condivisione cross-tenant basata soltanto su convenzioni applicative.

## 19. Decisioni da mantenere negli ADR

- storage separation e PHI logging policy;
- raw retention e object immutability;
- tenant isolation profile;
- encryption/key hierarchy;
- audit tamper-evidence model;
- canonical model e semantic lineage;
- OMOP version e multi-CDM;
- dataset publication/quality gate;
- secondary-use permit e secure environment boundary.

## 19. Fonti ufficiali

Consultate o riconfermate il 5 settembre 2026:

- [PostgreSQL 18 point-in-time recovery](https://www.postgresql.org/docs/current/continuous-archiving.html)
- [PostgreSQL 18 warm standby](https://www.postgresql.org/docs/current/warm-standby.html)
- [OpenTelemetry data collection and security](https://opentelemetry.io/docs/security/)
- [Regolamento UE 2016/679 GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [Regolamento UE 2025/327 European Health Data Space](https://eur-lex.europa.eu/eli/reg/2025/327/oj)
