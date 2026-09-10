# Roadmap: dall'MVP al prodotto enterprise

Stato: baseline di programma 1.0  
Data di riferimento: 1 settembre 2026  
Orizzonte: avvio Q4 2026, enterprise readiness prima delle scadenze EHDS 2029  
Target: installazioni sanitarie europee multi-azienda, multi-facility e multi-site

## 1. Scopo

Questa roadmap definisce il percorso dal primo incremento eseguibile alla release enterprise completa di HyperHealth Connect (HHC). L'MVP non è un prototipo usa-e-getta: è il primo vertical slice della stessa architettura finale. Ogni fase deve produrre software installabile, evidenze verificabili e una riduzione misurabile del rischio.

Le date sono finestre di pianificazione, non impegni contrattuali. Al termine di ogni fase il programma viene ri-pianificato usando throughput del team, risultati dei benchmark, feedback clinico e rischio residuo. Scope e qualità possono essere rinegoziati; i gate di sicurezza, integrità, audit e continuità non possono essere eliminati per rispettare una data.

## 2. Risultato finale atteso

La release enterprise deve offrire:

- Control Plane centralizzabile e Data Plane distribuito in Runtime Cell locali;
- supporto multi-tenant, multi-organization, multi-facility e multi-CDM;
- HL7 v2, FHIR, REST, SOAP, file/SFTP, JDBC, messaging, CDA/IHE e DICOM/DICOMweb secondo capability dichiarate;
- flow e mapping dichiarativi, versionati, firmati, testabili e promuovibili;
- raw event immutabili, lineage, replay e riconciliazione end-to-end;
- semantic layer canonico, terminologie e Semantic Mapping Registry;
- proiezione OMOP con quality gate e compatibilità OHDSI;
- SLO fino al 99,99% per Tier 0, RPO locale 0 dopo ACK durabile e DR multi-site;
- audit append-only con tamper evidence, monitoraggio continuo 24×7 e integrazione NOC/SOC/SIEM;
- deployment Kubernetes enterprise, modalità container portabile e upgrade a basso downtime;
- secure development lifecycle, SBOM, vulnerability handling e assurance evidence;
- adapter evolvibili verso MyHealth@EU, HealthData@EU e requisiti EHDS applicabili.

## 3. Principi di investimento

### 3.1 Costruire una sola volta i fondamenti

I seguenti elementi entrano nella prima fase e non vengono sostituiti in seguito:

- identificativi Tenant → Organization → Facility → Application → Endpoint;
- HHC Integration Envelope versionato;
- raw storage immutabile e checksum;
- Connector SDK e porte HHC sopra le librerie esterne;
- flow dichiarativo e Configuration as Code;
- separazione Control Plane/Data Plane;
- correlation ID, audit event e telemetry conventions;
- idempotency, retry e error model comuni;
- release bundle firmato e compatibility metadata;
- confine fra percorso clinico e analytics.

Le fasi successive ne aumentano scala, profondità e copertura; non cambiano paradigma.

### 3.2 Rinviare senza creare debito strutturale

L'MVP può limitare:

- numero di protocolli e profili;
- ricchezza del designer visuale;
- automazione di HA/DR;
- numero di domini canonici e OMOP;
- autoscaling e multi-region;
- ecosistema di connector di terze parti.

Non può rinviare:

- isolamento dello scope;
- durabilità prima dell'ACK per eventi critici;
- audit di accessi e modifiche;
- cifratura, secret reference e dependency scanning;
- versionamento e compatibilità;
- test automatici e dati sintetici;
- health, metriche e trace;
- impossibilità per analytics di bloccare il fast path.

### 3.3 Evidenze incrementali

Ogni fase produce:

1. release candidate riproducibile e firmata;
2. test report funzionale, sicurezza, performance e failure;
3. SBOM e vulnerability disposition;
4. runbook, dashboard e alert;
5. mapping requisiti → test → evidenza;
6. decisioni ADR aggiornate;
7. risk register e remediation;
8. retrospettiva con misure reali.

