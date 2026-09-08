# ADR-007 — Configuration as Code

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Platform Engineering e Governance

## Contesto

Configurazioni create solo in UI non sono diffabili, riproducibili o promuovibili in modo sicuro tra molti clienti e facility.

## Decisione

Flow, connector binding, mapping reference, contract, policy binding e deployment intent sono documenti dichiarativi con schema versionato. Git o registry immutabile autorizzato è source of truth; la UI è un editor che produce lo stesso artifact.

Il lifecycle è `draft → validate → review → approve → sign → promote → observe → rollback/revoke`. Secret sono riferimenti, mai valori. Il bundle include dependency manifest, compatibility range, checksum e firma; il runtime compila un execution plan deterministico e rifiuta campi sconosciuti critici.

## Conseguenze

- ambienti ricostruibili e drift rilevabile;
- separation of duties e review obbligatorie;
- migration schema backward-compatible e test N/N-1;
- emergency change ha scadenza e riconciliazione post-evento;
- export/import non perde semantica o provenance.

## Alternative respinte

- database/UI come unica fonte: audit e portabilità insufficienti;
- script imperativi non tipizzati: idempotenza e preview deboli;
- secret nei file cifrati insieme alla config: blast radius e rotazione accoppiati.

## Collegamenti

- [Configuration as Code](../development/configuration-as-code.md)
- [Control Plane API](../api/control-plane-api.md)
- [ADR-010](./ADR-010-versioning-and-compatibility.md)
