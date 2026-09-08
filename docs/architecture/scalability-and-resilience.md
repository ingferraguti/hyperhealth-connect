# Scalabilità, resilienza e continuità operativa

Stato: baseline architetturale 1.0  
Data di riferimento: 1 settembre 2026  
Target: produzione enterprise multi-azienda, multi-facility e multi-site

## 1. Obiettivo

L'architettura deve sostenere crescita prevedibile, burst clinici, guasti parziali, perdita di rete, indisponibilità di servizi esterni e disastro di sito senza perdita silenziosa. La resilienza viene progettata per flow e service tier, non attribuita genericamente al cluster.

## 2. Service tier e obiettivi

| Tier | Esempi | SLO mensile HHC | RTO sito | RPO cross-site | RPO locale dopo ACK |
|---|---|---:|---:|---:|---:|
| Tier 0 | ADT, patient identity, risultati critici, ordini urgenti | 99,99% | ≤30 min | ≤5 min | 0 |
| Tier 1 | documenti, prescrizione ordinaria, feed operativi | 99,95% | ≤2 h | ≤15 min | 0 quando `ACK_ON_DURABLE` |
| Tier 2 | OMOP, analytics, batch, catalogo | 99,9% | ≤8 h | ≤1 h | secondo job checkpoint |
| Control Plane | governance e deployment | 99,9% | ≤4 h | ≤15 min | non applicabile al data path |

Gli SLO sono misurati per flow e facility. Le dipendenze esterne sono escluse soltanto dal calcolo HHC, ma devono essere mostrate separatamente come causa; l'esperienza end-to-end non può essere mascherata.

## 3. Modello di capacità

La capacità è pianificata sulle seguenti dimensioni:

- eventi/s medi e picco a 1, 5 e 15 minuti;
- dimensione p50/p95/p99 e payload massimo;
- connessioni concorrenti e connection churn;
- costo di parse, validation, mapping e cifratura;
- numero di destinazioni per evento;
- latency e rate limit dei downstream;
- retention e replay window;
- ordering key e numero di partizioni attive;
- query e indicizzazione Message Explorer;
- volume canonical/OMOP e workload batch;
- failure headroom e recovery catch-up rate.

Formula di base per ogni stage:

```text
required_capacity = peak_input_rate × fan_out × cost_factor × safety_factor
worker_count = ceil(required_capacity / measured_worker_capacity)
```

Il `safety_factor` minimo production è 1,5; per Tier 0 e failover N+1 deve permettere di perdere un nodo/zona mantenendo lo SLO. I valori derivano da benchmark sulla stessa classe di payload, non da stime nominali del broker.

## 4. Target prestazionali di riferimento

- Fast path, payload HL7 v2 ≤64 KiB e mapping in-memory: overhead HHC p95 ≤50 ms, p99 ≤100 ms, esclusi rete e sistema destinatario.
- Runtime Cell di riferimento: almeno 2.000 eventi/s asincroni da 8 KiB per 60 minuti, CPU media ≤70%, p99 ingest ≤250 ms e zero mismatch di riconciliazione.
- Control Plane: almeno 100 organization, 1.000 facility, 10.000 endpoint e 20.000 flow per tenant di riferimento; query indicizzate p95 ≤500 ms.
- Payload grandi: streaming end-to-end e memoria bounded; nessun buffering completo in heap.
- Headroom: dopo la perdita di una replica/worker pool, CPU sostenuta ≤75% e queue age entro il budget del tier.

Questi target sono quality gate del prodotto, non promesse universali. Ogni installazione riceve capacity plan e benchmark con hardware, rete, storage, dipendenze e profili reali.

## 5. Strategia di scaling

### 5.1 Gateway

Scala per connection count, handshake rate, bandwidth e protocol-specific load. Port/protocol binding e session affinity sono gestiti esplicitamente. MLLP e DICOM possono richiedere draining delle connessioni prima del rollout.

### 5.2 Flow Worker