## 4. Timeline indicativa

| Fase | Finestra indicativa | Maturità | Outcome principale |
|---|---|---|---|
| 0 — Mobilitazione e assurance | Q4 2026 | Program ready | Architettura, governance, ambienti e test strategy approvati |
| 1 — Foundation / Technical MVP | Q4 2026–Q1 2027 | MVP tecnico | Due flow clinici end-to-end, tracciabili e replayable |
| 2 — Production Pilot MVP | Q2–Q4 2027 | Pilot production | Una organization e più facility su runtime HA controllato |
| 3 — Semantic & Interoperability Suite | H1 2028 | Product expansion | Semantic registry, OMOP, IHE/CDA e use case europei prioritari |
| 4 — Enterprise & Imaging Maturity | H2 2028 | Enterprise GA | Multi-azienda, DR, imaging, scale, assurance e supportabilità |
| 5 — EHDS/National Adaptation Train | 2027–2031 continuo | Regulatory evolution | Adapter aggiornati agli atti UE e profili nazionali |

La fase 5 è un workstream continuo, non una ragione per ritardare i fondamenti. Gli atti di esecuzione EHDS previsti entro marzo 2027 alimentano il backlog; l'obiettivo è arrivare prima di marzo 2029 con adapter qualificabili per patient summary ed ePrescription/eDispensation e preparare entro marzo 2031 immagini, laboratorio e discharge report.

## 5. Workstream permanenti

| Workstream | Responsabilità | KPI di programma |
|---|---|---|
| Product & Clinical | use case, safety, workflow e acceptance | outcome accettati, clinical hazard chiusi |
| Control Plane | tenancy, registry, policy, deployment e UI | lead time change, rollback success |
| Runtime & Connectors | SDK, gateway, worker, reliability e protocolli | throughput, latency, delivery success |
| Semantic & Data | canonical, mapping, terminology, lineage e OMOP | coverage, quality gate, reproducibility |
| Security & Privacy | IAM, encryption, audit, SSDLC e privacy | control coverage, vulnerability age |
| SRE & Platform | Kubernetes, storage, telemetry, HA/DR e capacity | SLO, MTTR, restore/failover evidence |
| Quality & Conformance | automation, standard profiles e release evidence | escaped defects, pass rate, test depth |
| Regulatory & Standards | EHDS, GDPR, NIS2, CRA, AI/MDR screening | gap closure, evidence freshness |
| Adoption & Migration | legacy inventory, training, support e rollout | migrated flows, operator readiness |

Un responsabile clinico e un responsabile SRE devono partecipare a tutte le decisioni che influenzano patient safety o Tier 0.

## 6. Fase 0 — Mobilitazione e assurance

L'esecuzione operativa e le evidenze sono definite in [Esecuzione della Fase 0](./phase-0-execution.md).

Stato: completata e qualificata il 7 settembre 2026. Il [report di qualification](./phase-0-qualification.md) registra controlli, digest, limiti e condizioni di ingresso alla Fase 1.

### 6.1 Obiettivi

- trasformare la documentazione in baseline approvata;
- chiudere gli ADR fondativi;
- costruire ambienti, pipeline e dati sintetici;
- selezionare reference implementation e policy di dipendenza;
- definire operating model, team ownership e risk governance.

### 6.2 Deliverable

- repository modulare, branch protection e CODEOWNERS;
- CI con build riproducibile, test, SAST, SCA, secret scan, SBOM e firma;
- ambienti dev/test/conformance/performance isolati;
- threat model di sistema e data protection design review;
- terminology e licensing assessment;
- reference topology e benchmark protocol;
- catalogo iniziale dei casi d'uso e matrice di tracciabilità;
- ADR-001…ADR-010 almeno `Accepted` o `Rejected` con motivazione;
- Business Impact Analysis preliminare per Tier 0/1;
- schema audit comune e policy PHI nei log;
- regole per synthetic data e divieto di PHI reale negli ambienti non production.

### 6.3 Exit criteria

