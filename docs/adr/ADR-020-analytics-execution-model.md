# ADR-020 — Modello di esecuzione analytics

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Analytics Platform e Security

## Contesto

Query e cohort job possono saturare database e produrre risultati sensibili. Devono essere ripetibili e non interferire con l'assistenza.

## Decisione

Analytics usa job asincroni dichiarativi con dataset version, purpose, owner, query/cohort definition version, resource budget, deadline e output policy. Compute, work schema e connection pool sono separati dal Tier 0. I consumer ordinari leggono dataset `READY`; non mutano CDM.

Scheduler applica quota/fairness per tenant e priorità inferiore al clinico. Risultati sono versionati, cifrati, minimizzati e sottoposti a disclosure control. Query libera è limitata a workspace autorizzati; agenti usano primitive tipizzate del Governed AI Gateway.

## Conseguenze

- cancellazione/checkpoint e cost attribution;
- audit di input, query, versione e accesso al risultato;
- OMOP rebuild e query non bloccano ACK;
- risultati possono scadere o essere revocati;
- monitoring di queue age e budget.

## Alternative respinte

- query sincrone illimitate: noisy-neighbour;
- analytics nel Platform DB: failure coupling;
- file risultato non governati: leakage e non riproducibilità.

## Collegamenti

- [Analytics API](../api/analytics-api.md)
- [OHDSI compatibility](../omop/analytics-and-ohdsi-compatibility.md)
- [ADR-021](./ADR-021-governed-ai-access.md)
