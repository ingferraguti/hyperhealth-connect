# ADR-017 — Replay e reprocessing deterministico

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Runtime e Data Governance

## Contesto

“Replay” può significare ritentare una consegna o ricalcolare anni di dati. Trattarlo come un'unica operazione espone a duplicati, side effect clinici e risultati non riproducibili.

## Decisione

HHC distingue `delivery replay`, `transformation replay`, `semantic reprocessing` e `OMOP rebuild`. Ogni job dichiara input snapshot, versioni, scope, mode dry-run/execute, side-effect policy, idempotency strategy, quota, owner e approvazione.

Il raw è immutabile; l'output ha nuovo lineage e non sovrascrive quello storico. Delivery verso sistemi clinici richiede receipt/dedup e preview del blast radius. Semantic/OMOP job usano mapping e vocabulary pinnati. Live traffic ha priorità e risorse separate.

## Conseguenze

- UI/API non offrono un generico pulsante “replay”;
- pause/cancel/resume e checkpoint sono obbligatori;
- audit contiene motivo, selezione, conteggi e outcome;
- reconciliation determina completion, non il solo stato del job;
- reprocessing può essere approvato per facility senza coinvolgere altre.

## Alternative respinte

- reinvio cieco dalla DLQ: duplicati e ordine errato;
- update in-place degli output: lineage perso;
- job illimitato concorrente al live: rischio SLO.

## Collegamenti

- [Replay e reprocessing](../operations/replay-and-reprocessing.md)
- [Reliability model](../operations/reliability-and-error-model.md)
- [ADR-004](./ADR-004-immutable-raw-events.md)