- nessuna dipendenza critica senza license/security owner;
- pipeline produce artefatto firmato e SBOM;
- reference environment è ricostruibile da codice;
- test di tenant isolation e redaction esistono prima dei feature test;
- ADR fondativi non presentano conflitti con `product/requirements.md`;
- risk register contiene owner e trattamento per tutti i rischi critici.

### 6.4 Casi d'uso preparati

UC-CLIN-001 ADT, UC-CLIN-002 laboratorio, UC-OPS-002 autonomia dal Control Plane, UC-OPS-003 store-and-forward e UC-OPS-007 audit investigativo.

## 7. Fase 1 — Foundation / Technical MVP

La scomposizione operativa, le dipendenze, le stime e i gate di completamento sono definiti nella [roadmap esecutiva della Fase 1](./phase-1-execution.md).

### 7.1 Outcome

Dimostrare due vertical slice reali con l'architettura definitiva:

1. HL7 v2 ADT/ORU → validazione → mapping → HL7/REST destination;
2. HL7 v2 ORU → canonical observation → FHIR Observation, con proiezione OMOP dimostrativa non production.

### 7.2 Scope funzionale

- HHC Integration Envelope v1;
- raw object store immutabile con checksum e retention metadata;
- Platform DB con Tenant/Organization/Facility/Application/Endpoint;
- Connector SDK v0.x e connector HL7v2/MLLP, REST e FHIR client;
- flow YAML/JSON, schema validation e CLI/API di deploy;
- parser, validator, technical mapping e routing;
- fast path e queue asincrona semplice;
- delivery ledger, retry bounded, DLQ e manual replay autorizzato;
- UI essenziale per inventory, status, trace e Message Explorer metadata;
- OIDC, ruoli minimi, secret reference e TLS;
- OpenTelemetry, dashboard base e audit append-only iniziale;
- Docker Compose e Kubernetes dev profile.

### 7.3 Scope di qualità e operatività

- contract test per Connector SDK;
- synthetic ADT/ORU corpus multilingue e casi errati;
- crash test immediatamente prima/dopo il durability point;
- reconciliation ingress/raw/ledger/delivery;
- baseline latency/throughput pubblicata;
- backup e restore del Platform DB/raw store in ambiente test;
- runbook per endpoint down, DLQ, certificate expiry e raw store failure;
- PHI leakage test su log/trace/metriche.

### 7.4 Cosa non entra

- pilot con dati clinici reali;
- HA multi-zone certificata;
- tenancy fisica dedicata;
- IHE, CDA, DICOM e DICOMweb;
- designer grafico completo;
- secondary use operativo;
- SLA contrattuale.

L'assenza non autorizza scorciatoie: interfacce, scope e persistence model devono supportare le fasi successive.

### 7.5 Exit criteria

- due flow configurabili, testabili, eseguibili e tracciabili senza modifica del codice core;
- RPO locale 0 dopo `ACK_ON_DURABLE` dimostrato in fault test;
- nessuna perdita o duplicazione non spiegata su almeno 1 milione di eventi sintetici;
- overhead fast path misurato e regressioni bloccate dalla CI;
- ogni evento mostra raw checksum, flow/mapping version, trace e delivery receipt;
- Control Plane può essere arrestato senza fermare il flow già distribuito per almeno 4 ore;
- security review senza finding critico aperto;
- upgrade e rollback di un flow sono provati.

### 7.6 Decisione go/no-go

Il programma prosegue solo se il modello di durabilità, envelope, SDK e configurazione dimostra stabilità. Se non scala, si corregge in questa fase prima dell'onboarding di un pilot.

## 8. Fase 2 — Production Pilot MVP

### 8.1 Outcome

Eseguire un pilot controllato con una organization e almeno due facility, includendo un flow Tier 0 e più flow Tier 1. Il pilot usa dati reali soltanto dopo DPIA/processo privacy del cliente, security acceptance e readiness operativa.

### 8.2 Capability

