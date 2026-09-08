# Performance, resilienza e chaos engineering

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ambito | Prodotto finale HHC, installazioni multiazienda e multifacility |
| Ultimo aggiornamento | 4 settembre 2026 |
| Responsabili | SRE, Performance Engineering, Architecture, Security, QA, Clinical Safety |
| Revisione minima | Trimestrale e a ogni modifica sostanziale di architettura, capacità o SLO |

## 1. Finalità

Questo documento rende verificabili le proprietà non funzionali di HHC: latenza, throughput, capacità, scalabilità, isolamento, disponibilità, durabilità, recuperabilità, business continuity e disaster recovery. Le prove devono dimostrare che il sistema mantiene gli invarianti clinici e di sicurezza anche sotto saturazione, guasto parziale, perdita di dipendenze o disastro di sito.

“Veloce” non è un criterio di accettazione. Ogni risultato deve riferirsi a una release, un ambiente, un workload, una distribuzione dei payload, una topologia, un insieme di SLO e un pacchetto di evidenze riproducibile.

Le prescrizioni della [strategia di test](./test-strategy.md), della [qualificazione di conformità](./conformance-and-interoperability.md), della [politica agentic coding](./agentic-coding-quality-policy.md) e della [toolchain](./test-toolchain-and-environments.md) si applicano integralmente.

## 2. Obiettivi di sistema vincolanti

### 2.1 Service level objective

| Classe | Esempi | Availability SLO mensile |
|---|---|---:|
| Tier 0 | ingestione clinica, durabilità, routing critico, query necessarie al flusso clinico | 99,99% |
| Tier 1 | servizi operativi importanti non nel percorso immediato di ACK | 99,95% |
| Tier 2 | analytics, OMOP, elaborazioni differibili | 99,90% |
| Control Plane | configurazione, governance, distribuzione policy | 99,90% |

La classificazione del singolo servizio è registrata nel service catalog. Nessun team può declassificare un servizio per superare un gate.

### 2.2 Recovery objective

| Classe | RTO massimo | RPO massimo |
|---|---:|---:|
| Tier 0 | 30 minuti | 5 minuti tra siti; RPO 0 locale dopo ACK durevole |
| Tier 1 | 2 ore | 15 minuti |
| Tier 2 | 8 ore | 1 ora |
| Control Plane | 4 ore | 15 minuti |

I Runtime Cell devono continuare il traffico già autorizzato per almeno **24 ore** durante l'indisponibilità del Control Plane, usando configurazioni firmate, non scadute oltre i limiti ammessi e localmente disponibili.

### 2.3 Baseline prestazionale di riferimento

Per payload HL7 v2 fino a **64 KiB**, l'overhead introdotto da HHC sul percorso qualificato deve essere:

- p95 ≤ 50 ms;
- p99 ≤ 100 ms.

Una Reference Runtime Cell deve sostenere per **60 minuti** almeno **2.000 eventi asincroni al secondo**, payload medio **8 KiB**, con:

- CPU media ≤ 70% durante la finestra stabile;
- p99 di ingestione ≤ 250 ms;
- zero mismatch di integrità o tenant;
- nessuna perdita silenziosa;
- backlog riconciliato entro il limite dichiarato dal test.

Questa è una baseline di prodotto, non una garanzia universale per qualsiasi hardware, mapping, partner o payload. Ogni sizing di produzione deve essere qualificato sul workload del cliente con margine di sicurezza.

## 3. Invarianti sotto carico e guasto

Le prestazioni non possono essere ottenute violando questi invarianti:

1. nessun dato attraversa il confine di tenant, organization o facility senza autorizzazione esplicita;
2. un ACK positivo è emesso soltanto dopo il punto di durabilità contrattuale;
3. il raw accettato resta immutabile e verificabile tramite digest;
4. ogni duplicato, retry o replay è idempotente o riconciliabile;
5. gli eventi non sono scartati silenziosamente in caso di backpressure;
6. audit, correlation ID, lineage e security event restano completi;
7. configurazioni non valide o non firmate non diventano attive;
8. una proiezione OMOP o un servizio Tier 2 non rallenta il percorso clinico di Tier 0;
9. il guasto di un connettore o di un Runtime Cell non si propaga a domini non correlati;
10. failover e failback impediscono split brain e doppia elaborazione non riconciliabile.

