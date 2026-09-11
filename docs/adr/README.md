# Architecture Decision Records

Stato: baseline 1.1 — WP1-00
Ultimo aggiornamento: 10 settembre 2026
Owner: Architecture Governance

Gli ADR 001–028 sono decisioni `Accepted` della baseline enterprise. `Accepted` significa vincolante per l'implementazione fino a sostituzione, non immutabile. Una modifica strutturale crea un nuovo ADR con relazione `Supersedes/Superseded by`; non riscrive la motivazione storica.

## Stati

- `Proposed`: in valutazione e non vincolante;
- `Accepted`: baseline approvata;
- `Deprecated`: ancora presente ma non raccomandata;
- `Superseded`: sostituita da ADR indicato;
- `Rejected`: valutata e non adottata.

## Processo

Ogni ADR collega contesto, decisione, alternative, conseguenze, owner e documenti attuativi. Architecture Governance verifica impatto su requisiti, threat model, dati, API, compatibilità, costi, operazioni e test. Decisioni A3 richiedono review delle discipline coinvolte. L'implementazione non è `DONE` finché i gate citati dall'ADR non producono evidence.

## Catalogo

- ADR-001–010: piattaforma, runtime, connector, dati, identity e versioning;
- ADR-011–018: canonical model, semantica, OMOP, lineage, replay e privacy;
- ADR-019–024: OHDSI, analytics, AI governata, multi-CDM, retention e data quality.
- ADR-025: baseline tecnologica R0.2 e confini di portabilità;
- ADR-026: transazione raw/ledger, durability point e ACK;
- ADR-027: idempotenza, duplicati e ordering scope;
- ADR-028: bundle locale firmato e autonomia della Runtime Cell.

La numerazione non viene riutilizzata e i link locali sono verificati in CI.
