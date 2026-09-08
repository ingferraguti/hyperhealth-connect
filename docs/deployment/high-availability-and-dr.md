# Alta disponibilità e disaster recovery

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | SRE e Business Continuity Management |
| Approvatori | Architecture, Security, Data Owner, Clinical Operations |

## 1. Finalità

Questa baseline converte availability, business continuity e disaster recovery in proprietà misurabili. Replica non è backup, backup non dimostra restore e pod restart non dimostra continuità del servizio clinico.

## 2. Obiettivi

| Classe | SLO mensile | RTO sito | RPO cross-site | RPO locale dopo ACK |
|---|---:|---:|---:|---:|
| Tier 0 | 99,99% | ≤30 min | ≤5 min | 0 |
| Tier 1 | 99,95% | ≤2 h | ≤15 min | 0 se `ACK_ON_DURABLE` |
| Tier 2 | 99,9% | ≤8 h | ≤1 h | secondo checkpoint |
| Control Plane | 99,9% | ≤4 h | ≤15 min | n/a; runtime autonomo |

Valori contrattuali possono essere più stringenti. Ogni servizio registra tier, dipendenze, owner, modalità di misura ed esclusioni; non è ammesso declassificare per superare un gate.

## 3. Business Impact Analysis

Per tenant/facility e processo clinico la BIA registra:

- servizio, utenti, patient-safety impact e periodo critico;
- MTPD/maximum tolerable disruption;
- RTO, RPO, minimum business continuity objective;
- upstream/downstream, identity, DNS, PKI/KMS e telecom;
- backlog growth e recovery rate richiesto;
- modalità manuale/degradata e rischio clinico;
- responsabile decisionale e comunicazioni;
- requisiti normativi/contrattuali per paese.

La BIA è riesaminata annualmente e dopo cambiamenti importanti o incidenti.

## 4. Failure domain

`Process → Pod/VM → Node → Rack/Zone → Site/Region → Provider/Identity Domain`.

Replica che condivide host, storage, alimentazione, rete, credenziali o amministrazione può non ridurre il failure domain. La topologia documenta dipendenze correlate e common-mode failure, incluse configurazione errata, ransomware e certificati scaduti.

## 5. Strategia HA

- stateless service: almeno due repliche su failure domain distinti;
- stateful quorum: numero/placement coerente col protocollo e write acknowledgement;
- load balancer con health semantico, non solo porta aperta;
- readiness separata da liveness e startup;
- PDB compatibile con quorum e manutenzione;
- N+1 per Tier 0 al picco qualificato;
- graceful drain e in-flight ownership;
- backpressure prima dell'esaurimento;
- fencing prima della promotion di un writer;
- last-known-good e local spool durante dipendenza centrale assente.

## 6. PostgreSQL

Platform DB e OMOP DB hanno cluster e policy separati. Baseline:

- primary più standby sincrono/asincrono secondo RPO e latenza;
- orchestrazione failover con lease/quorum e fencing;
- WAL archiving e base backup per PITR;
- backup cifrato, immutabile e in failure/credential domain separato;
- replica lag, archive gap e restore point monitorati;
- consistency check e application-level reconciliation dopo promotion;
- connection pool riconnette senza retry storm;
- schema migration expand/migrate/contract;
- failback pianificato come nuova migrazione, non ritorno cieco.

La replica può propagare cancellazione o corruzione logica: non sostituisce PITR e copia immutabile.

## 7. Broker/event streaming

Broker mantiene eventi operativi entro retention, non è archivio clinico permanente. Partition replication, in-sync replica/minimum acknowledgement e rack/zone awareness sono coerenti col tier. Si testano leader loss, replica lag, unclean election vietata dove può perdere dati, disk full, retention e consumer offset restore.

Il delivery ledger e raw store permettono reconciliation anche se offset o receipt sono ambigui.

## 8. Object storage e raw