- Runtime Cell per facility con worker multipli;
- broker/event path persistente, partitioning, idempotenza e deduplica;
- durable local spool e autonomia di 24 ore dal Control Plane;
- canary deployment, health gate, rollback e bundle firmati;
- mapping registry/editor con review e separation of duties;
- contract/schema registry e compatibility rule;
- Keycloak o IdP enterprise, MFA, RBAC/ABAC iniziale e service identity;
- Vault/KMS integration, certificate inventory e rotazione;
- audit journal tamper-evident e export SIEM;
- dashboard per facility/flow, SLO, queue age, synthetic monitoring e alerting;
- PostgreSQL HA, object storage HA, broker multi-zone;
- backup immutabile, PITR e primo DR drill di componenti;
- support workflow, on-call, incident management e change management;
- migration toolkit iniziale per inventario dei flow legacy.

### 8.3 Casi d'uso in pilot

| Caso d'uso | Scope minimo |
|---|---|
| UC-CLIN-001 | ADT fra HIS, LIS e sistema downstream in due facility |
| UC-CLIN-002 | risultati laboratorio con correzione/cancellazione e mapping locale |
| UC-OPS-002 | WAN/Control Plane outage di 24 ore |
| UC-OPS-003 | downstream outage, circuit breaker e catch-up |
| UC-OPS-005 | revoca credenziale e quarantena endpoint |
| UC-OPS-006 | rolling upgrade e rollback per facility |
| UC-OPS-007 | evidence package per accesso, change e replay |
| UC-EU-001 | export access log verso sistema di trasparenza del cliente |

### 8.4 Target pilot

- Tier 0 SLO interno ≥99,95% durante pilot, con design già compatibile col 99,99% enterprise;
- zero perdita di eventi accettati;
- restore entro 2 ore per componenti pilota e recovery point ≤15 minuti cross-site;
- p95/p99 entro target di prodotto sulla reference topology;
- alert Tier 0 riconosciuto secondo on-call policy;
- backlog dopo outage recuperato senza violare limiti downstream;
- tutte le operazioni privilegiate e gli accessi al raw auditati.

Il valore 99,95% è un gate di pilot, non una riduzione del target finale Tier 0 99,99%.

### 8.5 Exit criteria

- almeno 90 giorni di esercizio controllato con error budget e post-incident review;
- nessun patient-safety finding critico irrisolto;
- DR component-level, backup restore e isolated-facility test superati;
- operatori NOC/SOC/site formati e runbook esercitati;
- security penetration test e tenant-isolation test superati;
- reconciliation giornaliera senza gap inspiegati;
- lead time deploy, rollback time e MTTR misurati;
- cliente pilota firma acceptance rispetto a scope e limiti dichiarati.

## 9. Fase 3 — Semantic & Healthcare Interoperability Suite

### 9.1 Outcome

Trasformare il runtime production-ready in Healthcare Semantic Event Hub e ampliare gli standard sanitari senza compromettere il pilot core.

### 9.2 Semantic e OMOP

- Canonical Semantic Event Model v1 per patient, encounter, observation, condition, procedure, medication e document metadata;
- Semantic Mapping Registry, workflow data steward e metriche di coverage;
- Vocabulary Service con snapshot, local concepts e license controls;
- lineage upstream/downstream;
- OMOP Schema Registry e projection pipeline;
- OMOP multi-CDM, dataset lifecycle e quality gate;
- DataQualityDashboard, Achilles e compatibility OHDSI;
- concept set e cohort API iniziali;
- pseudonimizzazione e separation of duties per secondary use.

### 9.3 Standard e connector

- CDA e SOAP/XSD/WSDL tooling;
- IHE XDS/XCA, PIX/PDQ/XCPD, MHD/PIXm/PDQm e ATNA selezionati per use case;
- FHIR R4/R4B/R5 strategy, profile/IG validation e Bulk Data dove richiesto;
- adapter patient summary ed ePrescription/eDispensation preparati per specifiche UE/nazionali applicabili;
- terminology integration e patient identity adapter;
- migration toolkit con shadow compare.

