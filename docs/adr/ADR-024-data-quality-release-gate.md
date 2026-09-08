# ADR-024 — Data quality come release gate

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Data Quality e Analytics Governance

## Contesto

Uno schema caricato con successo può contenere mapping errati, record mancanti o distribuzioni implausibili. Pubblicarlo espone analisi e decisioni a dati non qualificati.

## Decisione

Ogni Dataset Version attraversa stati `BUILDING → VALIDATING → READY` oppure `FAILED/QUARANTINED`; `SUPERSEDED/ARCHIVED` gestiscono il lifecycle. Publication alias cambia solo dopo structural check, reconciliation, rule pack HHC, OHDSI DQD qualificato, plausibility/semantic diff e approvazioni richieste.

Regole hanno ID, severity, scope, threshold, owner e versione. Fatal non è derogabile per perdita, cross-tenant, key integrity o corruzione semantica critica. Eccezione non critica ha evidenza, rischio, compensazione e scadenza. Il gate conserva raw result e manifest.

## Conseguenze

- consumer vedono solo versioni immutabili `READY`;
- build fallita non altera dataset corrente;
- quality trend e drift sono monitorati;
- rebuild è riproducibile da input/versioni;
- DQD è componente della prova, non unico oracle.

## Alternative respinte

- pubblicazione e correzione successiva: rischio non controllato;
- soli vincoli DB: non coprono semantica/plausibilità;
- waiver illimitati: debt invisibile.

## Collegamenti

- [OMOP projection ed ETL](../omop/omop-projection-and-etl.md)
- [Test di conformità](../testing/conformance-and-interoperability.md)
- [ADR-013](./ADR-013-omop-analytical-projection.md)
