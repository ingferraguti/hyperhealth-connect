# ADR-012 — Semantic Mapping Registry

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Terminology Governance

## Contesto

Le corrispondenze fra codici locali e terminologie standard sono contestuali, temporali e clinicamente rischiose. File sparsi o suggerimenti automatici non forniscono review né provenance.

## Decisione

Un registry centrale di metadata governa mapping con source/target system e version, relation type, scope tenant/facility, validità, rule, evidence, autore, reviewer e stato `DRAFT/PROPOSED/IN_REVIEW/APPROVED/DEPRECATED/REVOKED`.

Solo mapping approvati sono usati nei percorsi production che richiedono standardizzazione. Proposte AI o statistiche restano proposte. Snapshot pubblicati sono immutabili; modifica crea nuova versione. Precedenza e conflitti sono deterministici e verificati prima della promotion.

## Conseguenze

- audit e reproducibility dei risultati semantici;
- workflow di review clinica e separation of duties;
- metriche per unmapped, ambiguous, override e drift;
- cache keyed per mapping/terminology snapshot;
- rollback senza riscrittura silenziosa dello storico.

## Alternative respinte

- CSV in ogni flow: duplicazione e conflitti;
- mapping automatico in produzione: patient-safety risk;
- sovrascrittura della stessa riga: lineage perso.

## Collegamenti

- [Semantic Mapping Registry](../semantic/semantic-mapping-registry.md)
- [Mapping governance](../mapping/mapping-governance.md)
- [Politica agentic](../testing/agentic-coding-quality-policy.md)