### 9.4 Casi d'uso

UC-CLIN-003 documenti IHE/CDA; UC-CLIN-005 prescrizione transfrontaliera; UC-CLIN-006 patient summary; UC-EU-002 diritti/restrizioni; UC-EU-003 uso secondario; UC-EU-004 public health; UC-DATA-001 OMOP multi-CDM; UC-DATA-002 studio multicentrico; UC-OPS-001 migrazione legacy.

### 9.5 Exit criteria

- mapping semantici approvati e misurabili per domini scelti;
- un dataset OMOP `READY` supera gate strutturale, semantico, DQD e characterization;
- lineage ricostruisce raw → canonical → FHIR/OMOP;
- IHE/FHIR capability dichiarate superano conformance suite;
- uso secondario opera soltanto con permit/purpose e ambiente controllato;
- un guasto semantic/OMOP non modifica SLO del fast path;
- reprocessing produce nuova versione senza mutare la precedente;
- differenze nazionali rimangono negli adapter, non nel core.

## 10. Fase 4 — Enterprise & Imaging Maturity

### 10.1 Outcome

Rilasciare HHC 1.0 Enterprise GA, installabile in grandi gruppi sanitari europei, con HA/DR, scale, auditabilità e lifecycle dimostrati.

### 10.2 Capability finali

- multi-tenant/organization/facility con profili di isolamento fino a cluster/account dedicato;
- amministrazione delegata, policy inheritance e data locality;
- DICOM/DICOMweb connector suite e Orthanc integration opzionale;
- streaming payload multi-GB e bandwidth governance;
- Kubernetes production pattern, autoscaling e capacity reservation;
- HA multi-zone per ogni dipendenza Tier 0;
- DR multi-site con fencing, failover/failback e clean-room recovery;
- SLO engine, burn-rate alert, synthetic transaction e continuous control monitoring;
- audit append-only firmato, evidence package e integrazione SIEM/SOAR;
- Connector SDK 1.0, trust policy e compatibility certification kit;
- upgrade rolling/canary, N/N-1 compatibility e LTS policy;
- support model 24×7 per Tier 0 e lifecycle di lungo periodo;
- product security file e control matrix enterprise;
- performance, endurance, chaos, penetration e conformance program completi;
- governed AI Tool Gateway senza accesso SQL/raw arbitrario.

### 10.3 Casi d'uso finali

Tutti i casi definiti in `../product/personas-and-use-cases.md`, con particolare gate su:

- UC-CLIN-004 imaging enterprise;
- UC-CLIN-007 dispositivi e high-frequency observations;
- UC-CLIN-008 territorio e telemedicina;
- UC-DATA-003 AI governata;
- UC-OPS-004 disaster recovery di sito;
- UC-OPS-005 contenimento cyber selettivo;
- UC-OPS-006 upgrade senza interruzione significativa;
- UC-OPS-007 audit investigativo completo.

### 10.4 Exit criteria enterprise GA

1. Tier 0 99,99%, Tier 1 99,95% e Tier 2 99,9% dimostrati nel periodo di qualification definito.
2. Reference Runtime Cell sostiene almeno 2.000 eventi/s asincroni da 8 KiB per 60 minuti, p99 ingest ≤250 ms, CPU media ≤70% e zero mismatch.
3. Fast path HL7 v2 ≤64 KiB ha overhead HHC p95 ≤50 ms e p99 ≤100 ms nelle condizioni dichiarate.
4. Failover Tier 0 rispetta RTO ≤30 minuti e RPO cross-site ≤5 minuti; RPO locale dopo ACK è 0.
5. DR e failback end-to-end sono esercitati, inclusi identity, KMS, DNS, broker, DB, object store e audit.
6. Simulazione ransomware recupera da copia immutabile in ambiente pulito.
7. Perdita di nodo/zona e Control Plane non causa perdita di eventi accettati.
8. Carico 5× su una facility non viola lo SLO di una Runtime Cell isolata.
9. Nessun finding critico aperto di sicurezza, privacy, patient safety o license compliance.
10. Audit chain e access log superano integrity verification e scenario investigativo.
11. Upgrade, rollback e compatibility matrix N/N-1 sono provati.
12. Runbook, training, support matrix e spare capacity sono approvati.
13. Documentazione e claim commerciali riportano capability e limiti esatti.