Scala per queue age, lag, throughput e CPU. L'autoscaling non supera limiti del downstream. Pool separati per Tier 0, Tier 1 e batch prevengono starvation. Flow ad alto costo hanno concurrency budget dedicato.

### 5.3 Broker

Il numero di partizioni considera parallelismo richiesto, ordering e tempo di recovery. Troppe partizioni aumentano overhead e rebalance; troppo poche limitano il throughput. Replica factor production minimo 3 quando la piattaforma scelta e le zone lo consentono; `min.insync.replicas` impedisce ACK senza durabilità sufficiente.

### 5.4 Storage

Object storage scala indipendentemente dal metadata DB. Indici sono partizionati per tempo e scope; i dati scaduti passano attraverso lifecycle. Il Platform DB usa indici selettivi, connection pool bounded, replica read e partitioning soltanto quando giustificato dai piani di query.

### 5.5 Semantic e analytics

Semantic worker scala per dominio/partition e vocabulary cache. OMOP/analytics usa queue e compute pool separati con quote per tenant. Query lunghe hanno timeout, cost guard, work schema e resource group.

## 6. Isolamento e noisy-neighbour

Livelli di isolamento, dal più leggero al più forte:

1. quote e rate limit per tenant/facility/flow;
2. queue, consumer group e worker pool dedicati;
3. namespace e node pool dedicati;
4. broker/database/bucket e chiavi dedicati;
5. cluster/account/sito dedicato.

Tier 0 non condivide pool con analytics. Un tenant non può consumare capacity riservata a un altro. L'autoscaler rispetta budget economici ma non riduce repliche sotto il minimo HA.

## 7. Failure domain

```text
Process → Pod/VM → Node → Rack/Zone → Site/Region → Provider/Identity Domain
```

Ogni replica critica è collocata su failure domain differenti tramite anti-affinity/topology spread. Una topologia non è multi-zone se database, broker o storage restano single-zone. DR non è valido se primario e secondario condividono identità amministrativa compromettibile, rete, KMS o backup account.

## 8. Resilience pattern

### 8.1 Timeout e deadline

Deadline end-to-end è propagato. Ogni timeout downstream è inferiore al budget residuo. Timeout infiniti sono vietati.

### 8.2 Retry

Retry usa exponential backoff, jitter, max attempt e max age. Errori non retryable non vengono ripetuti. I retry non devono moltiplicare il carico durante outage; il retry budget è condiviso per destinazione.

### 8.3 Circuit breaker

Si apre su failure rate/latency e protegge destinazione e worker. Half-open usa probe limitati. Stato e transizioni sono metriche e audit quando forzate manualmente.

### 8.4 Bulkhead

Thread pool, connection pool, queue e resource quota separano protocollo, service tier e destinazione fragile. La saturazione di un LIS non blocca ADT verso EHR.

### 8.5 Back-pressure

Il sistema rallenta ingress, limita in-flight, usa spool o restituisce errore di protocollo. Non accetta indefinitamente messaggi che non può rendere durabili. Soglie soft/hard anticipano l'esaurimento.

### 8.6 Load shedding

Consentito solo per traffico dichiarato sacrificabile. Tier 0 non viene scartato; può essere rifiutato prima dell'ACK se non può essere durabilmente accettato. Telemetry ad alta frequenza può essere campionata, ma audit no.

### 8.7 Idempotenza e riconciliazione

At-least-once più idempotency key e delivery ledger è il modello standard. Stato `UNKNOWN` richiede query o business reconciliation prima di inviare di nuovo quando il side effect potrebbe essere avvenuto.

### 8.8 Graceful degradation

- Control Plane down: worker continuano, change bloccati.
- Vocabulary down: mapping usa snapshot/cache autorizzata o quarantena; non inventa concept.
- Analytics down: backlog cresce, percorso clinico invariato.
- SIEM down: audit bufferizza, security alert locale.
- Search down: processing continua; Message Explorer degradato.
- Raw central down: spool locale HA secondo capacity; poi back-pressure.

## 9. Alta disponibilità locale

### 9.1 Data Plane

