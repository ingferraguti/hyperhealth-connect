# Qualification record — WP1-00 / Gate G1

Verdetto: **PASS**
Data di esecuzione: 11 settembre 2026
Release baseline: R0.2 Technical MVP
Owner: Program Engineering
Commit di ingresso R0.1: `eb69c16858e1b5b36d2b10c79d81264513ed79f6`
Evidence generata: `target/phase1-wp1-00-evidence/qualification-report.json`

## 1. Outcome

WP1-00 è completato. La Fase 1 dispone ora di backlog P0 tracciabile, owner e authority model, decisioni critiche di persistenza/ACK/idempotenza/bundle, baseline tecnologica portabile, manifest di performance e ambiente, risk/hazard/privacy review per i due vertical slice, milestone/issue tracker e dependency disposition.

Questo verdict autorizza l'avvio di WP1-01 e WP1-02 nei limiti della roadmap. Non autorizza dati reali, uso clinico, pilot o produzione e non anticipa i gate di qualification successivi.

## 2. Verifica prerequisito

La qualification R0.1 richiesta in ingresso è `SUCCESS`: GitHub Actions [run 34449013221](https://github.com/ingferraguti/hyperhealth-connect/actions/runs/34449013221), commit `eb69c16858e1b5b36d2b10c79d81264513ed79f6`, verificato l'11 settembre 2026.

## 3. Attività eseguite

| ID | Risultato | Evidenza |
|---|---|---|
| P1-0001 | DONE — 13 epic P0 mappati a requirement, use case, acceptance test ed evidence | [backlog](./phase-1-backlog.md), `governance/phase-1-backlog.yml` |
| P1-0002 | DONE — owner di ruolo, RACI, authority e staffing checkpoint definiti | [RACI](./phase-1-raci.md) |
| P1-0003 | DONE — workload, percentile, fault, oracle, hardware e topology reference fissati | [test manifest](../testing/phase-1-performance-test-manifest.yml), [environment manifest](../testing/phase-1-reference-environment.yml) |
| P1-0004 | DONE — PostgreSQL, S3 contract/SeaweedFS reference, Flyway, Keycloak e OTel/Prometheus/Jaeger selezionati | [ADR-025](../adr/ADR-025-phase-1-technology-baseline.md) |
| P1-0005 | DONE — raw/ledger transaction, durability point, ACK, idempotency, ordering e bundle locale chiusi | [ADR-026](../adr/ADR-026-durable-ingest-transaction-and-ack.md), [ADR-027](../adr/ADR-027-idempotency-and-ordering-contract.md), [ADR-028](../adr/ADR-028-signed-local-runtime-bundle.md) |
| P1-0006 | DONE — nove rischi R0.2, dieci hazard e mini-DPIA dei due vertical slice registrati | `governance/risks.yml`, [clinical safety](../security/clinical-safety-assurance.md), [privacy review](../security/data-protection-design-review.md) |
| P1-0007 | DONE — milestone, label set, 14 epic issue e dashboard creati | [milestone R0.2](https://github.com/ingferraguti/hyperhealth-connect/milestone/1), [dashboard](./phase-1-dashboard.md), `governance/phase-1-delivery-tracker.yml` |
| P1-0008 | DONE — versioni, digest OCI, licenze, tre ownership e update cadence registrati | `governance/dependencies.yml` |

## 4. Gate G1

| Criterio | Verifica | Esito |
|---|---|---|
| nessuna decisione critica aperta su persistenza e ACK | raw-first, ledger-second, durability point e failure matrix vincolati da ADR-026 | PASS |
| nessuna decisione critica aperta su tenancy | scope obbligatorio già vincolato da ADR-009 e propagato a key, ledger, bundle e test | PASS |
| nessuna decisione critica aperta su identity | confine OIDC, separazione human/workload e IdP di test fissati da ADR-009/025 | PASS |
| tutti gli epic P0 hanno owner | 13/13 epic WP1-01…WP1-13 hanno un owner accountable di ruolo | PASS |
| tutti gli epic P0 hanno acceptance test | 13/13 hanno suite/oracle ed evidence directory | PASS |
| nessun componente core senza security/license disposition | 9/9 componenti aggiunti o regolarizzati hanno versione, source, license, lifecycle/security/license owner, disposition e cadence; tutte le immagini hanno digest | PASS |

## 5. Controlli automatizzati

`scripts/phase1-wp1-00-gate.ps1` verifica dieci aree e fallisce al primo requisito mancante. Il controllo è aggiunto a GitHub Actions e genera un report JSON machine-readable. Il run locale dell'11 settembre 2026 ha prodotto:

1. deliverable inventory — PASS;
2. ADR-025…028 Accepted — PASS;
3. 13 epic P0 completi — PASS;
4. 9 dependency disposition complete — PASS;
5. tracciabilità critica R0.2 — PASS;
6. P1-R01…P1-R09 — PASS;
7. HZ-01…HZ-10 — PASS;
8. mini-DPIA vertical A/B — PASS;
9. performance/environment baseline — PASS;
10. milestone/14 epic tracker — PASS.

La regressione della fondazione è stata rieseguita l'11 settembre 2026: i quattro overlay Kustomize e il Compose manifest sono validi; `mvn clean verify` su Temurin 21.0.11 ha completato 7 moduli, 10 test, SpotBugs senza finding e CycloneDX SBOM con `BUILD SUCCESS`. La CA della workstation è stata aggiunta soltanto a un truststore locale temporaneo per superare l'ispezione TLS; nessuna chiave o CA privata entra nel repository o negli artifact.

## 6. Decisioni di scalabilità e continuità preservate

- Il Control Plane non è nel fast path e il bundle locale supporta autonomia progressiva da 4 ore R0.2 al target enterprise di 24 ore.
- Il raw store è un contratto S3-compatible, non coupling a un vendor; la produzione richiede qualification del servizio concreto.
- L'ACK non dipende da mapping, FHIR, OMOP o telemetry backend.
- Ordering e deduplica sono scoped, così un hot tenant/facility non impone serializzazione globale.
- Generator e verifier sono separati dal runtime; le prove conservano raw series e correggono coordinated omission.
- HA, backup, restore, fencing e topology failure domains sono requisiti del profilo qualification, non claim derivati dal Compose developer.

## 7. Limiti e azioni vincolanti successive

Il token GitHub corrente non possiede scope `read:project/project`; il board operativo è quindi implementato come milestone + epic issue + dashboard versionata. Non è un blocker G1: stato, owner, gate e URL hanno una fonte canonica machine-readable. Se sarà concesso lo scope, il tracker potrà essere proiettato in GitHub Projects senza cambiare backlog.

La modalità solo maintainer non soddisfa le review indipendenti dei gate successivi. Devono essere nominati security reviewer prima di G2, SRE/Clinical Safety prima di G3, clinical reviewer prima di M2, DPO/security assessor prima di M4, restore witness prima di M5 e performance oracle owner prima di G8. L'assenza blocca il relativo gate.

## 8. Fonti e verifica temporale

Versioni e capability sono state verificate su fonti upstream ufficiali il 10 settembre 2026; digest multi-arch sono stati risolti dai registry ufficiali nella stessa data. Le fonti sono elencate in ADR-025, nei manifest e in `governance/dependencies.yml`. La data di qualification è 11 settembre 2026.
