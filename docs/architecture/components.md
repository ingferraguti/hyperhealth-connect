# Architettura dei componenti

Stato: baseline architetturale 1.0  
Data di riferimento: 1 settembre 2026  
Vista: componenti logici e ownership

## 1. Obiettivi di modularità

La decomposizione protegge i confini che costituiscono il valore del prodotto: Connector SDK, Integration Envelope, Flow Runtime, Semantic Mapping Registry, Canonical Model, lineage, replay e governance. Apache Camel, HAPI, IPF, dcm4che, Kafka, PostgreSQL, Keycloak e componenti OHDSI sono dipendenze sostituibili dietro adapter HHC.

Regole:

- un modulo possiede il proprio modello e non espone tabelle come API;
- le chiamate sincrone sono limitate al percorso che richiede una risposta immediata;
- le integrazioni fra piani usano contratti versionati;
- nessun modulo analytics è dipendenza del fast path;
- il tenant scope è esplicito in command, query, event e persistence context;
- le operazioni privilegiate producono audit nella stessa transazione logica;
- errori clinicamente rilevanti non sono ridotti a log testuali.

## 2. Control Plane Application

### 2.1 Identity and Access Context

Responsabilità:

- validare token federati e workload identity;
- tradurre claim esterni in subject HHC;
- applicare RBAC, ABAC, tenant/facility scope e purpose;
- gestire step-up, break-glass e sessioni privilegiate;
- produrre decision record per azioni sensibili.

Non conserva password dell'utente e non espone direttamente Keycloak/IdP al dominio.

### 2.2 Organization and Tenancy Context

Gestisce Tenant, Organization, Facility, Application, Endpoint, Runtime Cell, environment e data residency policy. Mantiene identificativi immutabili, lifecycle e relazioni temporali. Impedisce riferimenti cross-tenant e applica policy inheritance.

### 2.3 Asset Registry

Catalogo comune di connector, flow, mapping, contract, schema, terminology package, canonical type, projection, dataset e owner. Ogni asset ha:

- logical ID e versione immutabile;
- stato `DRAFT`, `IN_REVIEW`, `APPROVED`, `DEPRECATED`, `REVOKED`;
- scope e compatibilità;
- checksum, firma e provenance;
- dipendenze e impatto calcolabile;
- data di validità e deprecazione.

### 2.4 Flow Design and Validation

Interpreta la definizione dichiarativa HHC, verifica schema, riferimenti, tipi, capability, policy, cicli, route non raggiungibili e risorse. Genera un execution plan deterministico e una manifest delle dipendenze. La UI è un client di questo componente.

### 2.5 Approval and Change Workflow

Gestisce separation of duties, review tecnica/clinica/privacy/security, change ID, approvazioni, scadenze ed emergency change. Un autore non può auto-approvare modifiche ad alto impatto se la policy richiede four-eyes.

### 2.6 Deployment Orchestrator

Compila release bundle, firma, pianifica rollout per runtime cell, raccoglie preflight/health, applica canary e rollback. Mantiene desired state e observed state separati. Non invia codice arbitrario ai worker: distribuisce soltanto artefatti supportati e verificati.

### 2.7 Policy Service

Valuta policy su scope, data locality, encryption, retention, connector trust, release gate, replay, export e secondary use. La decisione restituisce `permit/deny`, motivazione, policy version e obblighi da applicare.

### 2.8 Audit Command Gateway

Costruisce eventi audit normalizzati e li invia a buffer durabile. Per operazioni critiche, il command non è considerato confermato finché l'audit minimo non è durable. Il fallimento dell'export SIEM non blocca il journal locale ma genera allarme.

## 3. Connector SDK e gateway

### 3.1 Connector SPI

Il contratto pubblico include:

- descriptor e capability;
- schema di configurazione e secret reference;
- lifecycle `install → validate → start → quiesce → stop → upgrade`;
- input/output port tipizzate;
- health, readiness e dependency status;
- timeout/retry/idempotency hooks;
- metriche standard e protocol-specifiche;
- contract test kit e compatibility version.

### 3.2 Protocol Adapter

Adapter ufficiali incapsulano Camel/HAPI/IPF/dcm4che e traducono errori nativi nel modello HHC. Non possono accedere ai database del Control Plane. Le estensioni di terze parti operano con permessi minimi e, dove pratico, in processi o sandbox separati.

### 3.3 Ingress Controller

Termina connessioni, autentica endpoint, applica connection/rate/size limit, assegna correlation ID e seleziona il flow. Per MLLP gestisce framing e ACK; per HTTP status e idempotency key; per DICOM association e presentation context.

### 3.4 Egress Controller

Gestisce pool di connessioni, timeout, retry, circuit breaker, TLS/mTLS, proxy e rate limit per destinazione. Espone delivery receipt normalizzata e non decide autonomamente se un errore clinico sia retryable: usa la policy del connector/flow.

## 4. Data Plane Runtime