- almeno due gateway e worker replica per cella Tier 0;
- distribution su almeno due zone; tre dove richiesto dal quorum;
- PodDisruptionBudget e capacity N+1;
- rolling drain che completa o restituisce in queue gli eventi in-flight;
- durable spool e broker replicati;
- load balancer health-aware con connection draining;
- nessuna dipendenza sincrona dal Control Plane.

### 9.2 Control Plane

- repliche stateless multi-zone;
- DB HA con failover testato;
- bundle store e audit buffer ridondati;
- leader election per scheduler;
- API idempotenti e optimistic concurrency;
- session/token indipendenti dalla singola replica.

### 9.3 Stateful services

Quorum, replication e write acknowledgement sono configurati in modo coerente con l'RPO. Un cluster a tre nodi sulla stessa VM o storage non costituisce HA. Il failover automatico è ammesso solo se fencing e split-brain prevention sono provati.

## 10. Business continuity

Business continuity copre persone, procedure e dipendenze oltre al software.

### 10.1 Modalità operative

| Modalità | Condizione | Comportamento |
|---|---|---|
| Normal | tutte le dipendenze entro SLO | elaborazione standard |
| Degraded | dipendenza lenta/down, capacity disponibile | store-and-forward, circuit breaker, alert |
| Isolated Facility | WAN/control plane non raggiungibili | autonomia locale con bundle e spool |
| Disaster | sito/identity domain compromesso | promozione DR e fencing primario |
| Recovery | servizio ripristinato | catch-up controllato e riconciliazione |

### 10.2 Autonomia della facility

Ogni cella critica conserva:

- ultima configurazione valida firmata;
- trust anchors e segreti con validità sufficiente o meccanismo locale di renewal;
- spool dimensionato sulla durata di isolamento concordata;
- dashboard e alert locali;
- runbook e contatti offline;
- capacità di esportare evidence e conteggi senza Control Plane.

La baseline richiede almeno 24 ore di autonomia; il capacity plan può elevare a 72 ore o oltre.

### 10.3 Procedure manuali

Se HHC e il DR sono indisponibili, i processi clinici devono avere procedure aziendali alternative. HHC documenta impatto, sistemi coinvolti, ultimo evento processato e modalità di riconciliazione al rientro, ma non sostituisce i downtime procedure dell'ospedale.

## 11. Disaster recovery

### 11.1 Strategie per componente

| Componente | Strategia primaria | Alternativa |
|---|---|---|
| Runtime Tier 0 | warm/hot standby cell cross-site | redeploy + restore bundle/spool |
| Broker | replica/cluster secondario con checkpoint | restore e replay da raw |
| Raw store | versioning + cross-site replication | immutable backup restore |
| Platform DB | replica async + PITR | restore full + WAL/log |
| Audit | immutable replica separata | restore + chain verification |
| Search | rebuild da metadata/eventi | backup snapshot |
| OMOP | replica/backup per dataset critico | deterministic rebuild da canonical/raw |
| Vocabulary | replica di snapshot immutabili | restore package verificato |

### 11.2 Dichiarazione del disaster

Richiede incident commander autorizzato, evidenza del fault, scelta del recovery point e fencing. In un cyber incident la replica può essere contaminata: prima della promozione sono richiesti compromise assessment, clean credentials e verifica dell'integrità.

### 11.3 Failover

1. congelare change e preservare audit;
2. isolare/fence il primario;
3. validare identity, PKI, secrets e time service secondari;
4. promuovere datastore nell'ordine delle dipendenze;
5. attivare ingress con TTL/routing predisposti;
6. verificare synthetic transaction Tier 0;
7. riaprire traffico gradualmente;
8. riconciliare checkpoint, queue e delivery ledger;
9. comunicare stato e gap residuo.

### 11.4 Failback

Non è un semplice switch DNS. Richiede ricostruzione o bonifica del primario, reverse replication, consistency check, finestra approvata, drain, fencing del secondario e riconciliazione. Ogni passaggio è reversibile fino al commit point dichiarato.

### 11.5 Backup anti-ransomware

