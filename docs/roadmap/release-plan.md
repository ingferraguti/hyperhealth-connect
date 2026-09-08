# Piano delle release

Stato: baseline di programma 1.0  
Data di riferimento: 1 settembre 2026  
Target: HyperHealth Connect 1.0 Enterprise GA

## 1. Scopo

Il piano traduce `mvp-to-enterprise.md` in incrementi distribuibili, criteri di promozione e lifecycle. Ogni release deve essere riproducibile, firmata, installabile e accompagnata da evidenze. Le versioni pre-1.0 possono cambiare contratti solo mediante migration e compatibility note; il suffisso “MVP” non riduce i requisiti di sicurezza, audit o integrità dei dati.

## 2. Modello di versione

HHC usa Semantic Versioning per il prodotto e versioni indipendenti per gli asset:

```text
Application         1.2.3
Connector SDK       1.1
Connector           2.4.1
Envelope            hhc-envelope/v1
Flow definition     hhc.io/v1
Canonical model     1.3
Mapping             immutable revision
Terminology         named snapshot
OMOP mapper         1.2
OMOP CDM            explicit CDM version
```

- **Major**: breaking change o migration obbligatoria.
- **Minor**: capability backward-compatible.
- **Patch**: correzione compatibile, security fix o documentation fix.
- **Build metadata**: commit, pipeline, SBOM digest e timestamp; non cambia la semantica.

Un evento registra le versioni effettivamente usate. Un release bundle include manifest e checksum di tutte le dipendenze.

## 3. Tipi di release

| Tipo | Cadenza | Uso | Vincoli |
|---|---|---|---|
| Feature release | 8–12 settimane dopo GA | capability backward-compatible | full regression e upgrade test |
| Maintenance release | mensile o quando necessario | bug e dipendenze | nessuna feature rischiosa |
| Security hotfix | on demand | vulnerabilità critica/incident | percorso accelerato, stessi controlli essenziali |
| Connector/profile pack | indipendente entro compatibility matrix | protocollo/vendor/nazione | contract/conformance test dedicati |
| Terminology/vocabulary pack | secondo fonte/licenza | nuovi snapshot | parallel load e mapping impact analysis |
| LTS release | 12–18 mesi | clienti enterprise stabili | supporto esteso e backport policy |
| Emergency configuration | on demand | contenimento o cambio endpoint | four-eyes, scadenza, audit e retro-review |

## 4. Release train verso 1.0

| Release | Finestra | Audience | Outcome |
|---|---|---|---|
| R0.1 Engineering Foundation | Q4 2026 | team interno | repository, CI/CD, assurance e reference environment |
| R0.2 Technical MVP | Q1 2027 | demo tecnica controllata | vertical slice HL7/REST/FHIR con raw, trace e replay |
| R0.5 Pilot Alpha | Q2 2027 | facility test | Runtime Cell, broker, HA iniziale e security baseline |
| R0.6 Pilot Beta | Q3 2027 | prima facility production limitata | Tier 0 controllato, SIEM, SLO e restore |
| R0.7 Production Pilot | Q4 2027 | due o più facility della stessa organization | 90 giorni, autonomia locale e DR component-level |
| R0.8 Semantic & OMOP Suite | H1 2028 | clienti design partner | canonical, terminology, multi-CDM e quality gate |
| R0.85 Healthcare Interoperability Pack | H1 2028 | pilot IHE/FHIR | CDA/IHE e profili europei prioritari |
| R0.9 Enterprise Release Candidate | Q3 2028 | qualification enterprise | multi-azienda, imaging, multi-site e full assurance |
| R1.0 Enterprise GA | Q4 2028 | produzione generale | SLA/SLO, supporto LTS, HA/DR e scale approvati |
| R1.x EHDS/National Packs | 2027–2031 | Paesi e nodi qualificati | adapter aggiornati alle specifiche applicabili |

Le finestre sono subordinate ai gate. Un ritardo di una release non viene recuperato trasferendo finding critici alla release successiva.