## 4. Performance Test Manifest

Ogni esecuzione usa un manifest firmato e conservato con i risultati:

```yaml
test_id: perf-ingest-reference-cell-001
product_release: 1.0.0
commit: ...
artifact_digests: []
environment:
  topology: active-active-two-zone
  compute: {}
  storage: {}
  network: {}
  kubernetes_version: pinned
  database_version: pinned
workload:
  model: hl7v2-async-mixed
  seed: 20260901
  tenants: 20
  organizations_per_tenant: 2
  facilities_per_organization: 4
  events_per_second: 2000
  duration_minutes: 60
  payload_distribution: {}
  hot_key_distribution: {}
faults: []
acceptance_criteria: []
observability_snapshot: evidence://...
started_at: 2026-09-01T00:00:00Z
```

Il manifest deve consentire a un team indipendente di riprodurre la prova. Sono inclusi configurazione del generatore, dataset sintetico, seed, warm-up, durata, clock source, versione delle dashboard e query usate per calcolare i percentile.

## 5. Workload model

### 5.1 Dimensioni obbligatorie

Un workload rappresentativo combina:

- numero di tenant, organization, facility, application ed endpoint;
- distribuzione uniforme e hot tenant/hot facility;
- protocolli HL7 v2, FHIR, IHE/CDA, DICOM/DICOMweb e API/eventi;
- payload piccoli, mediani, grandi e limite;
- mapping semplici, terminologia remota/cache, trasformazioni complesse;
- fan-out, fan-in, duplicati, replay e late event;
- letture/scritture, sequenziali/casuali e rapporti di concorrenza;
- pattern diurno, burst, cambio turno, indisponibilità partner e recupero backlog;
- utenti interattivi, workload macchina e job analitici concorrenti;
- retention, cardinalità della telemetria e dataset maturo, non solo database vuoto.

Le distribuzioni devono derivare da telemetria anonimizzata e aggregata quando disponibile; in assenza, sono ipotesi documentate e aggiornate dopo il pilot.

### 5.2 Portafoglio minimo

| Workload | Scopo | Caratteristiche |
|---|---|---|
| W1 nominale | comportamento ordinario | 40–60% della capacità qualificata, mix realistico |
| W2 picco | cambio turno/emergenza | burst 2–3×, durata e ramp realistici |
| W3 hot tenant | fairness e isolamento | un tenant genera ≥50% del traffico |
| W4 partner lento | backpressure | downstream con latenza, rate limit e timeout |
| W5 replay | recupero | backlog storico con traffico live concorrente |
| W6 mapping complesso | CPU/terminologia | lookup, regole e versioni miste |
| W7 imaging | I/O e streaming | oggetti multi-frame e multi-gigabyte entro limiti qualificati |
| W8 FHIR query | protezione risorse | ricerca comune, paginata, costosa e rifiutata |
| W9 Control Plane | scala gestionale | distribuzione config a molte Runtime Cell e audit |
| W10 OMOP | isolamento Tier 2 | ETL incrementale/full e query analitiche concorrenti |

## 6. Tipologie di prova

### 6.1 Microbenchmark

Misura parser, serializer, hashing, compressione, mapping, regole e componenti crittografici isolati. Serve a individuare regressioni locali, non a dichiarare la capacità del sistema.

### 6.2 Component load test

Verifica code, cache, database, object storage, terminology service, identity service e singoli connettori entro contratti espliciti. Comprende limiti di connessioni, pool, thread, file descriptor e memoria.

### 6.3 End-to-end load test

Misura il percorso dal client sintetico all'esito osservabile dal destinatario, includendo rete, persistenza, broker, mapping, routing e audit. Distingue latenza client, overhead HHC e latenza partner.

### 6.4 Step e stress test

Incrementa il carico per determinare:

- punto di saturazione;
- primo collo di bottiglia;
- forma del degrado;
- efficacia di rate limit e backpressure;
- capacità di recupero dopo la rimozione del carico.

Il test si interrompe prima di compromettere servizi condivisi esterni. Il risultato non è “massimo EPS”, ma una curva capacità-latenza-errori-saturazione.

### 6.5 Spike test

Applica un incremento rapido coerente con eventi reali. Verifica assorbimento tramite buffer, autoscaling, fairness, protezione dei downstream e tempo di ritorno allo steady state.

### 6.6 Soak test

Durata minima 24 ore per componenti stateful o a rischio leak; 72 ore per una major release o una variazione sostanziale del runtime. Verifica crescita di heap, handle, connessioni, log, indici, compaction, lag, storage e precisione dei timer.

### 6.7 Capacity e scalability test

Misura scaling verticale e orizzontale, efficienza aggiungendo repliche e limite della dipendenza più lenta. La prova include N, N+1 e perdita dell'unità di failure più grande. Una capacità è qualificata solo se mantiene il margine richiesto dopo la perdita prevista.

### 6.8 Failover e recovery test

Inietta guasti mentre il traffico è attivo e misura detection, fencing, election/failover, ripresa, riconciliazione e ritorno allo steady state.

### 6.9 Disaster recovery exercise

Esegue perdita di sito, ripristino isolato, verifica dei dati, riapertura controllata e failback. Un restore di backup riuscito senza riattivazione applicativa e riconciliazione non dimostra il RTO.

## 7. Metodo di misura

### 7.1 Latenza

Per ogni fase si raccolgono histogram ad alta risoluzione e almeno p50, p90, p95, p99, p99.9 e massimo. Non si usano solo medie. Gli intervalli comprendono:

- invio client → ricezione edge;
- ricezione → durabilità/ACK;
- durabilità → trasformazione;
- trasformazione → consegna downstream;
- consegna → conferma partner;
- end-to-end business event → stato clinicamente visibile.

Il generatore usa arrivi a tasso controllato o un modello esplicitamente dichiarato. Deve evitare o correggere la **coordinated omission**, mantenere un clock affidabile e non diventare esso stesso il collo di bottiglia.

### 7.2 Throughput e correttezza

Si registrano offered, accepted, durably stored, processed, delivered, acknowledged, quarantined, retried e reconciled rate. I conteggi devono chiudere:

`accepted = delivered + pending + quarantined + terminally_failed`

Le categorie sono mutuamente esclusive nello snapshot e ogni differenza è spiegata. “Zero errori” non è valido se il generatore non verifica payload, digest e destinazione.

### 7.3 Saturazione

Sono obbligatori CPU throttling, memoria/working set, GC, thread/event loop, queue depth/age, broker lag, disk latency/IOPS/space, database connection/lock/replication lag, network retransmit, object storage latency, cache hit ratio e rate dei dependency error.

### 7.4 Ripetibilità e significatività

- warm-up separato dalla finestra di misura;
- almeno tre ripetizioni per benchmark di release, salvo prove lunghe motivate;
- stessa topologia o normalizzazione esplicita;
- intervalli di confidenza o variabilità riportati;
- confronto con baseline della stessa classe hardware;
- esclusione documentata di run contaminati, mai selezione opportunistica del migliore;
- archiviazione dei dati grezzi oltre al report.

## 8. Capacity planning e margine

La capacità sicura di una cella è il minimo tra vincoli di compute, storage, broker, database, rete e downstream, misurato dopo la perdita dell'unità prevista. Il dimensionamento usa almeno:

`capacità richiesta = picco previsto × crescita × replay simultaneo × fattore di sicurezza`

Il fattore di sicurezza predefinito è **1,5**, da aumentare per workload incerto o facility critica. Non può essere ridotto senza evidenza, approvazione SRE e rischio residuo registrato.

La produzione deve poter sostenere:

- picco qualificato con un nodo/replica/availability zone indisponibile secondo topologia;
- traffico live più recupero backlog con quota e priorità separate;
- manutenzione e rolling upgrade senza violare lo SLO;
- crescita fino alla successiva finestra realistica di procurement/scaling.

