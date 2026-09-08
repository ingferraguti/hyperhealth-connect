# Modello di affidabilità ed errori

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Runtime Engineering e SRE |

## 1. Principio

HHC garantisce at-least-once transport dove dichiarato, idempotent processing, deduplica e riconciliazione. Non promette exactly-once end-to-end generico con sistemi legacy. Un esito sconosciuto resta `UNKNOWN`; non viene tradotto in successo o retry immediato senza policy.

## 2. Stato evento

```text
RECEIVED → DURABLE → PROCESSING → DELIVERED
                 ↘ RETRY_WAIT ↗
                 ↘ QUARANTINED
                 ↘ DLQ
                 ↘ UNKNOWN → RECONCILED_{DELIVERED|NOT_DELIVERED}
```

Stati terminali dipendono dalla completion policy multi-destination. Raw reception, ACK al source, transformation e delivery sono eventi distinti.

## 3. Durability/ACK

`ACK_ON_RECEIVE` è ammesso solo se il contratto accetta perdita pre-durability. `ACK_ON_DURABLE` richiede raw/envelope/ledger persistiti nel failure domain dichiarato. Tier 0 usa RPO locale 0 dopo ACK. Crash prima/dopo commit e risposta persa sono testati.

## 4. Error taxonomy

| Categoria | Esempio | Retry default |
|---|---|---|
| TRANSPORT_TRANSIENT | timeout connect/reset | sì, bounded |
| DEPENDENCY_UNAVAILABLE | 503/broker leader | sì con circuit |
| THROTTLED | 429/quota | `Retry-After`/policy |
| AUTHENTICATION | token/cert invalid | no fino a refresh/rotation |
| AUTHORIZATION | scope/purpose deny | no; security event |
| INVALID_SYNTAX | framing/schema | no; quarantine |
| PROFILE_VIOLATION | cardinalità/binding | no o workflow manuale |
| SEMANTIC_AMBIGUOUS | mapping non univoco | no; steward review |
| BUSINESS_REJECTED | partner rifiuta valore | secondo contract |
| CONFLICT | version/idempotency | reconcile |
| RESOURCE_EXHAUSTED | disk/queue/memory | backpressure, non loop |
| INTEGRITY | checksum/signature/audit gap | stop/escalate |
| UNKNOWN_OUTCOME | response persa dopo send | reconcile prima del retry |
| INTERNAL_DEFECT | invariant/uncaught | stop/retry limitato e defect |

Codice normalizzato conserva protocol-native code e raw response reference autorizzato.

## 5. Timeout budget

Deadline end-to-end è propagata e suddivisa fra queue, connect, request, processing e response. Timeout di un layer non eccede il budget caller. Nessun timeout infinito. Aumentare timeout richiede capacity/failure analysis; può aggravare thread/connection exhaustion.

## 6. Retry

Policy per operation/destination: errori ammessi, max attempt/time, exponential backoff, jitter, per-attempt timeout e budget. Retry è idempotente o protetto da key/receipt. `Retry-After` è rispettato entro limite. Retry storm è contenuto con quota e circuit breaker. Attempt history è nel ledger.

## 7. Idempotenza

Key deriva da tenant/facility/source/message/business scope e operation version. Collisione è rilevata confrontando digest. La stessa key con payload diverso è integrity conflict. Dedup window è maggiore del retry/replay horizon dichiarato. Idempotenza locale non garantisce side effect unico in un partner che non la supporta: si usa receipt/reconciliation/manual workflow.

## 8. Ordering

Ordering è garantito solo entro partition key e scope dichiarati (es. paziente/ordine). Global ordering è evitato. Retry di un evento può bloccare la key ma non tutte le altre; policy decide skip/quarantine solo se clinicamente sicuro. Repartition è migration compatibile e testata.

## 9. Circuit breaker e bulkhead

Circuit per destination/operation, non globale. Stati closed/open/half-open, threshold e probe sono osservabili. Bulkhead separa tenant, tier, connector e dependency. Apertura protegge risorse e alimenta queue/backpressure; non converte delivery in successo.

