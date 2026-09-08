# Tenancy e autorizzazione

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Identity & Access e Security Architecture |

## 1. Modello di scope

La gerarchia è:

```text
Tenant
└── Organization
    └── Facility
        └── Application
            └── Endpoint
```

Runtime Cell, environment, dataset e asset sono associati a scope, non inseriti nella gerarchia come semplici figli. ID sono globalmente univoci, immutabili e mai riciclati; display name non è chiave di sicurezza.

## 2. Principi

- deny-by-default e least privilege;
- tenant context determinato server-side;
- authorization su ogni operazione e oggetto, non solo UI/route;
- purpose e data class oltre al ruolo;
- amministrazione delegata senza ridurre policy superiori;
- isolamento applicato a compute, storage, cache, queue, search, audit, backup e telemetry;
- decisione e obblighi auditabili;
- nessun super-admin permanente per operazioni ordinarie;
- break-glass separato;
- test negativo per ogni percorso positivo sensibile.

## 3. Identità e principal

| Principal | Identità | Esempio scope |
|---|---|---|
| Human | subject federato stabile | organization/facility |
| Workload | service identity | cell/application/endpoint |
| Connector | identity/manifest publisher | endpoint e destination |
| Automation | workload identity | asset/environment limitati |
| Support | just-in-time human | tenant/case/time window |
| Agent/AI | workload via gateway | dataset/tool/purpose |

Gruppo IdP è input, non decisione finale: mapping verso ruoli HHC è versionato e scoped.

## 4. Ruoli baseline

- `TenantSecurityAdmin`: policy/identity senza accesso implicito a PHI;
- `OrganizationAdmin`: asset e deleghe dell'organization;
- `FacilityOperator`: endpoint, health e runbook della facility;
- `FlowDeveloper`: draft/test, non promotion autonoma;
- `FlowApprover`: approvazione secondo SoD;
- `InteroperabilityEngineer`: profili/connector test;
- `TerminologySteward`: mapping e vocabulary;
- `ClinicalSafetyReviewer`: hazard/oracle;
- `SREOperator`: operazioni runtime, non raw per default;
- `Auditor`: read-only evidence/audit;
- `DataAnalyst`: dataset/purpose autorizzato;
- `PrivacyOfficer`: review e rights workflow;
- `SupportEngineer`: accesso JIT per case.

Role assignment ha scope, valid-from/to, grantor, reason e review date.

## 5. ABAC e policy decision

Attributi: subject assurance/MFA, employment/contract, tenant/organization/facility, resource owner/classification, environment, action, purpose, time, device/session risk, emergency state e legal restriction. Policy restituisce `PERMIT`, `DENY` o `INDETERMINATE` più obligations; `INDETERMINATE` fallisce chiuso per azioni sensibili.

Obligations possono richiedere redaction, masking, step-up, double approval, watermark, rate limit, no-export o enhanced audit.

## 6. Propagazione del contesto

API gateway valida token e costruisce immutable security context. Chiamate interne propagano signed context o rivalutano con workload identity e resource scope. Messaggi/eventi portano tenant/facility nel metadata firmato; consumer confronta topic/queue/storage scope. Header client non sovrascrive il contesto.

Async job conserva initiator, purpose, policy version e delega; non continua oltre revoca/expiry se la policy lo vieta.

## 7. Isolamento per layer

| Layer | Controllo |
|---|---|
| API | object/function/property authorization e rate quota |
| Runtime | cell/worker pool, partition e flow scope |
| Database | schema/row security o database dedicato più query scope |
| Object storage | bucket/prefix/key policy e signed access breve |
| Broker | topic/ACL/consumer group scoped |
| Cache | key completa di tenant/facility/version/purpose |
| Search | index/alias e filter server-side non rimovibile |
| Audit | record scope e accesso separato |
| Backup | catalog, key e restore target autorizzati |
| OMOP | dataset instance e work schema per purpose |
| Telemetry | label bounded; nessun patient ID |

Defense in depth evita che un singolo filtro applicativo sia l'unico confine.

## 8. Deployment isolation profiles

- shared cluster/shared services con controlli logici rinforzati;
- shared cluster/dedicated Runtime Cell e datastore;
- dedicated cluster/database/key domain;
- on-prem facility cell con Control Plane centrale;
- sovereign/site-isolated con metadata minimizzati.

Il profilo deriva dal rischio e resta compatibile con gli stessi artifact.

## 9. Deleghe e policy inheritance

Policy tenant/corporate definisce floor non derogabile. Organization/facility può stringere o configurare valori consentiti. Ogni override registra origine, valore effettivo e motivo. Delegato non può assegnare privilegi che non possiede né modificare il proprio approvatore.

