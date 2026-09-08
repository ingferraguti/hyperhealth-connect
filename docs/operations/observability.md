# Osservabilità

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | SRE/Observability Platform |
| Target | Telemetria continua multi-tenant e multi-facility privacy-safe |

## 1. Obiettivi

L'osservabilità deve rispondere: il servizio è disponibile? quali tenant/facility/flow sono impattati? dove si trova un evento? la durabilità e il delivery sono coerenti? il sistema recupererà prima dello SLO? La risposta usa metriche, trace, log strutturati, audit e ledger; nessun segnale da solo è source of truth.

## 2. Principi

- OpenTelemetry come formato/SDK/collector boundary, versioni pinnate;
- SLI e business invariant prima delle metriche infrastrutturali;
- correlation end-to-end senza patient identifier;
- cardinalità bounded e budget per signal;
- redaction alla sorgente/prima dell'export;
- collector/buffer resilienti per 24 ore entro capacity;
- osservabilità separata dall'audit di compliance;
- dashboard e alert as code, reviewati e testati;
- synthetic monitoring chiaramente marcato;
- raw evidence conservabile per incident e release test.

## 3. Identificativi

`trace_id`, `span_id`, `request_id`, `correlation_id`, `message_id`, `delivery_id`, `job_id`, `change_id`, `runtime_cell_id`, `flow_id/version`, `connector_id/version`. Tenant/facility sono attributi solo nei backend autorizzati; patient/MRN/accession non sono label/log. Cross-boundary propagation usa W3C Trace Context dove il protocollo lo consente; altrimenti mapping tecnico nell'Envelope.

## 4. Metriche golden e clinico-operative

| Categoria | Metriche |
|---|---|
| Traffic | events/requests/bytes per cell/flow/protocol |
| Errors | reject, parse, semantic, policy, delivery, unknown outcome |
| Latency | ingress→durable/ACK, step, delivery, end-to-end, freshness |
| Saturation | CPU, memory, heap, thread, connection, disk, I/O |
| Queue | depth, age oldest, lag, retry/DLQ, recovery rate |
| Integrity | accepted/delivered/pending/quarantined/mismatch |
| Availability | SLI per tier/facility e error-budget burn |
| Dependency | health/latency/circuit state per destination class |
| Security | auth deny, signature failure, privileged action |
| Data quality | invalid/unmapped/ambiguous/drift/DQD |
| DR | replica lag, backup age, restore test age, spool capacity |

Patient safety invariant (falso ACK, event loss, cross-tenant, semantic corruption) genera alert dedicato e non è ridotto a error rate aggregato.

## 5. Metric naming e cardinalità

Unità e tipo seguono semantic convention pinnata. Label ammesse sono enum/ID a cardinalità controllata. Endpoint raw URL, exception message, payload field e external arbitrary value non sono label. Un budget per service limita serie; cardinality overflow produce counter/alert e fallback aggregato, non outage del processo.

## 6. Trace

Span boundary: ingress, durability, parse/validate, mapping, route, egress, receipt, audit enqueue e job projection. Payload e PHI non entrano in attribute/event. Sampling head/tail preserva errori critici, slow trace e synthetic; decisione e versione sono registrate. Trace linkage asincrono usa link, non finge una singola chiamata sincrona.

## 7. Log strutturati

Campi minimi: timestamp UTC, severity, service/build, cell, event/error code, correlation, outcome e safe context. Messaggio umano è secondario al codice. Stack trace è backend ristretto e redatto. Vietati authorization header, secret, raw payload, SQL con valori, path contenente patient data e dump oggetto.

## 8. Audit

Audit record è append-only/tamper-evident con actor, action, resource, scope, purpose, policy decision, outcome e time. È inviato a journal durabile separato. L'export SIEM può fallire senza perdere il record locale; gap/hash/signature sono monitorati. Telemetry retention non determina audit retention.

## 9. Collector architecture

Agent/sidecar/node collector raccoglie localmente e applica resource attributes/redaction; gateway collector applica routing/batching/export. Config è firmata/versionata. Queue persistente e retry sono bounded; backpressure e disk watermark hanno alert. Backend multipli non ricevono automaticamente la stessa data class.

## 10. Dashboard standard

