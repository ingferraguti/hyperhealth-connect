# ADR-011 — Canonical Semantic Event Model

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Semantic Architecture

## Contesto

Mapping point-to-point non scala e rende difficile riuso analitico, ma un modello canonico universale perderebbe dettagli e bloccherebbe flow semplici.

## Decisione

Il Canonical Semantic Event (CSE) rappresenta fatti clinico-operativi selezionati con subject, encounter/context, effective/recorded time, code/value/unit, status, provenance e source references. È distinto dall'Integration Envelope tecnico e non sostituisce il raw.

I canonical type sono versionati, con invarianti e compatibility rule. La produzione del CSE è opzionale per flow puramente tecnici; diventa obbligatoria per capability semantiche/analytics dichiarate. Unknown, absent, ambiguous e invalid non collassano nello stesso valore.

## Conseguenze

- riuso dei mapping verso OMOP e altre proiezioni;
- schema più piccolo di FHIR ma richiede governance propria;
- ogni semantic assertion registra rule e terminology snapshot;
- loss budget e unmapped state sono osservabili;
- nuove major richiedono migrazione o coesistenza.

## Alternative respinte

- FHIR universale: vedi ADR-005;
- canonical completo di ogni dominio: ingestibile;
- soli JSON generici: nessuna semantica verificabile.

## Collegamenti

- [Canonical Semantic Event](../canonical-model/canonical-semantic-event.md)
- [Integration Envelope](../canonical-model/integration-envelope.md)
- [Mapping engine](../mapping/mapping-engine.md)
