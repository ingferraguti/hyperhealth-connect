# Architettura dei container

Stato: baseline architetturale 1.0  
Data di riferimento: 1 settembre 2026  
Vista: C4 Container, prodotto enterprise finale

## 1. Principi di decomposizione

I container sono unità distribuibili o datastore con lifecycle e scaling distinti. L'architettura separa:

- **Control Plane**, che governa asset e deployment senza essere nel percorso dei messaggi;
- **Data Plane**, che esegue i flow e rimane operativo anche senza Control Plane;
- **Semantic Plane**, che gestisce normalizzazione, mapping, terminologie e lineage;
- **Analytics Plane**, che costruisce OMOP e serve uso secondario;
- **Platform Services**, che forniscono identity, storage, eventi, secrets e telemetry.

Il prodotto parte da moduli coesi, ma i confini di rete e persistenza qui definiti sono validi anche se più moduli condividono inizialmente lo stesso processo.

## 2. Vista complessiva

```text
                         ENTERPRISE ACCESS
                     WAF / Load Balancer / API GW
                                  │
               ┌──────────────────┴──────────────────┐
               ▼                                     ▼
        HHC Admin UI                           HHC Control Plane
                                                       │
                              signed release bundles   │ metadata/commands
               ┌───────────────────────────────────────┼──────────────┐
               ▼                                       ▼              ▼
        Runtime Cell A                           Runtime Cell B   Runtime Cell N
   Connector Gateway + Workers              local workers/bus    isolated/site
        │       │       │
        │       │       └── Raw/Object Store + local spool
        │       └────────── Event Bus / durable queues
        └────────────────── Clinical endpoints
               │
               ▼ async only
       Semantic & Projection Services ───► OMOP / Analytics Services
               │                                    │
               └──────── Metadata / Lineage ────────┘

Shared: Identity · PKI · Vault/KMS · PostgreSQL · Object Storage
        OpenTelemetry Collectors · Metrics/Logs/Traces · Audit Vault · SIEM
```

## 3. Catalogo dei container HHC

| Container | Piano | Responsabilità | Stato | Scaling |
|---|---|---|---|---|
| Admin Web UI | Control | Designer, inventory, deployment, explorer, audit e dashboard | Stateless | Repliche dietro LB/CDN privata |
| API Gateway / BFF | Control | API edge, rate limit, session context, request correlation | Stateless | Orizzontale per RPS |
| Control Plane Application | Control | Tenant, registry, policy, workflow, deployment e audit command | Stateless con DB | Orizzontale; leader solo per job esclusivi |
| Release Bundle Service | Control | Compila, firma, distribuisce e revoca bundle immutabili | Stateless + object store | Orizzontale; cache edge |
| Runtime Agent | Data | Registra la cella, verifica bundle, attiva/rollback e riporta health | Uno per cella, HA | Active/standby o quorum leggero |
| Connector Gateway | Data | Termina MLLP/HTTP/SOAP/SFTP/DICOM e applica limiti/protocol security | Stateless o session-aware | Per protocollo, porta e facility |
| Flow Worker | Data | Esegue pipeline Camel/HHC, mapping, routing e delivery | Stateless | Orizzontale per queue/partition |
| Durable Spool | Data | Buffer locale per outage WAN/broker e store-and-forward | Stateful | Replica locale o storage HA |
| Event Broker | Event | Buffer, fan-out, ordering per partizione, retry e replay operativo | Stateful | Cluster multi-zone |
| Contract Registry | Control/Event | OpenAPI/AsyncAPI/schema e compatibility rule | Stateful | HA con DB/object store |
| Raw Event Service | Data | Scrive raw immutabili, checksum, envelope e retention metadata | Stateless + object store | Orizzontale |
| Message Index Service | Data | Indicizza metadata tecnici per ricerca senza duplicare raw | Stateless + search store | Shard/replica per volume |
| Semantic Processing Service | Semantic | Canonical event, semantic mapping e data-quality rules | Stateless | Orizzontale per dominio/partition |
| Semantic Mapping Registry | Semantic | Asset, workflow, versioni e provenance dei mapping | Stateful | HA database |
| Vocabulary Service | Semantic | Ricerca, gerarchie, mapping e snapshot terminologici | Read-heavy | Repliche read-only, cache versionata |
| Lineage Service | Semantic | Grafo di derivazione e riferimenti a eventi/dataset | Stateless + DB | Partition per tenant/time |
| OMOP Projection Service | Analytics | Mappa canonical event in record/staging OMOP | Stateless batch/stream | Worker pool separato |
| Dataset Orchestrator | Analytics | Build, validate, publish, supersede e archive dataset | Stateful workflow | Leader election + worker |
| Quality/Characterization Workers | Analytics | DQD, Achilles e controlli HHC | Batch compute | Autoscaling per job |
| Clinical Analytics API | Analytics | Vocabulary, concept set, cohort, feature, quality e lineage | Stateless | Orizzontale; query guard |
| Cohort/Feature Workers | Analytics | Circe/CohortGenerator/FeatureExtraction e HADES adapter | Batch compute | Queue-based autoscaling |
| Audit Service | Platform | Ingest e query autorizzata di audit append-only | Stateless + immutable store | Orizzontale, buffer durabile |
| Telemetry Gateway | Platform | Colleziona, redige, campiona e inoltra OTel | Stateless con buffer | Per cella + livello enterprise |