### 4.1 Runtime Agent

- stabilisce identità della cella;
- scarica bundle via pull autenticato;
- verifica firma, checksum, scope e compatibility;
- esegue preflight su secrets, endpoint e risorse;
- mantiene ultima configurazione valida;
- attiva versione atomica per flow;
- riporta observed state senza payload.

### 4.2 Flow Scheduler

Assegna eventi a worker pool rispettando tier, fairness, partition key e quote. Usa code separate per fast path, standard e batch. Evita starvation e limita concurrency verso sistemi fragili.

### 4.3 Flow Execution Engine

Esegue l'execution plan senza modifiche runtime non dichiarate. Ogni step riceve un event context immutabile e produce un nuovo derivato o una decisione. Time budget e cancellation vengono propagati. Lo stato di esecuzione è persistito ai durability point.

### 4.4 Integration Envelope Service

Genera e valida HHC Integration Envelope. Impedisce collisioni di message ID, normalizza timestamp tecnici e mantiene riferimenti al raw e ai derivati. Il contenuto clinico non viene copiato nell'envelope.

### 4.5 Raw Event Writer

Scrive il payload in streaming su object storage, calcola checksum, usa encryption context corretto e registra metadata minimi. La scrittura è idempotente per content/message key e supporta object lock e retention.

### 4.6 Parser and Validator

Seleziona parser e profilo per standard/versione. Distingue errori di transport, framing, syntax, profile, business e semantic validation. Conserva il raw anche se il parse fallisce, quando policy e sicurezza lo permettono.

### 4.7 Technical Mapping Engine

Esegue trasformazioni deterministiche e pure ove possibile. I lookup esterni sono dichiarati, versionati, timeout-bound e cache-aware. Script ad hoc non hanno accesso illimitato a filesystem, rete o secrets.

### 4.8 Routing and Delivery Engine

Valuta route, fan-out e transazioni logiche. Ogni destinazione ha stato indipendente. Non interpreta delivery parziale come successo globale se il contratto richiede tutte le destinazioni; espone la semantica di completion scelta.

### 4.9 Reliability Coordinator

Calcola idempotency key, deduplica, ordering, retry schedule, circuit state e DLQ transition. Mantiene un delivery ledger per riconciliazione. Non implementa distributed transaction con sistemi legacy se non esplicitamente supportata.

### 4.10 Local Durable Spool

Conserva eventi accettati quando servizi centrali non sono raggiungibili. È cifrato, bounded, monitorato e protetto da policy di eviction che non elimina eventi confermati. Raggiunta la soglia critica, il gateway applica back-pressure o risposta di errore prevista dal protocollo.

## 5. Semantic Plane

### 5.1 Canonical Model Registry

Conserva schema, invarianti, compatibility rule e migration del Canonical Semantic Event. Le versioni pubblicate sono immutabili; breaking change richiede nuova major version e dual-read/dual-write temporaneo se necessario.

### 5.2 Semantic Mapping Engine

Applica mapping da concetto locale a terminologia e canonical type. Restituisce valore, confidence/status, rule ID, vocabulary snapshot e provenance. Ambiguità o assenza di mapping sono stati espliciti, non valori di default.

### 5.3 Semantic Mapping Registry

Gestisce scope, validità temporale, autore, revisore, evidenza e relazione tra mapping. Supporta proposte assistite, ma soltanto mapping approvati alimentano percorsi production che richiedono standardizzazione.

### 5.4 Vocabulary Service

Espone ricerca e navigazione gerarchica su snapshot autorizzati. Separa vocabolari standard, locali e custom concepts. Le cache sono keyed per snapshot per evitare risultati mescolati durante upgrade.

### 5.5 Data Quality Rules Engine

Esegue controlli di conformità, completezza e plausibilità al livello raw/parsed/canonical. Le regole hanno severity, scope, owner e disposition: warn, quarantine, block o allow-with-evidence.

### 5.6 Lineage Builder

Registra nodi e relazioni fra raw, parsed, canonical, transformed, delivery e projection. Usa riferimenti stabili, non copia i payload. Il lineage è append-only; una correzione aggiunge una nuova derivazione.

## 6. OMOP e Analytics

### 6.1 OMOP Schema Registry

Conserva metadata versionati di tabelle, campi, datatype, vincoli, domini e convenzioni ETL. Genera validatori e artefatti DB senza trattare il DDL come unica fonte semantica.

### 6.2 OMOP Projection Engine

Trasforma canonical event in record di staging con mapping versionato. Gestisce identity token, observation period, visit linkage, source value/concept e standard concept. Non scrive direttamente nel CDM `READY`.

### 6.3 Dataset Builder

Materializza una dataset version, applica merge/incremental rule e isola la build dalla versione pubblicata. Registra input watermark, mapping, vocabulary e software version.

### 6.4 Dataset Quality Gate