## 11. Fase 5 — EHDS e adattamento nazionale continuo

### 11.1 Contesto normativo

Al 1 settembre 2026:

- EHDS è in vigore; gli atti di esecuzione chiave sono attesi entro marzo 2027;
- patient summary ed ePrescription/eDispensation entrano nella prima finestra applicativa da marzo 2029;
- immagini, risultati di laboratorio e discharge report seguono da marzo 2031;
- dal 11 settembre 2026 si applicano gli obblighi CRA di reporting per vulnerabilità attivamente sfruttate e incidenti gravi, se HHC rientra nel relativo ruolo/scope;
- l'AI Act ha raggiunto una fase generale di applicazione e ogni funzione AI richiede classification e governance dedicate.

### 11.2 Processo di adaptation

1. Regulatory watch mensile UE e nazionale.
2. Gap assessment entro 30 giorni da specifica stabile rilevante.
3. ADR/requirement change e impact analysis.
4. Adapter/profilo versionato, non modifica hard-coded al core.
5. Conformance environment e test con nodi nazionali.
6. Pilot limitato e evidence package.
7. Rollout per Paese con support matrix e deprecation date.

HHC usa il termine `EHDS-ready` solo per indicare architettura adattabile. “Conforme” o “certificato” è usato esclusivamente dopo il percorso applicabile.

## 12. Sequenza dei casi d'uso

| Caso d'uso | Fase iniziale | Production complete | Dipendenze principali |
|---|---:|---:|---|
| UC-CLIN-001 ADT | 1 | 2 | HL7, envelope, ordering, identity adapter |
| UC-CLIN-002 Laboratorio | 1 | 3 | mapping, terminology, FHIR/OMOP |
| UC-CLIN-003 IHE/CDA | 3 | 3 | IPF, contracts, document security |
| UC-CLIN-004 Imaging | 4 | 4 | dcm4che, streaming, network capacity |
| UC-CLIN-005 ePrescription EU | 3 | 5 | adapter nazionale, MyHealth@EU specs |
| UC-CLIN-006 Patient Summary | 3 | 5 | identity, FHIR/EEHRxF, access audit |
| UC-CLIN-007 Devices | 2 limitato | 4 | rate isolation, IEC 80001 risk process |
| UC-CLIN-008 Territorio | 3 | 4 | intermittent connectivity, federation |
| UC-EU-001 Access log | 2 | 4 | audit integrity, identity, export |
| UC-EU-002 Diritti/restrizioni | 3 | 4 | lineage, policy, national law |
| UC-EU-003 Uso secondario | 3 | 4/5 | permit, SPE, pseudonymisation |
| UC-EU-004 Public health | 2 limitato | 3/5 | national adapter, reconciliation |
| UC-DATA-001 OMOP | 1 demo | 3 | canonical, vocabulary, quality gate |
| UC-DATA-002 Studio multicentrico | 3 | 4 | multi-CDM, disclosure control |
| UC-DATA-003 AI governata | 3 preview | 4 | AI governance, tool API, audit |
| UC-OPS-001 Migrazione legacy | 2 | 4 | inventory, shadow compare, rollback |
| UC-OPS-002 Control Plane outage | 1 | 2 | signed bundle, local spool |
| UC-OPS-003 Store-and-forward | 1 | 2 | durable queue, circuit breaker |
| UC-OPS-004 DR sito | 2 componenti | 4 | secondary site, fencing, clean restore |
| UC-OPS-005 Cyber containment | 2 | 4 | IAM, cell isolation, SIEM/SOAR |
| UC-OPS-006 Upgrade | 2 | 4 | compatibility, canary, rollback |
| UC-OPS-007 Audit investigation | 1 | 4 | audit vault, evidence package |

