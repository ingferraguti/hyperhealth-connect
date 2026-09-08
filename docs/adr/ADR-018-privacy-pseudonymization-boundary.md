# ADR-018 — Boundary di privacy e pseudonimizzazione

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Privacy Architecture e DPO delegate

## Contesto

Pseudonimizzare troppo tardi espone dati; farlo prima di un workflow assistenziale può impedire matching e cura. La stessa persona non deve essere correlabile tra tenant o purpose senza autorità.

## Decisione

Il boundary è definito per use case e data flow. Nel percorso clinico identificato i dati restano nel Data Plane autorizzato con encryption e access control. Prima di analytics/secondary use, un Pseudonymization Service nel trust domain cliente sostituisce identificativi con token scoped per tenant, dataset e purpose.

Token vault, mapping e chiavi sono separati dal CDM. Export, log, trace e support bundle applicano minimizzazione/redaction prima di lasciare il boundary. Free text e immagini richiedono controlli dedicati; hashing semplice non è pseudonimizzazione sufficiente.

## Conseguenze

- linkage cross-dataset è esplicito e autorizzato;
- rotazione/re-tokenizzazione ha lineage;
- re-identification è break-glass o processo approvato e auditato;
- cancellazione/retention si propagano tramite subject token index governato;
- rischio residuo viene valutato per dataset.

## Alternative respinte

- token globale: correlabilità eccessiva;
- hash non salato: attacco dizionario;
- dati identificati nel data lake centrale: residency/minimizzazione insufficienti.

## Collegamenti

- [Privacy e data protection](../security/privacy-and-data-protection.md)
- [OMOP architecture](../omop/omop-architecture.md)
- [Data flow](../architecture/data-flow.md)