Il capacity review è mensile per Tier 0 e trimestrale per gli altri tier; un alert previsionale scatta prima che il consumo proiettato raggiunga il margine di sicurezza.

## 9. Gate di regressione prestazionale

Una release fallisce il gate quando, a parità di classe ambientale:

- viola una soglia assoluta di SLO o baseline;
- aumenta p95 o p99 oltre il 10% senza approvazione e budget esplicito;
- riduce throughput sostenibile oltre il 10%;
- aumenta CPU, memoria, I/O o costo unitario oltre il 15%;
- introduce leak, crescita non limitata o coda non recuperabile;
- riduce l'isolamento sotto hot tenant o partner lento;
- compromette audit, integrità o riconciliazione.

Le soglie relative servono da detector e possono essere adattate alla variabilità misurata; le soglie assolute e gli invarianti non sono derogabili automaticamente.

### 9.1 Ottimizzazioni e benchmark prodotti da agenti

Una modifica agentica che dichiara un miglioramento prestazionale è A3 quando tocca tenancy, cache, concurrency, persistence, ACK, retry, audit, mapping o recovery. Prima della patch si registra un Performance Test Manifest con workload, distribuzione, topologia, warm-up, numero di run, soglie e business invariant.

Il gate impedisce benchmark gaming:

- scenario e dataset blind/indipendenti completano quelli usati dall'agente;
- implementazione e benchmark non cambiano nello stesso diff senza review Performance/Oracle Owner;
- cache key include tenant, facility, versione e scope semantico richiesti;
- si misurano p95/p99, throughput, errori, saturazione, costo ed integrità, non solo media;
- i risultati scartati, gli outlier e i retry restano visibili;
- un miglioramento non compensa perdita di audit, lineage, durabilità, fairness o reconciliation;
- raw series, toolchain BOM, generator utilization e commit sono conservati;
- una soglia viene modificata soltanto con change record, non per rendere verde la release.

L'agente può analizzare profili e suggerire ipotesi, ma SRE/Performance Engineering approvano il modello di carico e ripetono la prova qualificante su runner e ambiente controllati.

## 10. Chaos engineering

### 10.1 Metodo

Ogni esperimento segue i principi di steady state, ipotesi verificabile, eventi realistici, automazione e blast radius minimo. In sanità la sicurezza clinica prevale sull'obiettivo di sperimentare in produzione.

Un esperimento contiene:

```yaml
experiment_id: chaos-broker-leader-loss-001
owner: sre-team
system_tier: tier-0
steady_state:
  - ingest_success_rate >= 99.99%
  - no_cross_tenant_events == true
hypothesis: Il traffico continua senza perdita e il backlog rientra entro 10 minuti.
fault: Perdita del broker leader nella zona A.
blast_radius:
  environment: preproduction
  tenants: [synthetic-a]
  duration_seconds: 120
abort_conditions:
  - integrity_mismatch > 0
  - cross_tenant_event > 0
  - clinical_ack_false_positive > 0
rollback: Ripristino leader e blocco automatico dell'iniezione.
evidence: []
approvals: []
```

### 10.2 Prerequisiti

Prima dell'iniezione devono essere veri:

- stato del sistema sano e baseline misurata;
- test data sintetici e tenant dedicati;
- backup/restore verificato quando il fault può toccare dati;
- osservabilità e alert funzionanti;
- owner, incident commander e on-call reperibili;
- abort automatico e rollback provato;
- change window, comunicazione e approvazione proporzionate;
- esclusione esplicita di dispositivi o workflow clinici non isolabili;
- nessun incidente attivo o error budget critico già esaurito.

### 10.3 Ambienti e guardrail

L'ordine ordinario è laboratorio → integrazione → preproduzione → produzione controllata. In produzione sono ammessi soltanto esperimenti:

- con traffico sintetico o componenti realmente isolati;
- con blast radius minimo e limite temporale;
- che non possano ritardare diagnosi, terapia o allerta clinica;
- approvati da SRE, service owner, security e clinical safety quando applicabile;
- arrestabili automaticamente al primo segnale di violazione.