- regola 3-2-1-1-0 adattata: più copie, media/failure domain distinti, una copia offline/immutabile e zero errori nei restore test;
- credenziali e account separati dal dominio primario;
- object lock/WORM per backup e audit;
- cifratura con recovery key protetta;
- scanning e restore in clean room;
- restore test automatici e drill semestrali end-to-end.

## 12. Monitoraggio continuo e SLO

### 12.1 Golden signals

- rate/throughput;
- error/success rate per categoria;
- latency p50/p95/p99;
- saturation CPU, memory, thread, pool, disk e network;
- queue age/depth e consumer lag;
- freshness dell'ultima consegna valida;
- availability endpoint e dependency;
- reconciliation gap;
- audit/telemetry export lag.

### 12.2 Alerting

Alert su burn rate multi-window riduce falsi positivi. Per Tier 0:

- fast burn: rischio di consumare error budget in poche ore;
- slow burn: degradazione persistente;
- queue age vicino al clinical freshness budget;
- assenza di eventi attesi, non solo errori espliciti;
- failover di replica, perdita quorum o spazio insufficiente;
- mapping/validation regression e spike di quarantena;
- clock skew e certificate expiry.

Ogni alert ha owner, severity, impact scope, runbook e escalation. Alert route e paging sono testati trimestralmente.

### 12.3 Synthetic monitoring

Messaggi sintetici privi di PHI attraversano ingress, mapping e destinazioni di test controllate. Verificano non solo liveness ma anche correttezza della trasformazione. Non devono contaminare sistemi clinici o dataset; sono chiaramente marcati e filtrati.

## 13. Test di resilienza

Prima della release enterprise e poi periodicamente:

- kill/restart worker durante ogni durability point;
- perdita di nodo e zona;
- broker leader failover e replica lag;
- DB failover durante deploy e ingest;
- raw store lento/non disponibile;
- WAN partition Control Plane–facility di 24 ore;
- downstream lento, error storm e connection reset;
- certificate/secret expiry e revoca;
- SIEM/telemetry outage;
- saturazione disk/spool e backlog catch-up;
- DR failover/failback completo;
- scenario ransomware e restore clean-room;
- clock skew e DNS failure.

Ogni test verifica eventi inviati, accettati, consegnati, duplicati, quarantinati e persi tramite checksum e ledger. “Il servizio è tornato su” non è criterio sufficiente.

## 14. Capacity e recovery governance

- capacity review trimestrale e prima di onboarding rilevante;
- forecast a 12 mesi con p95/p99 e stagionalità;
- alert a 60/75/85% di capacity utile;
- reserve capacity non allocata per failover;
- RTO/RPO owner e dipendenze registrati in CMDB/catalogo;
- Business Impact Analysis annuale per flow Tier 0/1;
- DR drill semestrale e tabletop cyber almeno annuale;
- remediation con owner e scadenza; eccezioni approvate e time-bound.

## 15. Anti-pattern vietati

- singolo broker/database condiviso senza failure isolation;
- ACK prima della durabilità per Tier 0;
- retry infinito o sincronizzato senza jitter;
- autoscaling soltanto su CPU ignorando queue age e downstream;
- analytics sul database operativo;
- backup mai ripristinati;
- DR nello stesso identity/failure domain;
- active-active con scritture concorrenti senza ownership/fencing;
- log del payload per “debug” permanente;
- dichiarare 99,99% senza definire misurazione e dipendenze.

## 17. Fonti ufficiali

Consultate o riconfermate il 5 settembre 2026:

- [Kubernetes production environment](https://kubernetes.io/docs/setup/production-environment/)
- [Kubernetes disruptions](https://kubernetes.io/docs/concepts/workloads/pods/disruptions/)
- [PostgreSQL 18 warm standby](https://www.postgresql.org/docs/current/warm-standby.html)
- [NIST SP 800-184 Guide for Cybersecurity Event Recovery](https://csrc.nist.gov/pubs/sp/800/184/final)
- [OpenTelemetry specification](https://opentelemetry.io/docs/specs/otel/)