## 4. Datastore e servizi infrastrutturali

| Servizio | Dati | Regola architetturale |
|---|---|---|
| PostgreSQL Platform | tenant, registry, deployment, policy, workflow e metadata | HA, PITR, schema isolati e migrazioni backward-compatible |
| S3-compatible Object Storage | raw, documenti grandi, bundle, evidence e backup export | Versioning, object lock, encryption e lifecycle |
| Kafka-compatible Event Broker | eventi operativi entro retention, command/event tecnici | Non è archivio clinico permanente; quorum multi-zone |
| Search Store | indice Message Explorer e metadata | Nessun payload completo; rebuildable dal metadata source |
| Audit Vault | audit append-only e tamper evidence | Admin ordinari non possono modificare/cancellare |
| OMOP PostgreSQL/DB target | CDM, vocabulary, results, cohort e work | Separato dal Platform DB; read-only per consumer ordinari |
| Metrics Store | serie temporali e SLO | Label cardinality limitata; nessun identificativo paziente |
| Log Store | log applicativi strutturati | redaction e retention breve/configurabile |
| Trace Store | span end-to-end | sampling risk-based; payload escluso |
| Vault/KMS/HSM | secrets, key encryption key e firma | Nessun secret persistito nelle configurazioni HHC |

## 5. Control Plane

### 5.1 Responsabilità

Il Control Plane gestisce asset dichiarativi, policy, approvazioni e desired state. Non riceve payload clinici nel funzionamento normale e non è sincrono nel data path.

### 5.2 Deployment

- almeno tre repliche applicative distribuite su zone diverse;
- API gateway ridondato e health-aware;
- PostgreSQL HA con failover orchestrato, backup PITR e replica DR;
- bundle store versionato, replicato e con object lock;
- job singleton protetti da leader election/lease;
- cache non autorevoli e ricostruibili;
- sessioni lato client o in store HA, mai sticky session come requisito.

### 5.3 Perdita del Control Plane

Durante l'indisponibilità:

- i Runtime Agent rifiutano nuovi bundle non verificabili;
- i flow esistenti continuano con l'ultima versione firmata e non revocata conosciuta;
- audit e telemetry sono bufferizzati localmente;
- API amministrative risultano indisponibili o read-only;
- i segreti già materializzati rimangono utilizzabili fino a expiry, salvo revoca locale;
- le celle applicano policy di autonomia e allertano prima della scadenza.

## 6. Runtime Cell

La Runtime Cell è l'unità minima di isolamento, capacity e recovery del Data Plane. Contiene gateway, worker pool, runtime agent, telemetry gateway e durable spool; può avere broker e raw store locali o usare servizi condivisi conformi alla policy.

### 6.1 Profili

| Profilo | Uso | Isolamento |
|---|---|---|
| Facility Cell | Un ospedale o sito con data locality | Rete, credenziali, spool e chiavi dedicati |
| Organization Cell | Più facility della stessa azienda | Pool condiviso con quote e partition per facility |
| Shared Enterprise Cell | Flussi non-PHI o comuni a più aziende | Ammessa solo dopo risk assessment |
| Dedicated Critical Cell | Tier 0, imaging ad alto volume o dispositivi | Nodi, broker/storage e capacity riservati |
| DR Cell | Standby warm/hot in altro sito | Configurazione sincronizzata, ingress disattivo o fenced |

### 6.2 Regole

- Un worker processa soltanto scope presenti nel bundle verificato.
- Le code includono tenant, facility, flow e partition key.
- Il gateway applica limiti prima di allocare risorse costose.
- Il raw è reso durabile prima dell'ACK quando il contratto richiede RPO locale 0.
- La semantic/analytics pipeline non condivide thread pool o code con il fast path.
- Restart e autoscaling non cambiano ordering scope né idempotency key.

## 7. Semantic Plane

Il Semantic Plane riceve eventi attraverso il broker o un'interfaccia asincrona durabile. Produce rappresentazioni canoniche e mapping observations, ma non muta il raw. Le sue funzioni sono separabili per dominio clinico, versione del canonical model e organization.