Orchestra structural validation, semantic checks, DataQualityDashboard e Achilles. Aggrega eccezioni approvate e produce manifest/evidence. Solo un gate riuscito consente `READY`.

### 6.5 Cohort and Feature Services

Versionano definizioni, traducono tramite componenti OHDSI compatibili, eseguono in work schema e pubblicano risultati soggetti a purpose e disclosure policy. Il query budget impedisce scansioni incontrollate.

### 6.6 Governed AI Tool Gateway

Espone primitive tipizzate a modelli e agenti. Applica purpose, dataset, row/cell suppression, rate limit e audit. Blocca SQL libero, accesso raw e mutazioni cliniche nella baseline.

## 7. Observability e audit

### 7.1 Telemetry Instrumentation

Libreria comune per trace, metriche e log strutturati. Applica redaction prima dell'export, limita cardinalità e inserisce build, cell, tenant, facility e flow ID quando consentito. Patient ID e payload non sono label.

### 7.2 Health Aggregator

Distingue:

- `alive`: processo funzionante;
- `ready`: può ricevere traffico;
- `degraded`: opera ma rischia lo SLO;
- `blocked`: non può processare uno scope;
- `dependency_down`: causa esterna identificata.

### 7.3 SLO Engine

Calcola availability, success rate, latency, queue age e freshness per service tier, flow e facility. Mantiene error budget e burn-rate alert su finestre brevi e lunghe.

### 7.4 Audit Journal

Normalizza eventi con actor, subject, action, resource, scope, purpose, reason, request ID, outcome, time e policy decision. Applica hash chaining/signature batches e retention separata dalla telemetria.

### 7.5 Evidence Package Builder

Esporta set limitati e firmati di audit, config, versioni e verifiche. Include manifest, checksum, filtri e richiedente; l'export stesso è evento privilegiato.

## 8. Error model comune

Categorie minime:

```text
TRANSPORT_ERROR      PROTOCOL_ERROR       PARSING_ERROR
VALIDATION_ERROR     MAPPING_ERROR        SEMANTIC_ERROR
AUTHENTICATION_ERROR AUTHORIZATION_ERROR  POLICY_ERROR
DESTINATION_ERROR    TIMEOUT              RESOURCE_EXHAUSTED
DATA_QUALITY_ERROR   STORAGE_ERROR        INTERNAL_ERROR
```

Ogni errore contiene `errorCode`, `category`, `severity`, `retryable`, `component`, messaggio sicuro, riferimento ai dettagli protetti, timestamp e trace ID. Le cause native non vengono esposte a utenti non autorizzati né usate come unico codice di business.

## 9. Transazioni e consistenza

- Metadata DB e audit usano outbox transazionale per pubblicare eventi senza dual-write fragile.
- Raw object e metadata adottano protocollo staged: upload temporaneo, checksum, commit metadata, finalize; job di reconciliation ripulisce orfani.
- Delivery esterna usa ledger e idempotency, non una transazione XA generica.
- Config activation è atomica per flow e cella; rollout enterprise può essere graduale ma ogni evento vede una versione precisa.
- Dataset analytics sono pubblicati per switch di manifest/schema/view, mai aggiornati in-place mentre sono interrogati.

## 10. Dipendenze consentite

```text
UI → Control Plane API
Control Plane → Registry/Policy/Deployment/Audit ports
Runtime → Connector SDK/Envelope/Flow ports
Semantic → Canonical/Mapping/Vocabulary/Lineage ports
Analytics → Canonical projection/OMOP ports

Vietato:
Runtime → tabelle Control Plane
Analytics → endpoint clinici operativi
Connector → database interni HHC
UI → database
Third-party extension → secrets non dichiarati
```

## 11. Strategia di estrazione in servizi

Un modulo diventa servizio separato solo se almeno uno dei seguenti fattori è dimostrato:

- profilo di scaling materialmente diverso;
- necessità di isolamento PHI/failure/security;
- lifecycle o ownership indipendente;
- tecnologia incompatibile con il processo ospite;
- recovery objective diverso;
- carico batch che minaccia il fast path.

La separazione non deve introdurre chatty calls o distributed transaction sul percorso Tier 0.

## 12. Test dei componenti

Ogni componente deve avere:

- unit e property test delle invarianti;
- contract test per porte pubbliche;
- test tenant-isolation negativi;
- failure injection e timeout test;
- test di concorrenza/idempotenza;
- benchmark dove è nel data path;
- test redaction e audit coverage;
- migration/compatibility test per stato persistente.

## 12. Fonti ufficiali

Consultate o riconfermate il 5 settembre 2026:

- [Kubernetes Components](https://kubernetes.io/docs/concepts/overview/components/)
- [OpenTelemetry specification](https://opentelemetry.io/docs/specs/otel/)
- [NIST SP 800-207 Zero Trust Architecture](https://csrc.nist.gov/pubs/sp/800/207/final)
- [OHDSI Common Data Model](https://ohdsi.github.io/CommonDataModel/)
