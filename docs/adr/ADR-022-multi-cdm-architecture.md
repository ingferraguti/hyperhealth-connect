# ADR-022 — Architettura multi-CDM

Stato: Accepted  
Data: 5 settembre 2026  
Owner: OMOP Platform e Data Governance

## Contesto

Gruppi sanitari possono richiedere dataset separati per azienda, regione, purpose, residency o ricerca. Un CDM globale non garantisce sempre isolamento e governance.

## Decisione

HHC supporta più Dataset Instance, ciascuna con immutable ID, tenant/organization/facility scope, purpose, residency, CDM/vocabulary version, key domain e lifecycle. L'isolamento può essere schema, database o deployment dedicato secondo rischio; la scelta è policy-driven e non richiede fork.

Cross-dataset analysis usa workspace/federation autorizzata e dati minimizzati, non join impliciti. Person ID e token non sono globali per default. Registry centrale conserva solo metadata permessi e stato, non record clinici.

## Conseguenze

- maggiore costo di provisioning e fleet management;
- backup/restore e DQD per dataset;
- quota e noisy-neighbour isolation;
- publication alias atomico per ogni scope;
- cancellazione o legal hold indipendenti.

## Alternative respinte

- unico CDM con colonna tenant: blast radius elevato;
- deployment custom per cliente: divergenza del prodotto;
- token persona globale: correlabilità non necessaria.

## Collegamenti

- [OMOP architecture](../omop/omop-architecture.md)
- [Tenancy](../security/tenancy-and-authorization.md)
- [Deployment topologies](../deployment/deployment-topologies.md)
