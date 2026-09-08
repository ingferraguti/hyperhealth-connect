# ADR-015 — Architettura terminologica

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Terminology Services

## Contesto

Terminologie hanno licenze, versioni, gerarchie e distribuzione diverse. Query live a provider esterni nel fast path introdurrebbero drift e indisponibilità.

## Decisione

HHC espone un Vocabulary Service dietro API interne stabili. Importa package/snapshot autorizzati, verifica provenienza/licenza e pubblica versioni immutabili. Cache e indice sono keyed per snapshot; local/custom concepts hanno namespace e governance separati.

Lookup nel fast path usa snapshot locale qualificato. Expansion, subsumption e validation dichiarano code system e version. Upgrade è blue/green con impact report su mapping e dataset; la vecchia versione resta disponibile per replay entro retention.

## Conseguenze

- risultati deterministici e resilienti;
- costo di storage per snapshot multipli;
- license enforcement e distribution boundary necessari;
- fallback non cambia versione silenziosamente;
- monitoring di freshness senza auto-promotion.

## Alternative respinte

- chiamata internet live: availability e data disclosure;
- database unico aggiornato in-place: replay non deterministico;
- concetti locali senza namespace: collisioni.

## Collegamenti

- [Terminology architecture](../semantic/terminology-architecture.md)
- [Mapping governance](../mapping/mapping-governance.md)
- [Conformance](../testing/conformance-and-interoperability.md)