- executive/service tier SLO ed error budget;
- Runtime Cell/facility health e autonomy;
- connector/protocol, ACK e destination;
- queue/retry/DLQ/replay;
- event reconciliation e lineage gaps;
- Control Plane fleet desired/observed drift;
- semantic mapping/terminology/DQ;
- OMOP job/freshness/DQD;
- security/privacy/audit;
- backup/replica/DR readiness;
- deployment/canary version comparison.

Ogni dashboard dichiara owner, audience, datasource/query version, timezone, freshness e drill-down autorizzato.

## 11. Alert policy

Alert usa sintomo e impatto, non ogni eccezione. SLO burn multi-window per availability/latency; early warning per queue age, disk, certificate e replica lag. Severity:

- SEV1: patient safety, perdita, cross-tenant, Tier 0 ampio, audit integrity;
- SEV2: facility/Tier 0 degradato, recovery risk;
- SEV3: Tier 1/2 o capacity trend;
- SEV4: manutenzione/actionable backlog.

Ogni alert ha runbook, owner, routing, dedup/silence policy e synthetic test. Silence ha motivo, scope, approvatore ed expiry.

## 12. Message Explorer

Mostra timeline tecnica e lineage: ricevuto, durable, parsed, mapped, routed, delivered/quarantined. Default senza payload; raw view richiede privilege/purpose/step-up ed è auditata. Ricerca usa ID tecnici; eventuale ricerca paziente è capability separata, scoped e non esporta risultati massivi.

## 13. Monitoring sintetico

Eventi identificabili `SYNTHETIC-NOT-FOR-CARE`, namespace e destinatari test. Coprono ingress→durability→delivery→audit e Control Plane API. Non contaminano sistemi clinici/dataset. Missing synthetic, unexpected real destination o ritardo oltre soglia allerta.

## 14. SLO reporting

Report mensile per tier e facility mostra numerator/denominator, exclusion contrattuali, burn, incident, dependency e data completeness. Tier 0 target 99,99%, Tier 1 99,95%, Tier 2/Control Plane 99,9%. Maintenance non è esclusa automaticamente. Telemetry gap rende il periodo `UNKNOWN`, non disponibile per dimostrare successo.

## 15. Casi d'uso

### OBS-01 — Risultato laboratorio in ritardo

Trace/ledger mostrano ACK durable, mapping riuscito, circuit aperto e queue age. Nessun payload nei log; operator identifica facility/destination e avvia runbook.

### OBS-02 — Noisy tenant

Quota/saturation evidenziano tenant A a 5×; SLO tenant B resta verde. Cardinalità non esplode e security rileva abuse se applicabile.

### OBS-03 — Collector offline

Buffer locale cresce, early warning scatta e servizio clinico continua. Al recupero, export riprende e gap detection conferma completezza.

### OBS-04 — Mapping drift

Canary release aumenta `unmapped` per versione. Rollout si arresta e lineage identifica mapping/terminology snapshot.

### OBS-05 — Audit tamper

Hash chain/signature verification fallisce; alert SEV1 preserva evidence e blocca operazioni privilegiate secondo policy.

## 16. Test e gate

- correlation coverage su casi E2E;
- zero canary PHI nei canali vietati;
- cardinality/load test;
- 24 ore backend offline entro capacity;
- alert→paging→ack→runbook test;
- dashboard/query versionate e reviewate;
- audit gap/tamper e clock skew test;
- SLO calculation replayabile dai raw data.

## 17. Fonti

Verificate il **5 settembre 2026**:

- [OpenTelemetry Specification 1.60.0](https://opentelemetry.io/docs/specs/otel/);
- [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/);
- [W3C Trace Context](https://www.w3.org/TR/trace-context/);
- [Prometheus — Instrumentation practices](https://prometheus.io/docs/practices/instrumentation/);
- [Google SRE — Monitoring distributed systems](https://sre.google/sre-book/monitoring-distributed-systems/);
- [EHDS, Regolamento (UE) 2025/327](https://eur-lex.europa.eu/eli/reg/2025/327/oj/), incluse capability di logging quando applicabili.

## 18. Collegamenti

- [Reliability model](./reliability-and-error-model.md)
- [Performance e chaos](../testing/performance-and-chaos.md)
- [Privacy](../security/privacy-and-data-protection.md)
- [Supporto e runbook](./support-and-runbooks.md)
