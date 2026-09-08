# Topologie di deployment

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Platform Engineering e Architecture |
| Target | On-premises, private cloud, hybrid e multi-site |

## 1. Finalità

Questo documento definisce le topologie supportabili senza fork del prodotto. Kubernetes è la baseline enterprise; container/Compose è un profilo controllato per sviluppo, demo, edge limitato o singola appliance, non una dichiarazione di alta disponibilità.

## 2. Unità di deployment

| Unità | Responsabilità | Failure/scaling boundary |
|---|---|---|
| Control Plane | tenancy, registry, policy, approval e deployment | piattaforma governance |
| Runtime Cell | connector, flow, spool, delivery e audit buffer | facility/site/tenant secondo policy |
| Semantic Services | canonical, mapping, vocabulary e DQ | tenant/region e snapshot |
| Analytics Cell | OMOP, job e risultati | dataset/purpose |
| Shared Platform Services | identity, PKI/KMS, registry, telemetry | trust domain dichiarato |

I payload restano nella Runtime Cell quando la residency policy lo impone. Nessuna topologia cambia la gerarchia Tenant→Organization→Facility→Application→Endpoint.

## 3. Profili supportati

### 3.1 Developer/CI

Container effimeri, dati sintetici, identity locale e dipendenze reali minime. Non è supportato per PHI o disponibilità contrattuale. Il manifest fissa image digest, porte dinamiche, seed e cleanup.

### 3.2 Compose controllato

Singolo host o piccola VM set per laboratorio, demo e pilot non critico. Include Control Plane, una Runtime Cell, PostgreSQL, object storage, broker se necessario e collector. Backup esterno obbligatorio se conserva stato. Limiti dichiarati: host e storage possono essere single point of failure; niente claim 99,99%.

### 3.3 Kubernetes single-site

Cluster production-grade su almeno tre failure domain logici/fisici disponibili. Control Plane HHC e Runtime Cell possono condividere cluster solo con namespace, node pool, identity, network policy, quota e storage class separati. Tier 0 usa replica, anti-affinity/topology spread, PDB, readiness/startup probe e capacity N+1.

### 3.4 Hub centrale con Runtime Cell locali

Control Plane centrale governa più aziende/facility; le celle locali processano traffico e raw. Comunicazione outbound/pull dove possibile, mTLS, bundle firmati e metadata minimizzati. Ogni cella mantiene 24 ore di autonomia, queue/audit capacity e last-known-good.

### 3.5 Multi-site active/passive

Site primario processa; site DR riceve replica/backup e mantiene infrastruttura warm/hot secondo tier. Failover richiede fencing, promotion, DNS/routing, identity/KMS e reconciliation. È il default DR quando active/active non è giustificato.

### 3.6 Multi-site active/active

Consentito solo per workload partitionabili con ownership/fencing espliciti. Partition key, home cell/site, conflict policy e routing impediscono doppia scrittura. Non si dichiara active/active per il solo fatto che esistono repliche in due siti.

### 3.7 Deployment dedicato

Tenant o facility ad alto rischio può avere cluster, Runtime Cell, database, key domain e telemetry pipeline dedicati usando gli stessi artifact. Il dedicated profile aumenta isolamento ma non elimina governance, observability o test.

## 4. Baseline Kubernetes enterprise

- control plane cluster altamente disponibile gestito o self-managed qualificato;
- worker distribuiti per zone/rack e node pool per trust/workload;
- container runtime e versioni Kubernetes nella support matrix;
- namespace per plane/cell/environment, senza affidarsi al namespace come unico security boundary;
- default-deny network policy e egress allowlist;
- workload identity, short-lived credential e secret CSI/vault equivalent;
- Pod Security Standards restricted o eccezione motivata;
- image registry privato, digest pinning, signature/provenance admission;
- resource request/limit, PriorityClass e quota;
- topology spread/anti-affinity e disruption budget coerenti con replica;
- encrypted storage, snapshot/backup e restore test;
- GitOps/pipeline con server-side policy e drift detection;
- audit Kubernetes, telemetry e security events esportati su buffer resiliente.

## 5. Collocazione dei componenti

| Componente | Baseline | Vincolo |
|---|---|---|
| API/UI Control Plane | replica multi-zone | stateless; sessione/token non in memoria locale |
| Platform PostgreSQL | HA orchestrata | PITR, fencing, replica DR |
| Runtime worker | pool orizzontale | partition/fairness e graceful drain |
| Local durable spool | storage persistente per cella | nessuna eviction di evento confermato |
| Object storage raw | S3-compatible o equivalente | immutabilità, encryption, lifecycle |
| Event broker | quorum multi-zone | non archivio clinico permanente |
| Vocabulary service | replica/cache per snapshot | nessun latest implicito |
| OMOP database | analytics cell separata | non nel fast path |
| OTel Collector | agent/gateway ridondanti | redaction prima dell'export |
| Audit journal | writer/buffer durabile | append-only/tamper-evident |

## 6. Rete e trust boundary

I flussi ammessi sono dichiarati: ingress clinico→connector; runtime→destinazioni; runtime→storage/broker; runtime→Control Plane per bundle/status; servizi→identity/KMS; componenti→collector. Accesso amministrativo passa da bastion/zero-trust access e identity federata. Database, broker, object store e admin endpoint non sono esposti pubblicamente.

Ogni connessione dichiara protocollo, direzione, DNS, porta, TLS/mTLS, identity, timeout, rate e data class. Proxy/inspection non può alterare payload o rompere pin/trust senza qualification.

## 7. Data residency

Il deployment manifest associa data class a region/site/store e replica ammessa. Metadata centrali escludono payload e patient identifier per default. Backup, telemetry, support bundle e DR copy sono inclusi nella valutazione: non sono eccezioni implicite alla residency.