## 10. Accesso a PHI/raw

Non deriva dal ruolo operativo generale. Richiede case/purpose, scope preciso, step-up, time window, view redatta per default e audit. Download/export necessita controllo ulteriore e watermark/recipient. Bulk raw è vietato salvo processo specifico. Supporto usa synthetic evidence quando possibile.

## 11. Break-glass

Precondizioni: emergenza definita, identity forte, reason code e testo minimo, scope paziente/facility, TTL. Effetti: privilege temporaneo, banner, alert Security/Privacy, audit ad alta integrità e review obbligatoria. Non permette modifica audit, key export o disattivazione dei gate.

## 12. Lifecycle e access review

Provisioning da source autorevole; access request e approval; activation; periodic recertification; suspension/revocation; evidence retention. Leaver e compromissione revocano sessione/token/credential. Stale group, orphan workload, dormant privileged account e grant scaduto sono rilevati continuamente.

## 13. API authorization pattern

Ogni handler:

1. valida identity/audience;
2. risolve resource senza rivelarla al caller non autorizzato;
3. costruisce action/resource/scope/purpose;
4. valuta policy;
5. applica obligations prima di query/side effect;
6. vincola query server-side;
7. esegue con idempotency e concurrency control;
8. registra decision/outcome;
9. redige response/error.

Bulk/list/search applicano il filtro a monte e impediscono conteggi/timing che rivelino oggetti altrui.

## 14. Cache e consistency

Decision cache ha key completa, TTL breve, policy version e revocation strategy. Nessuna cache di response cross-tenant. Cambio grant/policy invalida o supera via version. In caso di Policy Service indisponibile: operazioni privilegiate deny; runtime continua solo azioni già autorizzate nel bundle valido.

## 15. Audit

Record minimo: actor/workload, authentication assurance, action, resource type/id opaco, tenant/facility, purpose, policy/version, obligations, decision, outcome, reason, request/correlation, timestamp e emergency flag. Deny sensibili e tentativi cross-scope sono security event. Audit non contiene payload.

## 16. Test matrix

Attore×azione×scope×purpose×stato. Copre BOLA/BFLA, ID enumeration, mass assignment, nested resource, bulk, export, cache/search/index, async job, backup/restore, impersonation, stale grant, token replay, audience, policy outage, confused deputy e side channel. Si usano due tenant, organization/facility multiple e ID locali collidenti.

## 17. Casi d'uso

### TEN-01 — Amministratore di gruppo

Gestisce policy di due organization ma non visualizza raw. Delega FacilityOperator senza concedere poteri corporate o accesso all'altra organization.

### TEN-02 — Facility con ID paziente coincidenti

Due aziende usano MRN uguale. Cache, mapping, raw e OMOP restano distinti; una ricerca globale non rivela la collisione a utenti non autorizzati.

### TEN-03 — Support case

Tecnico riceve accesso JIT alla singola cella per 30 minuti; vede metadata redatti. Ogni elevazione è approvata, alertata e revocata automaticamente.

### TEN-04 — Workload compromesso

Token del connector A viene usato contro storage B. Audience/scope e storage policy negano; l'identity è revocata senza fermare altri connector.

### TEN-05 — Analytics cross-organization

Ricerca approvata usa dataset derivato e token scoped al purpose; join non autorizzati e re-identification sono impediti e auditati.

## 18. Gate

- zero accesso/leakage cross-tenant in suite e pen test;
- 100% endpoint sensibili nella authorization matrix;
- nessun ruolo orfano o grant privilegiato scaduto;
- break-glass e revoca esercitati;
- policy/cache outage behavior verificato;
- audit decision coverage completo;
- restore non cambia ownership/scope.

## 19. Fonti

Verificate il **5 settembre 2026**:

- [NIST SP 800-207 — Zero Trust](https://csrc.nist.gov/pubs/sp/800/207/final);
- [NIST SP 800-207A — Cloud-native access control](https://csrc.nist.gov/pubs/sp/800/207/a/final);
- [OWASP ASVS 5.0.0](https://owasp.org/www-project-application-security-verification-standard/);
- [OWASP API Security Top 10](https://owasp.org/API-Security/);
- [Kubernetes Multi-tenancy](https://kubernetes.io/docs/concepts/security/multi-tenancy/).

## 20. Collegamenti

- [Security architecture](./security-architecture.md)
- [Privacy](./privacy-and-data-protection.md)
- [ADR-009](../adr/ADR-009-identity-and-tenancy.md)
- [Control Plane API](../api/control-plane-api.md)
