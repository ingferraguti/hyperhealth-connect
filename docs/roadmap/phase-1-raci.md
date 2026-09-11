# RACI e authority model — Fase 1 R0.2

Stato: BASELINED
Data: 10 settembre 2026
Accountable complessivo: Program Engineering
Staffing mode corrente: solo maintainer con funzioni indipendenti da nominare prima dei gate applicabili

## Regola organizzativa

Gli owner sono ruoli stabili, non nomi incorporati nell'architettura. Il maintainer corrente può ricoprire Responsible e Accountable nelle attività reversibili, ma non può autocertificare penetration test, review clinica di mapping ad alto rischio, DPIA o risk acceptance critica. Le caselle `Independent reviewer` sono obblighi di gate: prima della review il nominativo, organizzazione, conflitto di interessi e mandato sono registrati nell'evidence pack.

R = esegue; A = risponde dell'outcome e decide; C = consultato prima della decisione; I = informato. Una sola A per riga.

## Matrice

| Ambito | A | R | C | I | Independent reviewer richiesto |
|---|---|---|---|---|---|
| scope, budget, priorità e change control | Program Engineering | Product/Technical Lead | SRE, Security, Clinical Informatics | stakeholder | no |
| tenancy, API e Platform DB | Platform Engineering Lead | Backend Engineering | Security, Data Architecture, SRE | QE | security per G2 |
| raw, ledger, durability e ACK | Runtime Architecture Lead | Runtime Engineering | SRE, Data, Connector, Clinical Safety | Product | SRE + Clinical Safety per G3 |
| idempotenza, retry, DLQ, reconciliation | Runtime Architecture Lead | Runtime Engineering | Connector, SRE, Clinical Informatics | Support | QE/SRE per G4 |
| SDK e connector | Integration Architecture Lead | Connector Engineering | Security, Runtime, Clinical Informatics | Product | protocol specialist per G5/G6 |
| flow, mapping e bundle | Release/Runtime Lead | Runtime + Mapping Engineering | Security, Clinical Informatics, SRE | Support | Security per signature; Clinical per high-risk mapping |
| canonical, FHIR e OMOP demo | Clinical Informatics Lead | Semantic Engineering | Data Protection, FHIR/OMOP specialists | Product | Clinical Safety Officer per M2 |
| UI operativa | Product Engineering Lead | Frontend/Backend Engineering | Security, Accessibility, Operators | Product | accessibility specialist per M3 |
| identity, audit e privacy | Product Security Lead | Security Engineering | DPO, IAM, SRE, Clinical Safety | Program | independent security assessor per M4/G8 |
| observability e alert | SRE Lead | Observability Engineering | Security, Support, Performance | Program | operator witness per M4 |
| backup, restore, BC/DR | Business Continuity Owner | SRE/Data Engineering | Security, DPO, Runtime | Executive | recovery witness diverso dall'autore per M5 |
| performance e fault qualification | Quality Engineering Lead | Performance Engineering/SRE | Architecture, Security, Clinical Safety | Program | oracle owner diverso dall'implementer per G8 |
| release e supply chain | Release Engineering Lead | Release Engineering | Security, Legal/OSPO, Architecture | Users | Security/OSPO per G9 |
| claim sanitario/regolatorio | Product Owner | Regulatory Affairs | Clinical Safety, Legal, DPO | Sales/Support | regulatory reviewer prima di pilot/production |

## Authority e segregation of duties

- Product Security può bloccare un release artifact per vulnerability, provenance, secret o tenant isolation.
- Clinical Safety Officer può bloccare mapping/flow per rischio di attribuzione, omissione, ritardo o alterazione semantica.
- DPO può bloccare un flusso/dataset per base, minimizzazione, residenza, retention o trasferimento non risolti.
- SRE può bloccare una promozione se durability, capacity, restore o operabilità non sono dimostrati.
- Release Engineering non può derogare autonomamente ai blocchi precedenti.
- L'agente di coding non è A né independent reviewer; ogni contributo agentico mantiene prompt/provenance, diff review e test secondo la policy.

## Staffing checkpoints

| Entro | Ruolo da nominare | Evidenza | Conseguenza se assente |
|---|---|---|---|
| prima di G2 | security reviewer tenancy/IAM | review record e test sign-off | G2 bloccato |
| prima di G3 | SRE witness e Clinical Safety Officer | crash-matrix/hazard sign-off | G3 bloccato |
| prima di M2 | clinical informatics reviewer indipendente | golden corpus approval | M2 bloccato |
| prima di M4 | DPO e security assessor | privacy/audit disposition | M4 bloccato |
| prima di M5 | restore operator diverso dall'autore | drill transcript | M5 bloccato |
| prima di G8 | performance oracle owner e security assessor | qualification signatures | G8 bloccato |
| prima di G9 | OSPO/legal reviewer | dependency/license disposition | G9 bloccato |

## Modalità solo maintainer

Il maintainer è temporaneamente R/A operativo per Program, Architecture e Engineering. I ruoli indipendenti restano deliberatamente non autoassegnati. Questa configurazione consente di iniziare il build dopo G1 perché decisioni, owner di ruolo e acceptance oracle sono chiusi; non consente di superare i gate che richiedono indipendenza finché lo staffing checkpoint non è soddisfatto.