## 8. Dimensionamento

Input minimi:

- eventi/s medi, p95 e burst per protocollo/facility;
- distribuzione dimensione payload, incluso DICOM/file;
- numero flow/endpoint e concurrency;
- retention raw, queue e audit;
- outage downstream e backlog massimo;
- replay concorrente e recovery window;
- tenant growth e noisy-neighbour factor;
- RTO/RPO, zone/site failure e maintenance reserve.

Reference Runtime Cell: almeno 2.000 eventi/s asincroni da 8 KiB per 60 minuti, p99 ingest ≤250 ms, CPU media ≤70% e zero mismatch. Il dimensionamento reale usa benchmark sul profilo cliente e mantiene N+1 per Tier 0.

## 9. Disponibilità per tier

| Tier | Placement minimo enterprise | Degraded mode |
|---|---|---|
| 0 | multi-zone, N+1, storage/quorum HA | buffer/backpressure senza falso ACK |
| 1 | replica e recovery automatizzata | coda differibile entro SLO |
| 2 | compute scalabile/ripristinabile | sospensione prima di impattare Tier 0 |
| Control Plane | replica multi-zone e DB HA | runtime last-known-good per 24 h |

## 10. Configuration e release

Ogni ambiente è creato da IaC e deployment descriptor versionati. Promotion usa lo stesso digest dev→test→prod; cambiano configuration binding e secret reference. Rollout per Runtime Cell supporta preflight, canary, wave, pause, abort, rollback e revocation. Migration database usa expand/migrate/contract.

Drift, modifica manuale ed emergency change producono alert/audit e vengono riconciliati con source of truth. Nessun agente di coding possiede credenziali di deploy o approvazione release.

## 11. Security baseline

- non-root, read-only root filesystem e capability minime;
- seccomp/AppArmor/SELinux equivalente dove supportato;
- software bill of materials e image scanning;
- firma e admission per artifact;
- key separation tenant/environment e rotation;
- encryption at rest/in transit;
- RBAC least privilege e separation of duties;
- backup credential separata dal primario;
- vulnerability/patch SLA e node drain testato;
- runtime connector di terze parti isolato.

## 12. Observability e audit

Manifest e telemetry includono build, environment, cluster, cell, tenant/facility quando consentito, senza patient ID. Dashboard copre saturation, queue age, throughput, error, delivery, replica lag, backup, certificate expiry e policy drift. Alert è testato end-to-end fino a NOC/SOC. Audit conserva deploy, policy, secret rotation, accesso e failover.

## 13. Upgrade e rollback

Support matrix dichiara Kubernetes, runtime, database, broker, storage, SDK e connector N/N-1. Preflight verifica capacity, PDB, schema, image, signature, secret e dependency. Durante rolling upgrade un nodo/replica può essere indisponibile senza violare Tier 0. Rollback applicativo è automatico su health/invariant gate; rollback dati segue migration plan.

## 14. Casi d'uso

### DT-01 — Gruppo ospedaliero nazionale

Control Plane centrale e Runtime Cell per azienda, con raw e chiavi locali. WAN assente 24 ore: flow già approvati continuano, change amministrativi attendono, audit si bufferizza e reconnect riconcilia.

### DT-02 — Ospedale con due data center

Runtime Tier 0 su zone del sito A e warm standby nel sito B. Perdita sito attiva fencing e promotion entro 30 minuti, RPO cross-site ≤5 minuti, RPO locale 0 per ACK già durable.

### DT-03 — Tenant ad alto isolamento

Un'azienda richiede cluster/database/key domain dedicati. Gli artifact restano identici; fleet, policy e evidence sono governati centralmente senza condividere payload.

### DT-04 — Imaging ad alto volume

DICOM multi-GB usa streaming verso object storage, node pool/storage dedicati e metadata nel runtime. Heap resta bounded e analytics non compete con ingest.

### DT-05 — Pilot MVP

Kubernetes single-site o Compose controllato con backup esterno. Sono già presenti tenant/facility scope, raw, audit, restore e config-as-code; si riducono replica e catalogo protocolli, dichiarando limiti e nessun falso claim HA.

## 15. Gate di accettazione

- nessun single point of failure Tier 0 nel profilo enterprise;
- topology e data flow corrispondono al manifest osservato;
- test cross-tenant e network segmentation superati;
- performance, N+1, rolling upgrade e noisy-neighbour dimostrati;
- backup/restore/failover nei target;
- 24 ore Control Plane offline provate;
- artifact firmati e digest pinnati;
- dashboard, alert, runbook e owner presenti;
- nessun payload fuori residency policy.

## 16. Fonti ufficiali

Verificate il **5 settembre 2026**:

- [Kubernetes — Production environment](https://kubernetes.io/docs/setup/production-environment/);
- [Kubernetes — Multi-tenancy](https://kubernetes.io/docs/concepts/security/multi-tenancy/);
- [Kubernetes — Pod Security Standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/);
- [Kubernetes — Disruptions](https://kubernetes.io/docs/concepts/workloads/pods/disruptions/);
- [Kubernetes — Topology spread constraints](https://kubernetes.io/docs/concepts/scheduling-eviction/topology-spread-constraints/);
- [OpenTelemetry Specification 1.60.0](https://opentelemetry.io/docs/specs/otel/).

## 17. Collegamenti

- [Alta disponibilità e DR](./high-availability-and-dr.md)
- [Scalabilità e resilienza](../architecture/scalability-and-resilience.md)
- [Security architecture](../security/security-architecture.md)
- [Test performance e chaos](../testing/performance-and-chaos.md)