La produzione non è usata per distruggere deliberatamente dati clinici, invalidare backup, compromettere chiavi reali o simulare ransomware su repository attivi. Questi scenari si esercitano in copie isolate e rappresentative.

## 11. Catalogo dei fault

### 11.1 Compute e orchestrazione

- crash e kill di processo/pod;
- perdita di nodo e di availability zone;
- CPU starvation, memory pressure, OOM e throttling;
- file descriptor/thread exhaustion;
- scheduling impossibile e image pull failure;
- autoscaler lento, bloccato o oscillante;
- rolling update interrotto e versione mista;
- liveness errata, readiness tardiva e startup lento.

**Atteso:** esclusione rapida delle istanze non ready, nessun routing prematuro, replica sana disponibile, backlog contenuto, nessuna perdita/integrity mismatch.

### 11.2 Broker e code

- perdita leader o replica;
- perdita quorum controllata;
- partition piena, retention errata e compaction;
- consumer lento, poison message e rebalance storm;
- duplicazione, riordino e redelivery;
- dead-letter store indisponibile.

**Atteso:** ACK coerente con durabilità, backpressure, deduplica, quarantena, monitoraggio dell'età della coda e riconciliazione completa.

### 11.3 Database

- failover primario, replica in ritardo e replica non raggiungibile;
- connection pool exhaustion;
- lock prolungato, query lenta e indice mancante;
- disco pieno, I/O lento e corruzione simulata su copia;
- backup incompleto e restore a point-in-time;
- schema migration fallita o parzialmente applicata.

**Atteso:** fencing, nessuna doppia scrittura divergente, errore esplicito, rollback/migrazione riprendibile, integrità e RPO verificati.

### 11.4 Object storage

- latenza, 5xx, timeout e indisponibilità regionale;
- quota/disk full;
- autorizzazione revocata o credenziale scaduta;
- object checksum mismatch e versione mancante;
- lifecycle/retention policy errata in ambiente isolato.

**Atteso:** raw non perso, digest verificato, nessun ACK falso, replica o recovery path utilizzabile, allarme di capacità anticipato.

### 11.5 Rete e service discovery

- latenza, jitter, packet loss, bandwidth cap e connessione resettata;
- partizione asimmetrica e blackhole;
- DNS lento, record errato e cache scaduta;
- MTU mismatch;
- proxy/load balancer indisponibile;
- TLS handshake failure, certificato prossimo alla scadenza/scaduto e trust rotation.

**Atteso:** timeout finiti, retry con jitter e budget, circuit breaker, nessuna retry storm, errore osservabile e isolamento del partner.

### 11.6 Control Plane e configurazione

- Control Plane indisponibile per 24 ore;
- perdita di connettività da una o più Runtime Cell;
- configurazione corrotta, non firmata, incompatibile o revocata;
- rollout parziale e rollback;
- registry di artefatti indisponibile.

**Atteso:** traffico già autorizzato continua localmente, ultima configurazione valida resta attiva, nuove modifiche non verificate sono rifiutate, stato e drift sono auditabili.

### 11.7 Identità, secret e crittografia

- identity provider indisponibile;
- JWKS non raggiungibile e key rotation;
- Vault/KMS/HSM lento o indisponibile;
- secret scaduto, revocato o con permessi insufficienti;
- clock skew che altera token e certificati.

**Atteso:** cache e grace period soltanto entro policy, nessun bypass, sessioni privilegiate limitate, break-glass auditato, traffico machine-to-machine esistente gestito secondo rischio.

### 11.8 Dipendenze cliniche

- PACS, LIS, EHR o endpoint nazionale indisponibile;
- risposta lenta, malformata o semanticamente incoerente;
- rate limit e finestra di manutenzione;
- ACK perso o esito ambiguo;
- partner che ritorna online con capacità inferiore al backlog.

**Atteso:** isolamento per endpoint, store-and-forward ove consentito, priorità al traffico live, replay controllato, riconciliazione e allarme comprensibile agli operatori.