Il Vocabulary Service usa snapshot immutabili. Una versione nuova viene caricata in parallelo, validata, attivata per nuovi processi e mantenuta per replay/riproducibilità finché la retention lo richiede.

## 8. Analytics Plane

L'Analytics Plane è separato per sicurezza, performance e lifecycle:

- usa database e credenziali diversi dal runtime clinico;
- elabora eventi o snapshot mediante code dedicate;
- limita CPU, memoria e concorrenza per proteggere servizi condivisi;
- rende un dataset interrogabile solo nello stato `READY`;
- applica quality gate, purpose/permit e output policy;
- può essere interamente dedicato a un tenant o progetto.

## 9. Comunicazioni

| Origine → Destinazione | Protocollo logico | Sicurezza | Semantica |
|---|---|---|---|
| Browser → Gateway | HTTPS | TLS, OIDC, MFA, CSRF/CSP | API UI versionata |
| Gateway → Control Plane | HTTPS/gRPC interno | mTLS workload identity | command/query API |
| Control Plane → Runtime Agent | pull HTTPS o message channel | mTLS, bundle firmati | desired state/version |
| Gateway → Worker | in-process o queue | network policy/mTLS | HHC Envelope |
| Worker → Broker | protocollo broker | mTLS/SASL, ACL | topic contract versionato |
| Worker → Raw Store | S3 API | mTLS, scoped credentials | immutable object + checksum |
| Runtime → Telemetry Gateway | OTLP | mTLS, redaction | metric/log/trace schema |
| Semantic → OMOP Projection | event contract | mTLS, ACL | canonical event versionato |
| Analytics API → OMOP | JDBC/DB protocol | service identity, read-only | governed query |

Comunicazioni tra site attraversano link cifrati e autenticati. Nessun database viene esposto direttamente su rete pubblica.

## 10. Scalabilità

- Gateway: scale-out per protocollo/porta e connection count.
- Worker: scale-out per lag, queue age, CPU e service tier; non solo CPU.
- Broker: partizioni calcolate da throughput, ordering e recovery time.
- Semantic: scale-out per dominio e versione; cache locale immutabile.
- Search: shard per tenant/time e indici con lifecycle.
- OMOP: ingest separato dalle query; partizionamento fisico valutato per DB target.
- Batch analytics: queue dedicata e budget per tenant, con preemption a favore dei job operativi di qualità.

## 11. Alta disponibilità e recovery

| Container | HA locale | Recovery di sito |
|---|---|---|
| Control Plane | repliche multi-zone + DB failover | secondary control plane + restore/PITR |
| Runtime Agent/Gateway/Worker | più repliche, anti-affinity e PDB | cella DR con fencing e DNS/LB switch |
| Broker | quorum e replica multi-zone | cluster secondario/replicazione con checkpoint |
| Raw/Object Store | erasure coding/replica + versioning | replica cross-site + copie immutabili |
| Platform DB | synchronous HA + PITR | async replica e backup cyber-vault |
| Audit | buffer locale + immutable replica | replica separata e chain verification |
| OMOP | HA secondo tier dataset | rebuild da canonical/raw oppure replica/backup |

Il DR non si limita a riavviare container: comprende identity, PKI, DNS, secrets, network routes, storage, configurazione firmata, audit e riconciliazione. Ogni topologia ha un ordine di recovery documentato.

## 12. Upgrade e compatibilità

1. Gli artefatti sono content-addressed e firmati.
2. Control Plane e worker supportano una matrice di compatibilità `N/N-1` dichiarata.
3. Le migrazioni DB usano expand/migrate/contract, mantenendo rollback finché la release non è promossa.
4. I bundle specificano minimum/maximum runtime version.
5. Il rollout procede canary → facility pilota → organization → tenant.
6. Health, error budget e data reconciliation bloccano automaticamente la promozione.
7. Un evento conserva le versioni effettivamente applicate durante rollout misti autorizzati.

## 13. Requisiti di deploy

- immagini minimali e non-root;
- filesystem root read-only dove possibile;
- resource request/limit e disruption budget;
- anti-affinity per repliche critiche;
- network policy default-deny;
- secret injection temporanea, mai in environment dump o manifest;
- admission policy per firma, SBOM e provenance;
- logging strutturato e health endpoint uniformi;
- backup class e restore runbook per ogni componente stateful.

## 14. Riferimenti

- `system-context.md`
- `components.md`
- `data-flow.md`
- `scalability-and-resilience.md`
- `data-architecture.md`
- `../deployment/deployment-topologies.md`
- `../deployment/high-availability-and-dr.md`