## 5. R0.1 — Engineering Foundation

Stato: completata e qualificata il 7 settembre 2026; vedi [qualification report](./phase-0-qualification.md). L'enforcement sul forge e la provenance OIDC si attivano alla pubblicazione del repository.

### Scope R0.1

- struttura repository e moduli;
- Java/Spring/Camel baseline e dipendenze isolate;
- build deterministica, artifact repository e firma;
- SAST, SCA, secret scanning, SBOM e provenance;
- unit/contract test framework e synthetic data policy;
- ambienti dev, integration, conformance e performance;
- ADR e control matrix iniziali;
- threat model, privacy model e patient-safety hazard log;
- OpenTelemetry conventions, error model e audit schema;
- Infrastructure as Code per reference environment.

### Gate R0.1

- artefatti ricostruibili da commit taggato;
- nessun secret o PHI nel repository/pipeline;
- dipendenze con licenza e owner;
- build non-root firmata e scansionata;
- documentazione architecture/product/roadmap approvata;
- rischi critici con trattamento finanziato.

## 6. R0.2 — Technical MVP

### Scope R0.2

- tenant hierarchy minima;
- Envelope v1 e raw immutable store;
- Connector SDK v0.2;
- HL7 v2/MLLP, REST e FHIR client;
- flow dichiarativo v0.2 e mapping deterministico;
- processing/delivery ledger;
- retry, DLQ, manual delivery replay;
- UI inventory/status/message metadata;
- OIDC, secret reference, TLS e audit;
- telemetry e reconciliation iniziali;
- Compose e Kubernetes development profiles.

### Acceptance pack

- test report su 1 milione di eventi sintetici;
- crash-before/after-ACK evidence;
- raw/ledger/delivery reconciliation;
- fast-path benchmark iniziale;
- restore test DB/object store;
- security review e PHI leakage scan;
- demo ripetibile dei due vertical slice.

### Limitazioni dichiarate

Non è autorizzata per produzione clinica, HA/SLA o secondary use. Le limitazioni sono incluse nell'artefatto e nella UI, non soltanto nelle release note.

## 7. R0.5 — Pilot Alpha

### Scope R0.5

- Runtime Agent e signed bundle;
- worker pool e broker persistente;
- idempotenza, deduplica, ordering e circuit breaker;
- durable local spool;
- mapping review e contract registry;
- RBAC per organization/facility;
- Vault/KMS e certificate inventory;
- dashboard per flow/facility e alert sandbox;
- PostgreSQL/broker/object storage HA in pre-production;
- canary/rollback e compatibility preflight.

### Gate R0.5

- fault injection su worker, broker e DB;
- isolamento fra due facility;
- 24 ore senza Control Plane in pre-production;
- no event loss, gap o collisione inspiegata;
- restore documentato e ripetibile;
- runbook esercitati dal team operazioni, non dagli sviluppatori soltanto.

## 8. R0.6 — Pilot Beta

### Scope R0.6

- prima facility production limitata;
- flow Tier 0 ADT e Tier 1 laboratory/document;
- audit tamper-evidence e SIEM export;
- SLO/error budget e synthetic transaction;
- privacy access workflow e break-glass;
- production support/on-call;
- backup immutabile e restore in clean environment;
- change, incident e problem management;
- baseline capacity del sito.

### Entry criteria

- DPIA/assessment e contratti del cliente completati;
- network, identity, PKI, DNS/NTP e backup readiness;
- training operatori e downtime procedure;
- production data approval e data retention configurata;
- rollback al sistema precedente provato.

### Exit criteria

- almeno 30 giorni con report SLO e nessun finding critico;
- incident drill e evidence package riusciti;
- reconciliation giornaliera completa;
- failover di nodo/zona senza perdita.

## 9. R0.7 — Production Pilot

### Scope R0.7

- seconda facility e amministrazione organization-level;
- quote/noisy-neighbour, capacity N+1 e recovery catch-up;
- distributed Runtime Cell management;
- access log export e site operator delegation;
- DR component-level cross-site;
- migration inventory/shadow compare;
- support matrix, release/rollback automation e operator handbook.