## 13. Gate trasversali

### Gate A — Architecture integrity

Nessuna feature può introdurre dipendenza sincrona di analytics/Control Plane nel Tier 0, bypassare envelope/ledger o accedere direttamente ai DB di un altro dominio.

### Gate B — Patient safety

Hazard analysis, failure behaviour, data correctness, clinical review e rollback sono obbligatori per trasformazioni cliniche. Unknown/ambiguous non viene trasformato in dato valido.

### Gate C — Security/privacy

Threat model, least privilege, encryption, redaction, audit e data lifecycle sono verificati. Finding critici bloccano la release.

### Gate D — Reliability/performance

Benchmark, endurance, fault injection, reconciliation e SLO evidence sono confrontati alla baseline; regressioni oltre budget bloccano la promozione.

### Gate E — Operations

Dashboard, alert, runbook, backup, restore, ownership, escalation e capacity sono pronti prima dell'onboarding production.

### Gate F — Compatibility

API, SDK, flow, mapping, schema e migration hanno compatibility report e rollback. Nessuna major change nascosta in release minore.

### Gate G — Regulatory claims

Claim EHDS/MDR/AI/NIS2/CRA/standard sono revisionati da funzione competente e supportati da evidenze; il software non è commercializzato come certificato per implicazione.

## 14. Metriche di programma

### Delivery

- lead time da approved change a production;
- deployment frequency e change failure rate;
- rollback success/time;
- escaped defect per severity;
- percentuale requisiti con test/evidenza.

### Runtime

- availability, latency e success rate per tier;
- oldest queue age, retry/DLQ/quarantine;
- reconciliation gap e event loss;
- capacity headroom e catch-up rate;
- MTTA, MTTR e incident recurrence.

### Semantic/data

- mapping coverage/ambiguity per facility e dominio;
- data-quality failure e dataset release time;
- lineage completeness;
- OMOP freshness e DQD trend;
- riuso di mapping/connector rispetto a nuove implementazioni.

### Security/assurance

- vulnerability age per severity;
- privileged action coverage e audit integrity;
- control evidence freshness;
- backup restore e DR drill pass rate;
- access review e secret/certificate expiry compliance.

## 15. Team topology indicativa

Per rispettare la finestra 2026–2028 senza comprimere i gate, il programma richiede workstream realmente paralleli:

- Product/Clinical & Regulatory;
- Control Plane/UI;
- Runtime/Connector SDK;
- Semantic/OMOP;
- Platform/SRE;
- Security/Quality Engineering.

Ogni stream ha tech lead e test responsibility; SRE, security e clinical safety non sono code review finali ma membri del ciclo di design. La capacità viene pianificata su outcome, non su percentuale di completamento delle feature.

## 16. Fonti e milestone europee

- [Commissione europea — European Health Data Space e timeline](https://health.ec.europa.eu/ehealth-digital-health-and-care/european-health-data-space-regulation-ehds_en)
- [EUR-Lex — Regolamento (UE) 2025/327](https://eur-lex.europa.eu/eli/reg/2025/327/oj/)
- [Commissione europea — CRA reporting dal 11 settembre 2026](https://digital-strategy.ec.europa.eu/en/policies/cra-reporting)
- [Commissione europea — AI Act e timeline](https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai)
- [Commissione europea — piano cybersecurity ospedali e healthcare provider](https://digital-strategy.ec.europa.eu/en/library/european-action-plan-cybersecurity-hospitals-and-healthcare-providers)

## 17. Condizione di chiusura del programma 1.0

La roadmap è completata soltanto quando HHC 1.0 Enterprise GA supera tutti gli exit criteria della fase 4, i rischi critici sono chiusi o formalmente accettati con scadenza, esiste un piano finanziato per vulnerability handling e supporto LTS, e almeno una installazione multi-azienda/multi-facility ha completato qualification, DR drill, audit investigation e upgrade controllato senza perdita di eventi accettati.
