# ADR-023 — Retention dei raw event

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Data Governance, Privacy e Legal

## Contesto

Raw abilita audit e replay ma contiene dati sanitari ad alto rischio. Un periodo universale non riflette finalità, contratto, paese, legal hold e costo.

## Decisione

La retention è policy per data class, purpose, tenant, facility, giurisdizione e stato. Il prodotto non codifica un periodo legale universale. Policy dichiara active retention, archive tier, deletion mode, legal hold, key lifecycle e metadata/tombstone retention.

Object lifecycle è automatico e auditato. Legal hold sospende cancellazione entro scope autorizzato. Crypto-shredding è ammesso solo se dimostrato e coordinato con replica/backup. Backup non estende accessibilità ordinaria e applica expiry compatibile.

## Conseguenze

- capacity planning include retention e replica;
- cancellazione è verificata con report e sample;
- replay oltre retention non è promesso;
- policy change ha impact preview;
- metadata minimo può sopravvivere senza payload per prova di cancellazione.

## Alternative respinte

- conservare tutto per sempre: minimizzazione e costo;
- cancellare subito dopo delivery: recovery/forensic insufficienti;
- TTL non auditato: impossibile dimostrare esecuzione.

## Collegamenti

- [Privacy](../security/privacy-and-data-protection.md)
- [Data architecture](../architecture/data-architecture.md)
- [ADR-004](./ADR-004-immutable-raw-events.md)
