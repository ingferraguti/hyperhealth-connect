# Replay e reprocessing

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Runtime Operations e Data Governance |

## 1. Finalità

Replay è un'operazione ad alto impatto. Questa policy impedisce che un semplice “re-drive” generi duplicati clinici, modifichi lo storico o saturi sistemi durante il recupero.

## 2. Tipi

| Tipo | Input | Output/side effect |
|---|---|---|
| Delivery replay | output già trasformato/versionato | nuova attempt verso destination |
| Transformation replay | raw/parsed snapshot | nuovo derivato con flow version |
| Semantic reprocessing | CSE/raw e mapping/vocabulary | nuova derivazione semantica |
| OMOP rebuild | input snapshot/watermark | nuova Dataset Version |

Il tipo è immutabile dopo creazione del job. Non esiste operazione generica “replay tutto”.

## 3. Manifest del job

```yaml
jobId: replay-...
type: DELIVERY_REPLAY
scope: {tenant: ..., facility: ..., flow: ...}
selection: {timeRange: ..., states: [DLQ]}
inputSnapshot: immutable-ref
versions: {flow: ..., mapping: ..., terminology: ...}
mode: DRY_RUN
sideEffects: DECLARED_DESTINATIONS_ONLY
idempotencyPolicy: ...
rateBudget: ...
owner: ...
approvals: [...]
reason: incident/change/correction
```

## 4. Authorization

Create, preview, approve, execute, pause, cancel e view payload sono azioni separate. Delivery verso sistemi clinici e semantic reprocessing A3 richiedono four-eyes secondo rischio. Scope/purpose/TTL e step-up sono obbligatori. Agenti possono preparare manifest/test, non approvare o eseguire su produzione.

## 5. Selection e preview

Selezione usa ID/stato/time range/versione e query bounded. Preview mostra conteggio, byte, tenant/facility, destination, side effect, collisioni, capacity e stima durata senza esporre payload. Campione redatto è opzionale. Il set selezionato viene congelato/content-addressed prima dell'esecuzione.

## 6. Determinismo

Transformation/semantic job fissa raw, software build, config, mapping, terminology, locale, timezone, clock policy e seed. External lookup non versionabile rende il risultato non qualificabile o richiede snapshot. Output include diff dal precedente e lineage completo.

## 7. Idempotenza e side effect

Delivery replay consulta ledger/receipt e non ritenta esiti `UNKNOWN` senza reconciliation. Idempotency key non cambia per mascherare duplicate salvo nuovo business event esplicito. Email/notifica/ordine/dispensazione sono side effect ad alto rischio con allowlist e dry-run obbligatorio.

## 8. Scheduling e capacity

Live Tier 0 prevale. Job ha rate/concurrency/I/O quota, maintenance window, downstream limit e abort su SLO burn, queue age o error rate. Scheduler garantisce fairness e checkpoint. Nessun job può occupare tutto il pool o storage.

## 9. Lifecycle

`DRAFT → VALIDATED → APPROVED → SCHEDULED → RUNNING → PAUSED/COMPLETED/FAILED/CANCELLED → RECONCILED → CLOSED`.

`COMPLETED` indica fine tecnica; `RECONCILED` prova conteggi e side effect. Resume parte da checkpoint e verifica manifest/lease. Cancellation è cooperativa e lascia stato consistente.

## 10. Output e storico

Output è nuova versione/attempt. Non sovrascrive raw, CSE storico, dataset READY o audit. Consumer può scegliere current/superseded secondo contratto. Correzione clinica segue amendment/correction del protocollo, non delete nascosto.

## 11. Failure handling

- crash worker: lease scade e checkpoint riprende;
- destination outage: circuit/retry bounded;
- mapping missing: quarantine, non default;
- policy revocata: pause/stop;
- version artifact mancante: fail closed;
- disk/capacity threshold: pause e alert;
- mismatch: job non chiude;
- operator cancel: audit e partial-result manifest.

## 12. Audit ed evidence

Reason, requester, approver, query hash, frozen selection, versioni, preview, start/stop, attempt, output, error, reconciliation e decisione finale. Evidence firmata e retention per policy. Raw access resta audit separato.

## 13. Reconciliation

Per delivery: selected = skipped-already-delivered + delivered + pending + failed/quarantined + unknown; ogni differenza è spiegata. Per transform/semantic: input = success + quarantine + deterministic exclusion. Per OMOP: source watermark/count/digest riconciliati con staging/CDM e DQ report.

## 14. UI/API safety

- default dry-run;
- scope e consequence summary prominenti;
- digitazione/second approval per bulk production;
- no wildcard globale senza policy;
- pause/abort visibili;
- progress con conteggi, non solo percentuale;
- stale preview invalida approval;
- accessible confirmation e keyboard workflow.

## 15. Casi d'uso

### RPL-01 — Downstream fermo due ore

Delivery replay smaltisce backlog con quota separata; live resta prioritario, receipt evita duplicate e job chiude solo dopo reconciliation.

### RPL-02 — Bug di trasformazione

Raw snapshot viene ricalcolato con flow corretto. Nuovi output sono comparati e promossi; quelli precedenti restano superseded con lineage.

### RPL-03 — Nuova terminologia

Impact preview identifica record coinvolti. Nessun remap retroattivo automatico; nuova semantic derivation usa snapshot pinnato e review clinica.

### RPL-04 — OMOP rebuild

Nuova Dataset Version è costruita in parallelo, DQD/gate superati e alias cambiato atomicamente. Tier 0 non è impattato.

### RPL-05 — Esito partner sconosciuto

La response era persa. Il job interroga receipt/reconcile e non reinvia finché lo stato non è risolto o approvato manualmente.

## 16. Gate

- frozen selection e dry-run;
- autorizzazioni/SoD corrette;
- versioni e artifact disponibili;
- idempotency e capacity testate;
- zero SLO breach Tier 0;
- reconciliation senza mismatch;
- lineage/audit completi;
- rollback/stop provati.

## 17. Collegamenti

- [ADR-017](../adr/ADR-017-replay-and-reprocessing.md)
- [Reliability](./reliability-and-error-model.md)
- [Data flow](../architecture/data-flow.md)
- [OMOP ETL](../omop/omop-projection-and-etl.md)

## 18. Fonti ufficiali

Consultate o riconfermate il 5 settembre 2026:

- [NIST SP 800-92 Guide to Computer Security Log Management](https://csrc.nist.gov/pubs/sp/800/92/final)
- [NIST SP 800-184 Guide for Cybersecurity Event Recovery](https://csrc.nist.gov/pubs/sp/800/184/final)
- [OpenTelemetry specification](https://opentelemetry.io/docs/specs/otel/)