- versioning/object lock secondo policy;
- encryption context e key recovery separati;
- replication status e checksum end-to-end;
- inventory periodico e missing-object detector;
- lifecycle/retention/legal hold testati;
- restore in clean account/site;
- accesso raw ristretto anche durante emergenza;
- large object streaming e multipart consistency verificati.

## 9. Local spool

Spool è cifrato, bounded e monitorato. Un evento confermato non viene eliminato per eviction. Capacity copre almeno 24 ore di isolamento del Control Plane e outage previsto del downstream, oppure la cella applica backpressure/errore prima di promettere durabilità. High/critical watermark attivano alert e riducono workload non prioritari.

## 10. Control Plane continuity

Durante indisponibilità:

- Runtime Cell esegue bundle firmati validi già attivi;
- nuove promotion, grant e configurazioni sono bloccati;
- status/audit si bufferizzano senza payload;
- revoche e certificati seguono emergency procedure;
- expiry policy distingue indisponibilità da compromissione;
- reconnect verifica identity, clock, bundle e desired/observed state.

## 11. Backup policy

Ogni asset registra owner, tier, metodo, frequenza, retention, encryption, location, immutability, credential domain, restore order e test cadence. Copie seguono una strategia multi-copia/multi-media o equivalente risk-based, includendo almeno una copia non alterabile dal primario.

Backup include:

- Platform DB e registry;
- raw/object metadata e, secondo policy, payload;
- delivery ledger/spool state;
- audit journal e verification material;
- configuration/flow/mapping/contract artifact;
- terminology snapshot e license metadata;
- OMOP dataset/version registry;
- PKI/KMS recovery material tramite procedura separata;
- IaC, runbook e toolchain necessari al restore.

Secret correnti non vengono riversati in chiaro nel backup.

## 12. Restore order

1. dichiarare incidente e autorità;
2. isolare sito/account compromesso;
3. creare clean environment da artifact verificati;
4. ristabilire identity, DNS, PKI/KMS e audit path;
5. ripristinare registry/configuration e Platform DB;
6. ripristinare raw, ledger/spool e broker state necessario;
7. verificare checksum, schema e single-writer fencing;
8. avviare Runtime Cell in read/closed ingress mode;
9. riconciliare accepted/pending/delivered/quarantined;
10. aprire canary sintetico, poi facility/wave;
11. recuperare backlog con quota separata;
12. ripristinare Semantic/OMOP/analytics dopo Tier 0/1;
13. approvare failback o stabilizzare il nuovo primario.

## 13. Failover e failback

Trigger automatico è ammesso solo per failure non ambigua e fencing provato. La perdita di quorum, network partition o stato incerto può richiedere decisione umana. Il runbook registra decision authority, evidence, communication, maximum wait e abort.

Failback verifica che il vecchio primario sia isolato, ricostruito da fonte autorevole e riallineato. Dati divergenti non sono uniti automaticamente; entrano in reconciliation/quarantine.

## 14. Cyber recovery

Per ransomware o supply-chain compromise:

- account e credenziali backup separati;
- immutabilità/offline control;
- clean-room con tool e image firmati;
- malware/IOC scan prima del restore;
- recovery point scelto con Security e Data Owner;
- rotazione key/credential e revoca artifact;
- preservation forense distinta dal ritorno in servizio;
- validazione applicativa, non solo checksum storage;
- disclosure/notifica secondo processo applicabile.

## 15. Esercitazioni

| Esercizio | Cadenza minima | Evidenza |
|---|---|---|
| restore campione | mensile automatizzato | checksum e tempi |
| restore completo per classe | trimestrale | application validation |
| failover componente/zone | trimestrale | SLO, fencing, backlog |
| site DR Tier 0 | almeno semestrale | RTO/RPO, canary, reconciliation |
| cyber recovery/tabletop | annuale | decisioni e comunicazioni |
| Control Plane isolation 24 h | ogni major release e almeno annuale | autonomy/capacity |

Un drill annunciato con passaggi saltati è formativo, non evidence qualificante. Almeno periodicamente si usa scenario a sorpresa controllato.

