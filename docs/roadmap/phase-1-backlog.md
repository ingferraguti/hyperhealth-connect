# Backlog baselined R0.2 — Fase 1

Stato: BASELINED — Ready to build
Baseline: 10 settembre 2026
Release: R0.2 Technical MVP
Owner: Program Engineering
Fonte machine-readable: [`governance/phase-1-backlog.yml`](../../governance/phase-1-backlog.yml)

## 1. Finalità e regole di utilizzo

Questo documento trasforma la roadmap in un backlog verificabile `requirement → use case → epic → test → evidence`. Le attività P1-nnnn e la sequenza vincolante restano definite in [phase-1-execution.md](./phase-1-execution.md); qui si stabiliscono owner, outcome, acceptance oracle e prova richiesta per ciascun epic P0.

Tutti gli epic sono P0: un epic non completato impedisce R0.2. `DONE` richiede evidence riproducibile collegata a commit e manifest, non soltanto codice unito. I due vertical slice usano esclusivamente dati sintetici e non autorizzano uso clinico o produzione.

## 2. Epic P0 e tracciabilità

| Epic | Outcome | Requirement principali | Use case | Owner accountable | Acceptance test/oracle | Evidence attesa |
|---|---|---|---|---|---|---|
| WP1-01 Scoped platform | gerarchia e API tenancy/identity persistenti | FR-TEN-001/002/003, NFR-SEC-001/002, NFR-SCL-001 | UC-CLIN-001, UC-SEC-01 | Platform Engineering | query/cache/API senza scope falliscono; matrice cross-tenant/facility nega prima del payload; migration fresh/N-1 verde | `evidence/R0.2/G2/` isolation report, migration report, schema digest |
| WP1-02 Durable ingest | envelope e raw recuperabile prima dell'ACK | FR-EVT-001/002/003, NFR-BC-001, NFR-REL-001 | UC-CLIN-002, UC-REL-01 | Runtime Engineering | crash matrix ADR-026; per ogni ACK durable esistono raw checksum-verificato e ingest record committed | `evidence/R0.2/G3/` crash matrix, object contract, reconciliation counts |
| WP1-03 Explainable delivery | ledger, retry, DLQ e replay idempotenti | FR-EVT-004/005, FR-RPL-001/002, NFR-REL-002/003 | UC-OPS-003, UC-REL-02 | Runtime Engineering | duplicati concorrenti/ACK perso/restart non generano side effect inspiegati; ogni evento chiude in stato esplicito | `evidence/R0.2/G4/` state-transition, duplicate, replay e reconciliation reports |
| WP1-04 Stable connector boundary | SDK v0.2 e test kit indipendenti dal core | FR-CON-001/002, NFR-STD-001, NFR-MNT-001 | UC-OPS-001, UC-CON-01 | Integration Runtime | ingress/egress contract suite; drain rilascia risorse; connector esempio usa solo API pubbliche | `evidence/R0.2/G5/` compatibility, resource-leak e permission reports |
| WP1-05 Protocol ready | connector MLLP, REST e FHIR limitati ai profili provati | FR-CON-003/004, NFR-SEC-003, NFR-PERF-001 | UC-CLIN-001/002, UC-CON-02 | Connector Engineering | ACK/HTTP receipt correlati al ledger; malformed/oversized/charset/profile negativi bounded e senza leakage | `evidence/R0.2/G6/` protocol contracts, fuzz-smoke, capability statement |
| WP1-06 Deployable runtime | flow e bundle versionati, firmati e reversibili | FR-FLW-001/002/003/004, FR-MAP-001/002, NFR-AVL-002 | UC-OPS-002/006, UC-FLW-01 | Runtime & Release Engineering | due flow senza core changes; bundle tamper/scope/compatibility negativi; rollback e CP outage 4h | `evidence/R0.2/G7/` bundle verification, deploy/rollback, autonomy report |
| WP1-07 Vertical slice A | ADT/ORU da MLLP a HL7/REST end-to-end | FR-CON-003, FR-FLW-001, FR-EVT-001/004 | UC-CLIN-001/002, UC-OPS-003 | Integration Delivery | happy path più partner down, duplicate, correction, tenant negative; lineage raw-to-receipt completo | `evidence/R0.2/M1/` demo manifest, golden diff, fault outcomes |
| WP1-08 Vertical slice B | ORU a canonical/FHIR con OMOP demo separata | FR-SEM-001/002/003/004, FR-OMOP-001/002/003, NFR-STD-002/003 | UC-CLIN-002, UC-DATA-001 | Clinical Informatics | unknown code non inventato; source/canonical/FHIR/OMOP ricostruibili; OMOP failure non tocca ACK | `evidence/R0.2/M2/` golden corpus, semantic review, FHIR validation, OMOP reconciliation |
| WP1-09 Operable MVP | UI operativa scoped e accessibile | FR-UI-001, FR-AUD-001, NFR-SEC-001, NFR-STD-004 | UC-OPS-003/007, UC-UI-01 | Product Engineering | least privilege; zero payload default; replay richiede motivo; keyboard/accessibility smoke | `evidence/R0.2/M3/` E2E UI, authorization matrix, accessibility report |
| WP1-10 Observable/auditable | audit append-only e telemetria senza PHI | FR-AUD-001/002/003, FR-OBS-001/002, NFR-AUD-001/002, NFR-MON-001/002 | UC-OPS-007, UC-SEC-02 | Security & Observability Engineering | audit chain valida; canary-PHI absent; outage backend contabilizzato; alert sintetici rilevati | `evidence/R0.2/M4/` audit verifier, DLP scan, alert and outage report |
| WP1-11 Recoverable environment | ambienti, backup/restore e runbook ripetibili | NFR-BC-001, NFR-DR-001/002, NFR-REL-004, NFR-OPS-001 | UC-OPS-002/004/006 | SRE | rebuild clean; restore DB/raw; checksum/count consistency; operator diverso dall'autore esegue runbook | `evidence/R0.2/M5/` environment validation, backup/restore drill, runbook transcript |
| WP1-12 Technical qualification | performance, fault e million-event proof | NFR-PERF-001/002/003/004/005, NFR-REL-001/002/003, NFR-SCL-001 | UC-REL-01/02, PC-01/02/07/08/10 | Quality & Performance Engineering | manifest R0.2; ≥1M eventi; p95/p99 target; zero unexplained loss/dupe; CP outage e fault matrix | `evidence/R0.2/G8/` signed manifest, raw series, reconciliation, qualification report |
| WP1-13 Release R0.2 | artifact riproducibile, firmato e limitato nei claim | NFR-SUPPLY-001, NFR-MNT-002, NFR-STD-005 | UC-OPS-006, UC-REL-03 | Release Engineering | clean build; SBOM/provenance/signature; install/upgrade/rollback; zero blocker; claim review | `evidence/R0.2/G9/` release bundle, attestations, known limitations, sign-off |