## 10. Backpressure

Propaga dal downstream a worker/queue/ingress secondo protocollo. Watermark su queue/spool/disk attiva throttle, riduce replay/analytics e infine rifiuta prima dell'ACK se non può garantire durabilità. Buffer infinito è vietato.

## 11. DLQ e quarantine

Quarantine per input valido da conservare ma non processabile in sicurezza; DLQ per delivery/processing esaurito secondo policy. Entrambe hanno owner, reason code, retention, alert, search redatta e workflow. Nessun auto-purge di evento Tier 0. Re-drive usa replay governato.

## 12. Multi-destination

Ogni destination ha ledger indipendente. Completion policy (`ALL`, `REQUIRED_SET`, `ANY`, custom approved) è dichiarata. Successo parziale non è successo globale quando `ALL`. Retry non ripete destinazioni già completate se non richiesto.

## 13. Reconciliation

Confronta source accepted, raw, ledger, partner receipt/query, destination e quarantine. Esito: matched, missing, duplicate, conflict o unverifiable. È periodica e on-demand dopo incident. `UNVERIFIABLE` resta visibile e ha escalation.

## 14. Error contract API

API usa Problem Details RFC 9457 con stable HHC code, status, safe detail, request/correlation, retryability e field errors. Nessun stack/PHI. Codici non vengono riutilizzati con semantica diversa. HTTP status non sostituisce business outcome.

## 15. Protocol mapping

- HL7 v2: transport/framing distinto da `AA/AE/AR` e enhanced `CA/CE/CR`;
- FHIR: HTTP, OperationOutcome e business state conservati;
- DICOM: association, DIMSE status, storage commitment distinti;
- file/SFTP: upload atomico, stable file, receipt/archive;
- messaging: broker ACK distinto da consumer business completion;
- JDBC: commit DB distinto da downstream workflow completion.

## 16. Observability

Metriche per error code/category, attempt, circuit, queue age, unknown outcome, DLQ/quarantine e reconciliation mismatch. Cardinalità non include messaggio/paziente. Alert severity combina tier, patient safety, scope, duration e recovery risk.

## 17. Casi d'uso

### REL-01 — Risposta persa

Partner applica side effect ma la response non arriva. Stato `UNKNOWN`; HHC usa idempotency/receipt query prima del retry, evitando doppio ordine.

### REL-02 — Crash dopo commit

Evento durable, ACK non inviato. Source ritrasmette; digest/key deduplica e restituisce outcome coerente.

### REL-03 — Partner lento

Circuit e bulkhead isolano destination; queue bounded e backpressure proteggono le altre facility.

### REL-04 — Payload diverso con stessa ID

Collisione produce integrity conflict e quarantine, non dedup silenzioso.

### REL-05 — Fan-out parziale

Due destinazioni riuscite e una fallita con policy `ALL`. Stato globale resta incomplete; retry riguarda la terza e audit mostra dettaglio.

### REL-06 — Disk spool critico

Replay/analytics si fermano, alert scatta e ingress applica errore protocollo prima di un falso ACK.

## 18. Gate

- zero loss/reconciliation mismatch non spiegato;
- crash matrix prima/dopo ogni durability point;
- unknown outcome non tradotto automaticamente;
- retry budget e idempotency testati;
- noisy destination isolata;
- DLQ/quarantine con owner e retention;
- mapping errori protocollari qualificato;
- dashboard e runbook presenti.

## 19. Fonti

Verificate il **5 settembre 2026**:

- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html);
- [AWS Builders' Library — Timeouts, retries and backoff with jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/);
- [HL7 v2 standard product](https://www.hl7.org/implement/standards/product_brief.cfm?product_id=185);
- [FHIR OperationOutcome](https://hl7.org/fhir/operationoutcome.html);
- [DICOM current standard](https://www.dicomstandard.org/current).

## 20. Collegamenti

- [Replay](./replay-and-reprocessing.md)
- [Observability](./observability.md)
- [Connector SDK](../connectors/connector-sdk.md)
- [ADR-006](../adr/ADR-006-sync-async-dual-path.md)