### 11.9 Osservabilità e audit

- collector, metrics backend, log store o SIEM indisponibile;
- cardinalità esplosiva;
- trace sampling configurato male;
- coda audit offline per 24 ore;
- timestamp incoerente.

**Atteso:** buffering protetto e limitato, nessun blocco improprio del percorso clinico salvo evento che richiede fail closed, perdita esplicita e allarmata, audit locale tamper-evident e recupero ordinato.

### 11.10 Supply chain e release

- immagine non firmata o digest inatteso;
- dipendenza vulnerabile bloccata dalla policy;
- canary con regressione;
- feature flag/config rollout errato;
- rollback a schema o mapping precedente.

**Atteso:** admission negata, canary arrestato, rollback compatibile, nessuna commistione semantica silenziosa e provenienza completa.

## 12. Kubernetes: verifiche specifiche

I PodDisruptionBudget limitano le disruption volontarie, ma non proteggono da ogni guasto e non sostituiscono replica, distribuzione e recovery. La suite deve verificare:

- replica effettiva e quorum, non solo replica dichiarata;
- topology spread su host e zone con vincoli coerenti;
- anti-affinity per componenti che condividono il failure domain;
- PDB durante drain e rolling update;
- perdita involontaria di nodo/zona, non coperta dal solo PDB;
- readiness che rimuove il traffico prima dello shutdown;
- startup probe per avvii lenti senza restart loop;
- liveness che non amplifica overload o dipendenze esterne guaste;
- termination grace period e drain delle richieste/code;
- resource request/limit, throttling e QoS;
- autoscaling con metriche affidabili e stabilization window.

La cancellazione diretta di workload o errori di configurazione possono aggirare le aspettative del PDB: questi scenari sono testati separatamente.

## 13. Business continuity e disaster recovery

### 13.1 Scenari minimi

- perdita completa della zona primaria;
- perdita dell'intero sito/region;
- corruzione logica scoperta dopo la replica;
- compromissione ransomware simulata su copia isolata;
- indisponibilità simultanea di Control Plane e link WAN;
- perdita KMS/HSM o delle credenziali di recovery;
- restore su infrastruttura pulita;
- failback al sito originario dopo stabilizzazione.

### 13.2 Procedura di esercitazione

1. Congelare manifest, inventario, backup set e recovery point atteso.
2. Dichiarare incidente simulato e avviare il cronometro RTO.
3. Fencing del writer non affidabile e verifica anti-split-brain.
4. Attivare sito secondario o ricostruire l'ambiente pulito.
5. Ripristinare secret, configurazione firmata, dati e servizi per dipendenza.
6. Verificare autenticità, checksum, schema, conteggi e replication position.
7. Riattivare traffico sintetico, poi canary e progressivamente il carico.
8. Riconciliare accepted/delivered/pending/quarantined e misurare RPO reale.
9. Eseguire test clinici end-to-end e sicurezza.
10. Dichiarare recovery soltanto quando il servizio è usabile e osservabile.
11. Pianificare failback, ripeterne fencing e riconciliazione.
12. Registrare gap, owner, scadenza e nuova prova.

### 13.3 Criteri di successo

- RTO/RPO di classe rispettati con misure temporali verificabili;
- nessun evento cross-tenant, perso silenziosamente o duplicato in modo incoerente;
- point-in-time e backup immutabile effettivamente recuperabili;
- chiavi, certificati e identità disponibili senza bypass permanenti;
- audit chain integra prima, durante e dopo il disastro;
- runbook eseguibile da personale diverso dall'autore;
- failback completato o formalmente rinviato con architettura stabile.

Un backup non restaurato non è considerato un backup qualificato. Una replica sincrona non sostituisce copie isolate contro corruzione logica o compromissione.

## 14. Osservabilità della prova

Ogni esecuzione deve poter essere ricostruita tramite:

- metriche RED/USE e SLI di business;
- log strutturati con test ID e correlation ID;
- trace distribuite campionate in modo controllato;
- eventi Kubernetes/cloud/infrastruttura;
- audit delle azioni umane e automatiche;
- timeline unica, clock synchronization e annotazione del fault;
- snapshot di config, topology, feature flag e deploy;
- dati grezzi dei generatori e verifier indipendenti.

OpenTelemetry è il formato di telemetria preferito, ma la stabilità è valutata per segnale e semantic convention specifica. La versione del protocollo/specification e delle convention è pinnata; non si assume che ogni convention abbia lo stesso livello di stabilità.

Sono vietati patient identifier, payload clinici e secret in label ad alta cardinalità, log o trace. Le fixture inseriscono canary PHI sintetici per verificare automaticamente assenza di leakage.

## 15. Frequenza e responsabilità

| Frequenza | Prove minime | Responsabile |
|---|---|---|
| Pull request | microbenchmark mirati, contract/invariant, regression detector | team componente |
| Giornaliera | smoke load e failover di processo in ambiente test | QA/SRE |
| Settimanale | workload misto, hot tenant, partner lento, backlog recovery | Performance Engineering |
| Mensile | capacity review, restore campionato, fault stateful selezionato | SRE/Data |
| Trimestrale | chaos game day, perdita nodo/zona, Control Plane outage, DR table-top | SRE/Architecture/Security |
| Semestrale | esercitazione DR tecnica completa e failback per Tier 0 | Business Continuity owner |
| Annuale | scenario cyber-recovery/ransomware isolato e revisione di crisi | CISO/BCM/Executive sponsor |
| Pre-major release | benchmark completo, soak ≥72h, failover e regression report | Release owner |

Eventi ad alto rischio, modifiche di storage/quorum, cambio regione, migrazione database o nuovo orchestratore richiedono prove straordinarie prima del rilascio.

## 16. Casi d'uso end-to-end

### PC-01 — Picco ADT al cambio turno

Venti tenant e più facility generano un burst 3× mentre un endpoint ricevente rallenta.

**Verifiche:** p95/p99; fairness; isolamento dell'endpoint; backpressure; ACK durevole; queue age; nessuna perdita; recupero backlog senza penalizzare il traffico live.

### PC-02 — Perdita di una availability zone

Una zona che ospita worker, broker replica e database replica diventa irraggiungibile durante 2.000 eventi/s.

**Verifiche:** detection/fencing; capacità N+1; election; p99; error budget; zero mismatch; replica delle code; recovery e rebalance senza storm.

### PC-03 — Control Plane isolato per 24 ore

Le Runtime Cell perdono il Control Plane ma mantengono i partner autorizzati.

**Verifiche:** autonomia ≥24h; configurazione firmata; nessun nuovo change non verificato; audit locale; alert; riconnessione e convergenza senza riavvio massivo.

### PC-04 — PACS lento e studio multi-gigabyte

Un PACS riduce banda e resetta connessioni durante l'invio di studi DICOM mentre continuano ADT e lab.

**Verifiche:** streaming limitato; retry non distruttivo; storage commitment; isolamento risorse; nessun impatto Tier 0 non correlato; resume/ripartenza prevista; integrità pixel e metadata.

### PC-05 — Terminology server indisponibile

Scade la cache di alcuni value set durante un burst di risultati laboratorio.

**Verifiche:** comportamento per binding/rischio; nessuna mappatura inventata; quarantena selettiva; preservazione raw; allarme; replay deterministico dopo il recupero.

### PC-06 — Hot tenant malevolo o mal configurato

Un tenant invia query FHIR costose e traffico oltre quota.

**Verifiche:** rate limit; query budget; fairness; nessun esaurimento globale; audit di sicurezza; risposta priva di leakage; continuità degli altri tenant.

### PC-07 — Replay post-outage

Dopo due ore di fermo partner, HHC deve smaltire il backlog mentre riceve il carico live.

**Verifiche:** priorità e quota replay; capacità; idempotenza; ordering per key; tempo di recovery; reconciliation; downstream protetto da retry storm.

### PC-08 — Failover database con ACK concorrenti

Il writer primario cade tra persistenza e risposta al client.