### Qualification

- 90 giorni di esercizio controllato;
- internal SLO Tier 0 ≥99,95%;
- outage WAN/Control Plane 24 ore;
- downstream outage e backlog recovery;
- credential compromise tabletop e revoca;
- cross-facility authorization negative tests;
- backup restore e DR component drill;
- pen test senza finding critici aperti;
- customer pilot acceptance.

### Decisione

Il prodotto esce dallo stato MVP soltanto dopo R0.7. L'onboarding di nuove organization prima del gate è vietato salvo ambiente non clinico.

## 10. R0.8 — Semantic & OMOP Suite

### Scope R0.8

- Canonical Semantic Model v1;
- Semantic Mapping Registry e stewardship;
- Vocabulary Service e snapshot;
- lineage graph;
- domain pack patient/encounter/observation/condition/procedure/medication;
- OMOP Schema Registry, projection e dataset lifecycle;
- DQD/Achilles e evidence package;
- multi-CDM, concept set e cohort API iniziale;
- pseudonymisation boundary e secure-use controls.

### Gate R0.8

- raw→canonical→OMOP lineage completo su campione e riconciliazione totale;
- mapping coverage per facility e dominio;
- dataset `READY` soltanto dopo gate;
- reprocessing produce nuova versione senza mutazioni;
- analytics failure non influenza fast path;
- query e data access autorizzati per permit/purpose.

## 11. R0.85 — Healthcare Interoperability Pack

### Scope R0.85

- CDA, SOAP/XSD/WSDL tooling;
- profili IHE prioritari tramite IPF;
- FHIR R4/R4B/R5 profile/IG validation;
- patient summary ed ePrescription/eDispensation adapter framework;
- patient identity e terminology adapter;
- national profile packaging e conformance harness.

### Gate R0.85

- capability statement/support matrix accurati;
- conformance suite per ogni profilo dichiarato;
- certificati, trust, audit e timeout testati;
- differenze nazionali isolate nel pack;
- nessun claim MyHealth@EU/EHDS senza percorso di qualifica applicabile.

## 12. R0.9 — Enterprise Release Candidate

### Scope R0.9

- multi-tenant/organization/facility isolation profiles;
- amministrazione delegata e policy inheritance;
- DICOM/DICOMweb, streaming grandi payload e imaging gateway opzionale;
- Kubernetes production, multi-zone e multi-site;
- autoscaling, capacity reservation e quota;
- SLO engine 99,99%, burn-rate e 24×7 monitoring;
- DR end-to-end, fencing/failback e cyber recovery;
- Connector SDK 1.0 RC e third-party trust model;
- AI Tool Gateway governato;
- control mapping ISO/NIS2/CRA/GDPR/EHDS readiness;
- install, upgrade, migration e decommission automation.

### Qualification campaign

| Campagna | Durata minima | Evidenza |
|---|---:|---|
| Functional regression | ogni build RC | test report e coverage |
| Endurance/soak | 7 giorni | throughput, memory, lag, reconciliation |
| Scale | profilo massimo dichiarato | benchmark e saturation curve |
| Chaos/failure | almeno 2 cicli | event accounting e recovery time |
| DR/failback | end-to-end | RTO/RPO, integrity e audit |
| Cyber recovery | tabletop + technical restore | clean-room evidence |
| Penetration | release candidate | findings e retest |
| Conformance | per profilo | tool/version/result |
| Upgrade/rollback | N-1 → RC | compatibility evidence |
| Operational readiness | 24×7 simulation | paging, runbook e escalation |

## 13. R1.0 — Enterprise GA

### Criteri obbligatori