## 16. Monitoring continuo

Metriche: availability/error budget, replica/WAL lag, archive success, last good backup, restore test age, spool usage/age, quorum, object replication, checksum gap, certificate/key expiry, DR drift e recovery capacity. Alert multi-window evita sia ritardo sia rumore. Ogni alert ha owner, severity, runbook e synthetic test.

## 17. Audit ed evidence

Failover, restore, accesso backup, legal hold, key recovery e cambio DNS producono audit append-only. Il DR evidence pack contiene BIA/versione, topologia, build, backup ID, fault timeline, decision log, RTO/RPO osservati, data gap, reconciliation, security scan, comunicazioni, difetti e remediation.

## 18. Casi d'uso

### DR-01 — Crash dopo ACK

Processo cade immediatamente dopo risposta al sender. Al restart l'evento è presente in raw/ledger e viene consegnato o riconciliato: RPO locale 0.

### DR-02 — Perdita availability zone

Worker, broker leader e DB replica della zone A spariscono a 2.000 eventi/s. N+1 mantiene servizio, election rispetta fencing e backlog rientra senza penalizzare live.

### DR-03 — Perdita sito primario

Il sito B viene promosso. Identity/KMS/DNS sono disponibili, RTO Tier 0 ≤30 min e RPO cross-site ≤5 min; receipt ambigui sono deduplicati/reconciliati.

### DR-04 — Corruzione logica replicata

La replica contiene una cancellazione errata. Si seleziona PITR precedente in clean-room e si applica forward recovery degli eventi validi, conservando chain of custody.

### DR-05 — Ransomware

Primario e credenziali operative sono compromessi. Copia immutabile con identity separata rimane disponibile; artifact e key sono ruotati prima del canary.

### DR-06 — Control Plane isolato

Tutte le facility continuano per 24 ore con bundle validi; una cella che raggiunge critical spool applica backpressure senza cancellare eventi confermati.

### DR-07 — SIEM/collector indisponibile

Audit e telemetry si bufferizzano; alert alternativo segnala il limite. Al ritorno si rilevano gap e si esporta in ordine senza PHI nei fallback.

### DR-08 — OMOP perso

Dataset analitico viene ricostruito da canonical/raw e manifest; Tier 0 resta invariato. DQD e publication gate precedono il ritorno dei consumer.

## 19. Gate non derogabili

- nessuna perdita silenziosa o doppio writer;
- RPO locale 0 dopo ACK Tier 0;
- backup immutabile e credenziali separate realmente accessibili;
- restore applicativo entro RTO, non solo database avviato;
- reconciliation completa e gap spiegati;
- runbook eseguibile da personale on-call, non da singola persona;
- alert e escalation funzionanti;
- remediation dei drill con owner/scadenza.

## 20. Fonti ufficiali

Verificate il **5 settembre 2026**:

- [PostgreSQL 18 — High Availability, Load Balancing and Replication](https://www.postgresql.org/docs/current/high-availability.html);
- [PostgreSQL — Continuous Archiving and PITR](https://www.postgresql.org/docs/current/continuous-archiving.html);
- [Apache Kafka — Operations](https://kafka.apache.org/documentation/#operations);
- [Kubernetes — Production environment](https://kubernetes.io/docs/setup/production-environment/);
- [NIST SP 800-34 Rev.1 — Contingency Planning](https://csrc.nist.gov/pubs/sp/800/34/r1/final);
- [NIST SP 800-184 — Cybersecurity Event Recovery](https://csrc.nist.gov/pubs/sp/800/184/final);
- [ISO 22301:2019](https://www.iso.org/standard/75106.html), con Amendment 1:2024 nella matrice standard HHC.

## 21. Collegamenti

- [Topologie](./deployment-topologies.md)
- [Performance e chaos](../testing/performance-and-chaos.md)
- [Supporto e runbook](../operations/support-and-runbooks.md)
- [Risk register](../roadmap/risk-register.md)