**Verifiche:** esito ambiguo gestito; client retry; deduplica; fencing; RPO locale 0 per ACK già emessi; nessun doppio side effect non riconciliato.

### PC-09 — Restore da backup immutabile

La replica contiene corruzione logica e occorre ripristinare un point-in-time precedente in ambiente pulito.

**Verifiche:** chain of custody; malware scan; chiavi; RPO misurato; consistency check; forward recovery degli eventi validi; riapertura canary; audit e failback.

### PC-10 — SIEM e telemetry backend offline

Metriche centrali e SIEM sono indisponibili mentre il traffico clinico continua.

**Verifiche:** buffer locale protetto; limiti e backpressure; audit tamper-evident; alert alternativo; nessuna PHI nei fallback; recupero in ordine e rilevazione di gap.

### PC-11 — Upgrade con regressione semantica

Un canary introduce una mapping rule che aumenta errori e riduce throughput.

**Verifiche:** metriche per versione; golden business invariant; arresto automatico; rollback; nessun remap degli eventi già accettati senza governance; evidenza completa.

### PC-12 — ETL OMOP concorrente

Un full reload OMOP e query analitiche pesanti coincidono con il picco clinico.

**Verifiche:** isolamento Tier 2; quote compute/I/O; nessun effetto sull'ACK; checkpoint e ripresa ETL; DQD; possibilità di sospendere analytics prima del Tier 0.

## 17. Report ed evidenze

Il report finale contiene:

- obiettivo, ipotesi e criteri di accettazione;
- manifest e diagramma della topologia realmente provata;
- workload, seed, distribuzioni e limitazioni;
- timeline, eventi e fault iniettati;
- percentile, throughput, errori, saturazione e costi;
- conteggi di integrità e riconciliazione;
- SLO/RTO/RPO osservati e margine;
- grafici con assi, unità e intervalli chiaramente definiti;
- anomalie, difetti, rischio residuo e owner;
- raw data, query, dashboard versionata e firme.

Le evidenze di release sono conservate secondo la policy di audit e sono collegate a requirement, risk, build e change. Un risultato senza dati grezzi o manifest riproducibile è informativo, non qualificante.

## 18. Gate di rilascio

Il rilascio è bloccato quando:

- un SLO, invariant o target vincolante è violato;
- una regressione supera il budget senza eccezione approvata e a scadenza;
- failover o restore non rispettano RTO/RPO;
- manca capacità N+1 per un componente Tier 0;
- sono presenti perdita silenziosa, cross-tenant leakage o ACK falso;
- il runbook non è stato esercitato o dipende da una singola persona;
- gli alert non rilevano il guasto entro il detection objective;
- non è possibile spiegare la riconciliazione dei conteggi;
- l'ambiente di prova non è rappresentativo e non esiste compensazione motivata.

## 19. Fonti ufficiali e data di verifica

Fonti verificate il **1 settembre 2026**:

- Principles of Chaos Engineering: <https://principlesofchaos.org/>
- Kubernetes, Disruptions e PodDisruptionBudget: <https://kubernetes.io/docs/concepts/workloads/pods/disruptions/>
- Kubernetes, topology spread constraints: <https://kubernetes.io/docs/concepts/scheduling-eviction/topology-spread-constraints/>
- Kubernetes, liveness, readiness e startup probes: <https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/>
- OpenTelemetry Specification: <https://opentelemetry.io/docs/specs/otel/>
- OpenTelemetry semantic conventions per metriche: <https://opentelemetry.io/docs/specs/semconv/general/metrics/>
- NIST SP 800-184, Guide for Cybersecurity Event Recovery: <https://csrc.nist.gov/pubs/sp/800/184/final>
- NIST SP 800-34 Rev. 1, Contingency Planning Guide: <https://csrc.nist.gov/pubs/sp/800/34/r1/final>

Prima di ogni major release, SRE verifica le versioni correnti delle specifiche e registra nel manifest eventuali cambiamenti. Le linee guida esterne non sostituiscono i limiti di sicurezza clinica, gli SLO e le decisioni architetturali HHC.