`NFR-SUPPLY-001` è un requisito di governance della baseline R0.1 già tracciato in `governance/traceability.yml`; se rinumerato nella requirements baseline, il change deve aggiornare entrambe le fonti prima di G9.

## 3. Test case trasversali baselined

| Test ID | Scenario sanitario 2026 | Oracle non negoziabile | Epic |
|---|---|---|---|
| UC-SEC-01 | operatore Tenant A tenta facility/endpoint di Tenant B | deny prima di leggere payload; security audit privo di PHI | WP1-01, WP1-09 |
| UC-REL-01 | crash in ciascun punto raw/ledger/ACK su ORU | nessun ACK falso; ogni ACK ha raw+ledger; retry converge | WP1-02, WP1-12 |
| UC-REL-02 | ACK MLLP/HTTP perso e tre retry concorrenti | un evento logico; receipt stabile; tentativi tutti auditabili | WP1-03 |
| UC-CON-01 | connector lento durante drain/upgrade | backpressure bounded; nessun thread/secret leak; stato riprendibile | WP1-04 |
| UC-CON-02 | HL7 malformed/oversized, charset errato, FHIR OperationOutcome | errore classificato; quarantine scoped; nessun crash/leakage | WP1-05 |
| UC-FLW-01 | bundle corrotto, fuori scope o N+2 | activation negata; previous-good resta attivo | WP1-06 |
| UC-UI-01 | clinical operator apre DLQ e chiede replay | metadata minimizzati; raw separato; reason e authorization obbligatori | WP1-09 |
| UC-SEC-02 | canary PHI in payload mentre collector/backend è offline | nessuna PHI nei segnali; perdita/buffer espliciti e recuperabili | WP1-10 |
| UC-REL-03 | upgrade N→N+1 fallisce dopo schema expand | rollback app sicuro; schema resta compatibile; nessun evento perso | WP1-11, WP1-13 |

I casi clinici di riferimento completi sono `UC-CLIN-001`, `UC-CLIN-002`, `UC-OPS-002`, `UC-OPS-003`, `UC-OPS-006` e `UC-OPS-007`. I casi normativi e di prodotto più ampi restano nel catalogo [personas and use cases](../product/personas-and-use-cases.md); WP1 non ne dichiara implementazione oltre i due vertical slice.

## 4. Regole evidence e change control

Ogni directory evidence contiene almeno `manifest.json`, `summary.json`, log redatti, raw measurements, artifact/commit digest e verdict. L'oracle vive fuori dal codice generato sottoposto a test. Finding Critical/High, mismatch di integrità, accesso cross-scope o ACK falso bloccano il gate senza eccezione automatica.

Una modifica a scope, owner, acceptance o dipendenza P0 aggiorna nello stesso change:

1. `phase-1-execution.md`;
2. `governance/phase-1-backlog.yml`;
3. `governance/traceability.yml`;
4. risk/hazard/privacy record interessati;
5. issue e milestone GitHub.

## 5. Baseline decision

Al 10 settembre 2026 tutti gli epic P0 hanno owner di ruolo, acceptance test e evidence target. I nominativi per reviewer indipendenti sono un requisito di staffing prima della review relativa, non una decisione architetturale aperta; l'assenza del reviewer al gate impedisce il gate stesso.