- tutti gli exit criteria della fase 4 in `mvp-to-enterprise.md`;
- zero finding critici aperti e nessun high senza risk acceptance time-bound;
- support organization, on-call, escalation e security contact attivi;
- SLA/SLO language revisionato e dipendenze dichiarate;
- reference architectures small/medium/large e capacity calculator;
- install, backup, restore, DR, upgrade e decommission guides;
- release notes, known limitations e compatibility matrix;
- LTS/backport/end-of-support policy;
- customer acceptance in almeno un ambiente multi-azienda/multi-facility;
- governance pronta per vulnerability/incident reporting applicabile.

### Artefatti GA

```text
container images + signatures
SBOM + provenance + vulnerability report
Helm/Kubernetes and supported Compose packages
database migrations + rollback constraints
connector/profile packs
configuration schemas + SDK
test/conformance/performance reports
threat model + product security file
control matrix + audit evidence
operations/admin/user documentation
release notes + compatibility/support matrix
```

## 14. EHDS e national pack train

I pack sono indipendenti dal core e seguono una matrice:

| Dimensione | Valore registrato |
|---|---|
| Paese/autorità | codice e authority |
| Caso d'uso | patient summary, ePrescription, imaging, lab, discharge, secondary use |
| Specifica | nome, versione e publication status |
| Core compatibility | intervallo versioni HHC |
| Terminologia | package e licenza |
| Conformance | suite, ambiente, risultato e scadenza |
| Certificato/accreditamento | riferimento, se realmente ottenuto |
| Data residency | requisito e topology profile |
| Lifecycle | preview, supported, deprecated, retired |

Una modifica urgente nazionale può essere distribuita come pack senza forkare il core. Breaking change della specifica genera major del pack e migration guide.

## 15. Processo di release

### 15.1 Code complete

- scope congelato;
- feature flag default sicuro;
- unit/contract/integration test superati;
- documentazione e migration draft presenti;
- nessuna attività operativa rimasta priva di owner e scadenza.

### 15.2 Release candidate

- artefatti firmati e immutabili;
- full regression/conformance/performance;
- SBOM e vulnerability disposition;
- upgrade/rollback e backup/restore;
- threat/privacy/safety delta review;
- dashboard/alert/runbook validati.

### 15.3 Go/no-go

Partecipano Product, Engineering, Clinical Safety, Security, Privacy, SRE, Quality e Support. Ogni funzione può bloccare per finding nel proprio mandato. La decisione, eccezioni e scadenze sono auditabili.

### 15.4 Publication

- tag e release manifest;
- immagini e pack nel registry;
- release notes e known issues;
- support matrix e checksums;
- customer notification e maintenance guidance;
- CRA/security reporting workflow pronto se applicabile.

### 15.5 Post-release

- monitoraggio intensivo per finestra definita;
- review di error budget, rollback signal e support ticket;
- patch decision;
- aggiornamento risk register e knowledge base;
- post-implementation review entro 10 giorni lavorativi.

## 16. Deployment waves

| Wave | Target | Durata osservazione | Criterio di avanzamento |
|---|---|---:|---|
| 0 | test interno e conformance | ≥24 h | regression e synthetic pass |
| 1 | canary non clinico | ≥24 h | nessun alert nuovo |
| 2 | facility pilota, flow non Tier 0 | 3–7 giorni | error budget e reconciliation |
| 3 | facility pilota, quota Tier 0 | 7 giorni | SLO e operator acceptance |
| 4 | restante facility | 7–14 giorni | stable operations |
| 5 | organization successiva | secondo change plan | no cross-tenant regression |

Il rollout può essere sospeso automaticamente da SLO burn, data-quality regression, audit gap, security signal o reconciliation mismatch. L'avanzamento non è automatico soltanto perché è trascorso il tempo.

## 17. Upgrade e rollback

### 17.1 Preflight

- compatibility di runtime, bundle, SDK, connector e schema;
- capacity durante coesistenza N/N-1;
- backup e recovery point recente;
- migration dry-run su copia realistica;
- certificati/segreti e endpoint readiness;
- rollback limit e irreversible step dichiarati.

### 17.2 Schema evolution

