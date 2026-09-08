# ADR-016 — Lineage e provenance semantica

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Data Governance

## Contesto

In sanità non basta conoscere il valore finale: occorre spiegare origine, trasformazioni, mapping, versioni, approvazioni e destinazioni senza duplicare PHI nell'audit.

## Decisione

Il lineage è un grafo append-only di riferimenti tra raw, parsed, canonical, transformed, delivery e projection. Ogni edge registra operation/rule, software build, mapping/terminology version, timestamp, actor/workload, scope e outcome. Il payload resta nello store appropriato.

Correzione aggiunge derivazione e relazione `supersedes/corrects`; non riscrive la storia. Dataset e output espongono riferimenti fino agli input autorizzati. Hash chaining/signature batch protegge gli eventi critici e gap detection verifica completezza.

## Conseguenze

- forensic, replay e impatto diventano interrogabili;
- volume richiede retention/tiering e indici controllati;
- accesso lineage è autorizzato perché metadata possono essere sensibili;
- deletion del payload mantiene tombstone minimo conforme;
- correlation ID non sostituisce provenance semantica.

## Alternative respinte

- log testuali: incompleti e mutabili;
- copia payload in ogni step: minimizzazione e costo;
- solo source/target finale: impossibile spiegare regole intermedie.

## Collegamenti

- [Lineage e provenance](../semantic/lineage-and-provenance.md)
- [Integration Envelope](../canonical-model/integration-envelope.md)
- [Osservabilità](../operations/observability.md)
