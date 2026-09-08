# ADR-008 — Separazione storage e policy PHI nei log

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Security, Privacy e Data Architecture

## Contesto

Metadata operativi, payload clinici, audit e analytics hanno requisiti diversi. Un database o log store comune aumenterebbe blast radius, retention coupling e accesso improprio.

## Decisione

Platform metadata, raw object, delivery ledger/spool, audit vault, telemetry e OMOP usano store logicamente separati e, secondo rischio, fisicamente separati. Ogni store ha schema, chiave, backup, retention, access policy e SLO propri.

Log, metriche e trace sono PHI-free per default. Patient ID, payload, free text e token non sono label o messaggi diagnostici. Redaction avviene prima dell'export; support bundle usa allowlist. Accesso eccezionale al payload avviene via Message Explorer governato, purpose, audit e time-bound authorization.

## Conseguenze

- correlation usa identificativi tecnici opachi;
- audit non è affidato ai normali application log;
- backup e cancellazione sono per data class;
- canary sintetici verificano leakage continuo;
- observability resta utile mediante error code strutturati e lineage reference.

## Alternative respinte

- payload completo nei log per troubleshooting: rischio sproporzionato;
- unico datastore enterprise: failure e privilege domain troppo ampi;
- redaction solo nel backend: dati già esposti nel trasporto/buffer.

## Collegamenti

- [Privacy e protezione dati](../security/privacy-and-data-protection.md)
- [Osservabilità](../operations/observability.md)
- [Data architecture](../architecture/data-architecture.md)
