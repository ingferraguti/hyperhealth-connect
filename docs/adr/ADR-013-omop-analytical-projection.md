# ADR-013 — OMOP come proiezione analitica

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Data & Analytics Architecture

## Contesto

OMOP CDM è ottimizzato per analytics osservazionale, non per orchestrare ACK, routing o stato operativo in tempo reale.

## Decisione

OMOP è una proiezione asincrona, governata e ricostruibile dal raw/CSE, mai source of truth del workflow clinico. Projection Engine produce staging; Dataset Builder materializza una versione; Data Quality Gate promuove atomicamente `READY`.

La pipeline clinica non attende OMOP. Identity token, visit linkage, concept mapping, vocabulary e source values sono versionati. Correzione genera nuova dataset version o processo incrementale dichiarato, non update opaco.

## Conseguenze

- outage analytics non riduce disponibilità Tier 0;
- dataset può essere ricostruito e confrontato;
- latenza/freshness è SLO distinto;
- accesso analytics ha purpose e disclosure control;
- storage e compute OMOP sono isolati dal Platform DB.

## Alternative respinte

- OMOP come modello operativo: semantica e workload inadatti;
- ETL nel fast path: latenza e failure coupling;
- scrittura diretta in dataset pubblicato: impossibile gate atomico.

## Collegamenti

- [OMOP architecture](../omop/omop-architecture.md)
- [Projection ed ETL](../omop/omop-projection-and-etl.md)
- [Data quality gate](./ADR-024-data-quality-release-gate.md)
