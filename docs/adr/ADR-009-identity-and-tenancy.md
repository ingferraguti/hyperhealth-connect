# ADR-009 — Identità e tenancy

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Security Architecture

## Contesto

HHC deve servire gruppi sanitari, aziende e facility con amministrazione delegata e identificativi locali sovrapposti. Un semplice `tenant_id` applicativo non protegge cache, code, storage, audit o backup.

## Decisione

La gerarchia normativa è `Tenant → Organization → Facility → Application → Endpoint`; Runtime Cell è associata a uno o più scope autorizzati e costituisce failure/scaling boundary. Gli identificativi sono immutabili e non riutilizzati.

Identity umane federano via OIDC/SAML secondo deployment; workload usano identity dedicata e mTLS/token con audience. Authorization combina RBAC, ABAC, resource scope, purpose e obblighi. Il tenant context è derivato da credenziali/risorsa server-side, mai accettato ciecamente da header client.

## Conseguenze

- ogni query, cache key, event, object e audit record porta scope esplicito;
- delega non può abbassare policy corporate non derogabili;
- break-glass richiede step-up, motivo, durata, alert e review;
- deployment dedicato è possibile senza fork del codice;
- test negativi attraversano API, search, export, backup, OMOP e telemetry.

## Alternative respinte

- ruolo globale più filtro UI: bypassabile;
- cluster separato obbligatorio per ogni facility: costo non sempre proporzionato;
- tenant dichiarato dal client: confused-deputy e spoofing.

## Collegamenti

- [Tenancy e autorizzazione](../security/tenancy-and-authorization.md)
- [API standards](../api/api-standards.md)
- [System context](../architecture/system-context.md)