Usare `expand → deploy compatible code → migrate/backfill → verify → contract`. La fase contract avviene solo dopo scadenza della rollback window. Eventi e configurazioni mantengono reader compatibili durante il rollout.

### 17.3 Rollback trigger

- burn-rate oltre soglia;
- data mismatch o duplicate spike;
- audit/telemetry gap;
- security anomaly;
- latency/queue age regression oltre budget;
- health gate o synthetic transaction fallita;
- incompatibilità con endpoint clinico.

Rollback del software non implica rollback dei dati: la migration strategy deve dichiarare come tornare a un'applicazione compatibile senza perdita.

## 18. Compatibility policy

- Control Plane supporta runtime N e N-1 durante la finestra dichiarata.
- SDK 1.x mantiene source/binary contract secondo support matrix; breaking change richiede 2.0.
- Flow e mapping pubblicati restano eseguibili o migrabili automaticamente per almeno una LTS transition.
- Envelope major precedente è leggibile durante retention/reprocessing definito.
- Terminology snapshot usati da dataset pubblicati restano disponibili finché serve la riproducibilità.
- API hanno deprecation header, migration guide e almeno una minor release di preavviso, salvo security emergency.
- Database downgrade non è promesso; è garantito il rollback applicativo entro lo schema expand-compatible dichiarato.

## 19. Supporto e lifecycle

### 19.1 Support tier

| Livello | Copertura | Target iniziale |
|---|---|---|
| P1 Critical | 24×7, patient care/service Tier 0 impattato | acknowledgement ≤15 min |
| P2 High | 24×7 o extended hours per contratto | acknowledgement ≤1 h |
| P3 Medium | business hours | acknowledgement ≤1 giorno lavorativo |
| P4 Low | business hours | backlog pianificato |

Resolution time dipende da causa e sistemi esterni; si gestiscono update frequenti, workaround e owner, non promesse non controllabili.

### 19.2 Lifecycle indicativo

- feature release: 12 mesi di maintenance;
- LTS: almeno 36 mesi, estendibili contrattualmente;
- security fixes: secondo severity e risk policy;
- connector/profile pack: supportato finché fonte/protocollo e core compatibility restano disponibili;
- end-of-support: preavviso, migration path e data/config export.

## 20. Release metrics

- deployment lead time, frequency e change failure rate;
- percentuale rollback e mean rollback time;
- defect escape per severity e componente;
- performance delta rispetto alla baseline;
- vulnerability count/age e SLA remediation;
- test flakiness e conformance pass rate;
- documentation/runbook completeness;
- support ticket nei primi 30 giorni;
- SLO/error-budget impact per wave;
- mismatch di reconciliation, audit gap e data-quality regression.

## 21. Release checklist enterprise

- [ ] Scope, claim e known limitations approvati.
- [ ] Requisiti e casi d'uso tracciati a test.
- [ ] Artefatti riproducibili, firmati e con SBOM.
- [ ] Vulnerability e license gate superati.
- [ ] Test funzionali, conformance, scale, endurance e failure superati.
- [ ] Upgrade, rollback, backup e restore verificati.
- [ ] DR/failback eseguiti quando la release tocca componenti critici.
- [ ] Threat model, privacy e patient-safety review aggiornati.
- [ ] Audit coverage e tamper-evidence verificati.
- [ ] Dashboard, alert, synthetic e runbook aggiornati.
- [ ] Capacity e N+1 headroom approvati.
- [ ] Compatibility/support matrix pubblicata.
- [ ] Training e support readiness completati.
- [ ] Go/no-go e ogni eccezione registrati.

## 22. Riferimenti

- `mvp-to-enterprise.md`
- `risk-register.md`
- `../product/requirements.md`
- `../product/personas-and-use-cases.md`
- `../architecture/scalability-and-resilience.md`
- `../architecture/data-flow.md`
- `../development/dependency-and-license-policy.md`
- [CRA reporting obligations dal 11 settembre 2026](https://digital-strategy.ec.europa.eu/en/policies/cra-reporting)
