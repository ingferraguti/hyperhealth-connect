# API del Control Plane

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ambito | Governance HHC multitenant, multiazienda e multifacility |
| Ultimo aggiornamento | 1 settembre 2026 |
| Responsabili | Control Plane, Architecture, Security, SRE, Product Governance |
| SLO | 99,9% mensile; RTO ≤4 ore; RPO ≤15 minuti |

## 1. Finalità e confine

La Control Plane API governa inventario, contratti, connector, flow, mapping, policy, approvazioni, deployment, audit, evidence e operazioni di recovery. È il sistema di record del **desired state**, non del traffico clinico.

Non riceve normalmente payload sanitari e non è una dipendenza sincrona del Data Plane. Ogni Runtime Cell conserva l'ultima release valida e continua i flow già autorizzati per almeno 24 ore in assenza del Control Plane.

Le convenzioni di [api-standards.md](./api-standards.md) sono vincolanti.

## 2. Obiettivi architetturali

1. Un'unica semantica di governance per cloud, on-premise, sovereign cloud ed edge facility.
2. Isolamento forte di Tenant → Organization → Facility → Application → Endpoint.
3. Desired state dichiarativo, versionato, firmato e convergente.
4. Mutazioni idempotenti, optimistic concurrency e workflow approvativi.
5. Nessun accesso diretto UI/connector ai database del Control Plane.
6. Audit append-only e ricostruzione completa di chi ha cambiato cosa, perché e dove.
7. Rollout canary/progressivo e rollback come primitive di prima classe.
8. Letture efficienti su scala enterprise e operazioni lunghe asincrone.
9. Degrado sicuro: change bloccati, runtime operativo con stato locale valido.
10. API evolvibili senza accoppiare il core agli atti EHDS o a country pack non definitivi.

## 3. Topologia e trust boundary

```text
Operator / CI / SIEM / ITSM
             │
        WAF + API Gateway
             │
   Identity + Policy Enforcement
             │
      Control Plane Services
       │      │       │
 Metadata DB  Artifact Store  Audit Ledger
       │      │       │
       └── signed desired-state bundles ──┐
                                          │ pull/mTLS
                                    Runtime Agents
```

- Browser e automation hanno client/audience distinti.
- Gateway applica authentication, body/rate limits, correlation e coarse policy.
- Il servizio applica object/field authorization e invariant transazionali.
- Runtime Agent preferisce pull autenticato; eventuale notification channel comunica solo la disponibilità di una release.
- Bundle/artifact sono scaricati per digest, verificati localmente e attivati solo dopo health gate.
- Secret value non è contenuto nel metadata DB né nel bundle: solo reference e binding policy.

## 4. Modello di authorization

### 4.1 Ruoli di riferimento

| Ruolo | Capability principale | Limiti |
|---|---|---|
| Tenant Administrator | organization, identity assignment, policy tenant | niente accesso payload implicito |
| Organization Administrator | facility/application/endpoint assegnati | non oltre organization |
| Integration Engineer | connector instance, flow draft, test e deploy request | non approva il proprio change critico |
| Clinical Informatics | mapping/terminology semantic review | niente secret value |
| Security Administrator | trust, policy, publisher, credential binding | dual control per azioni critiche |
| SRE/NOC | runtime, health, rollback operativo, capacity | payload solo break-glass |
| SOC | security event, containment, revoke connector/credential | nessuna modifica semantica |
| Data Steward | dataset/purpose/access analytics | niente flow deployment |
| Auditor | read-only evidence e audit export | scoped, watermarked, auditato |
| Vendor Integrator | asset del contratto assegnato | niente tenant global catalog write |

RBAC assegna capability; ABAC/policy valuta scope, environment, purpose, rischio, time e obligations. I ruoli sono template: la decisione reale è sempre server-side.

### 4.2 Separation of duties

Richiedono approvatore distinto dal proponente:

- publish/rollback di mapping clinico ad alto rischio;
- produzione di un flow Tier 0;
- replay/reprocessing massivo;
- export di dati sensibili;
- trust di un publisher esterno;
- break-glass o riduzione di retention;
- modifica di policy cross-organization;
- revoca/rotazione della root of trust.

## 5. Regole comuni delle risorse

Ogni risorsa governata contiene almeno:

```json
{
  "id": "opaque-id",
  "tenantId": "t-...",
  "name": "human-readable",
  "status": "DRAFT",
  "version": 7,
  "labels": {"country": "IT"},
  "owner": {"teamId": "team-..."},
  "createdAt": "2026-09-01T10:00:00Z",
  "createdBy": "subject-...",
  "updatedAt": "2026-09-01T10:05:00Z",
  "updatedBy": "subject-...",
  "links": {"self": "/api/v1/..."}
}
```

`tenantId` è restituito per trasparenza ma verificato tramite ownership. `version` genera ETag. Label non sostituiscono campi governati e hanno namespace/allowlist/limiti.

## 6. Lifecycle comune

Stato logico degli asset distribuibili:

`DRAFT → IN_REVIEW → APPROVED → ACTIVE → DEPRECATED → RETIRED`

Sono consentiti `REJECTED` da review e `REVOKED` per contenimento. Una versione ACTIVE è immutabile; una modifica crea nuova versione. RETIRED non elimina evidence o riferimenti storici.

Transizioni:

- richiedono `If-Match`;
- sono validate contro policy e dipendenze;
- producono audit con before/after digest;
- possono creare un'operation asincrona;
- non possono lasciare riferimenti a artifact mancanti;
- falliscono atomicamente prima della pubblicazione o restano in stato esplicitamente recuperabile.

## 7. Tenancy e inventory API

### 7.1 Risorse

| Risorsa | Relazioni principali |
|---|---|
| Tenant | root di ownership, residency e policy |
| Organization | azienda sanitaria, gruppo o legal entity configurata |
| Facility | sito ospedaliero, territorio, laboratorio o data center |
| Application | EHR, LIS, RIS/PACS, registry, gateway |
| Endpoint | protocollo/direzione/address reference/trust binding |
| RuntimeCell | failure domain, capacità, sito e desired release |
| Environment | dev/test/preprod/prod e policy promozione |

Endpoint principali:

```text
GET/POST       /api/v1/tenants
GET/PATCH      /api/v1/tenants/{tenantId}
GET/POST       /api/v1/organizations
GET/POST       /api/v1/facilities
GET/POST       /api/v1/applications
GET/POST       /api/v1/endpoints
GET/POST       /api/v1/runtime-cells
GET            /api/v1/inventory-relationships
POST           /api/v1/inventory-validation-jobs
```

### 7.2 Invarianti

- parent e child appartengono allo stesso tenant salvo relazione federata esplicita;
- spostare una facility tra organization è migrazione governata, non PATCH semplice;
- ID e history non cambiano in caso di rename;
- endpoint address/credential reference sono redatti in list API;
- produzione richiede owner, support group, data classification, residency, SLO tier e Runtime Cell;
- decommission verifica flow, secret, audit, retention e dependency.

## 8. Catalogo asset

Il catalogo espone:

```text
/api/v1/connector-definitions
/api/v1/connector-instances
/api/v1/contracts
/api/v1/schemas
/api/v1/mapping-releases
/api/v1/terminology-packages
/api/v1/canonical-types
/api/v1/flow-definitions
/api/v1/policy-bundles
/api/v1/country-packs
```

Ogni asset ha canonical ID, SemVer, digest, publisher, trust status, compatibility, license, SBOM/provenance ove eseguibile, dependencies, capability e limitations. Artifact bytes sono in content-addressed store; metadata e signature nel catalogo.

## 9. Connector API

### 9.1 Definition e instance

`ConnectorDefinition` descrive il tipo immutabile; `ConnectorInstance` lega versione, endpoint, config e credential reference a uno scope.

```json
{
  "definitionId": "connector-hl7v2-mllp",
  "definitionVersion": "1.4.0",
  "artifactDigest": "sha256:...",
  "publisher": "hhc-official",
  "trustStatus": "TRUSTED",
  "capabilities": ["INGRESS", "EGRESS", "ACK_ON_DURABLE"],
  "sdkCompatibility": ">=1.3 <2.0"
}
```

Instance config è validata dal JSON Schema della definition. Campi secret sono `secretRef`; la response non espone valore né hash riutilizzabile. Test connection è job isolato, con target allowlist, timeout e audit; non abilita automaticamente l'instance.

Endpoint:

```text
POST /api/v1/connector-instances
POST /api/v1/connector-instances/{id}:validate
POST /api/v1/connector-instances/{id}:test-connection
POST /api/v1/connector-instances/{id}:enable
POST /api/v1/connector-instances/{id}:disable
GET  /api/v1/connector-instances/{id}/compatibility
```

I comandi usano POST con Idempotency-Key e operation resource; il suffisso `:azione` è riservato ai comandi espliciti.

## 10. Flow API

Un flow versionato specifica ingress, processing, mapping, destinations, failure policy, ACK point, retention, SLO tier e observability.

```json
{
  "flowId": "flow-lab-result",
  "release": "3.2.0",
  "scope": {"tenantId": "t-...", "facilityId": "f-..."},
  "sourceConnectorInstanceId": "ci-...",
  "pipeline": [
    {"type": "validate", "contractRef": "contract://...@1.1.0"},
    {"type": "map", "mappingRef": "mapping://...@2.4.0"},
    {"type": "route", "destinationIds": ["ci-dest-..."]}
  ],
  "ackPolicy": "ACK_ON_DURABLE",
  "delivery": {"mode": "AT_LEAST_ONCE", "orderingScope": "patient-order"},
  "sloTier": "TIER_0"
}
```

Validation produce dependency graph, compatibility report, threat/policy findings, semantic loss report e capacity estimate. Nessun flow ACTIVE punta a DRAFT, mutable alias o latest non risolto.

## 11. Deployment API

### 11.1 Deployment resource

```json
{
  "deploymentId": "dep-...",
  "flowReleaseRef": "flow://lab-result@3.2.0",
  "targets": ["rc-...", "rc-..."],
  "strategy": {
    "type": "CANARY",
    "canaryTargets": ["rc-..."],
    "maxUnavailable": 0,
    "pauseAfterCanary": true
  },
  "status": "PLANNED",
  "rollbackRef": "release-bundle://..."
}
```

### 11.2 Stati

`PLANNED → VALIDATING → APPROVAL_PENDING → DISTRIBUTING → CANARY → PAUSED → PROMOTING → SUCCEEDED`

Terminali alternativi: `REJECTED`, `FAILED`, `ROLLED_BACK`, `CANCELLED`. Un target ha stato autonomo: `PENDING`, `DOWNLOADED`, `VERIFIED`, `STAGED`, `ACTIVE`, `UNHEALTHY`, `ROLLED_BACK`.

### 11.3 Bundle

Il bundle include solo riferimenti risolti e immutabili:

- flow e connector versions;
- contract/schema e mapping release;
- terminology snapshot richiesto;
- policy/runtime limits;
- secret references, mai secret value;
- compatibility range;
- manifest, digest, firma, issuer, scope, issued/expiry;
- predecessor per rollback.

Il Runtime Agent verifica firma, chain, revoca, digest, target scope, compatibilità, monotonic version e policy. Activation è atomica per flow; config parziale non diventa active.

### 11.4 Health gate

Canary considera config validation, process/readiness, synthetic transaction, error/latency, queue age, integrity mismatch, cross-tenant deny, audit e mapping outcome. Un gate critico fallito arresta la promozione. Rollback non cancella gli eventi già elaborati e preserva la versione effettiva nel lineage.

## 12. Runtime Cell API

Runtime Agent espone verso il Control Plane solo metadata operativi minimizzati:

- agent/runtime version e compatibility;
- active/staged bundle digest;
- connector/flow health aggregato;
- capacity/saturation summary;
- queue age/lag e replication pending;
- certificate/config expiry;
- last contact, time sync e audit buffer status;
- conformance/qualification status.

Non invia payload o identificativi clinici. Diagnostic bundle dettagliato è on-demand, redatto, cifrato, con TTL e approvazione.

Heartbeat non è l'unica prova di salute: synthetic transaction e SLI business prevalgono. Il Control Plane non forza il runtime a usare una config non verificata.

## 13. Mapping, semantic e contract API

Authoring e publish sono separati:

```text
POST /api/v1/mapping-drafts
POST /api/v1/mapping-drafts/{id}:validate
POST /api/v1/mapping-drafts/{id}:submit-review
POST /api/v1/mapping-releases
POST /api/v1/contracts/{id}:qualify
POST /api/v1/terminology-packages/{id}:promote
GET  /api/v1/assets/{id}/impact
```

Impact analysis attraversa dependency graph di flow, facility, dataset e release. Publish richiede golden test, semantic review, terminology pin, loss report, signature e rollback candidate. I runtime non consumano draft.

## 14. Policy API

Policy domains:

- tenancy/residency;
- connector publisher e permission;
- encryption/trust;
- data access/purpose/consent;
- retention/legal hold;
- deployment/release gate;
- replay/export/break-glass;
- capacity/rate/cost;
- country pack.

Policy evaluation restituisce:

```json
{
  "decision": "PERMIT",
  "decisionId": "pd-...",
  "policyBundleRef": "policy://tenant-prod@9",
  "obligations": ["AUDIT_HIGH", "DUAL_APPROVAL", "WATERMARK_EXPORT"]
}
```

Explain API è autorizzata, redatta e auditata; non rivela policy di altri tenant né dettagli sfruttabili. Policy publish usa test positive/negative e simulation su snapshot.

## 15. Secret reference e trust API

Il Control Plane gestisce metadata:

- provider/secret path reference opaco;
- target scope e workload binding;
- owner, rotation/expiry e last validation;
- certificate subject/SAN/purpose senza private key;
- trust bundle version e revocation status.

Non accetta secret in JSON, query, log o artifact. La creazione del valore avviene nel secret manager o con one-time secure exchange. Runtime risolve localmente con identity autorizzata.

Operazioni critiche: rotate, revoke, overlap, emergency disable. Tutte asincrone, idempotenti, dual-control quando il blast radius è multi-facility e con verifica post-rotazione.

## 16. Message Explorer e payload access

Il Control Plane offre indici e riferimenti, non una copia indiscriminata del raw. Query consentite:

- message/correlation ID esatto;
- endpoint/flow/status/time range con limiti;
- delivery state, mapping/version, error code e lineage summary;
- payload access tramite capability separata e fetch dal data domain autorizzato.

Payload view:

- default redatto/minimizzato;
- purpose e ticket/incident obbligatori;
- step-up e time-bound grant;
- no bulk download;
- watermark/screen protection ove pratico;
- audit high-severity;
- residency rispettata: il payload può restare visualizzabile solo localmente.

## 17. Replay e reprocessing API

Tipi distinti:

| Tipo | Input | Effetto |
|---|---|---|
| Delivery replay | output già prodotto | nuova delivery attempt, stessa semantic version |
| Transformation replay | raw/CSE | riesegue trasformazione dichiarata |
| Semantic reprocessing | raw/CSE | usa nuove mapping/terminology versions |
| OMOP rebuild | event snapshot | nuova DatasetVersion analitica |

Request obbligatoria:

```json
{
  "type": "DELIVERY_REPLAY",
  "scope": {"flowId": "flow-...", "from": "...", "to": "..."},
  "reason": "incident-123",
  "dryRun": true,
  "rateLimit": {"eventsPerSecond": 100},
  "expectedCount": 4821
}
```

Preview mostra count, byte, destinations, known duplicate risk, expired consent/retention, unavailable raw e estimated duration. Mass replay richiede dual approval. Traffic live ha priorità; job ha pause/cancel/checkpoint. Nessuna modalità modifica raw o nasconde history.

## 18. Audit ed evidence API

```text
GET  /api/v1/audit-events
POST /api/v1/evidence-export-jobs
GET  /api/v1/integrity-verifications/{id}
POST /api/v1/integrity-verification-jobs
GET  /api/v1/change-records/{id}
```

Cursor è firmato e scoped. Export è asincrono, cifrato, firmato, con manifest/hash, watermark, expiry e recipient binding. Query massive non competono con audit ingest. Integrity verification controlla chain/hash/signature/gap e produce finding, non altera la fonte.

## 19. Approval e change record

Ogni change ad alto impatto ha:

- immutable change ID;
- proponente, motivazione e ticket;
- asset diff e dependency impact;
- test/evidence/Conformance Pack;
- risk/clinical safety assessment;
- approvatori e separation-of-duties outcome;
- maintenance window e rollback;
- deployment result e post-implementation review.

Approval ha TTL e viene invalidata se digest o perimetro cambiano. Una firma su “latest” non autorizza versioni future.

## 20. Eventi del Control Plane

Eventi AsyncAPI/CloudEvents informano consumer autorizzati senza contenere PHI:

- `asset.version.published.v1`;
- `deployment.status.changed.v1`;
- `runtime.health.changed.v1`;
- `connector.trust.revoked.v1`;
- `policy.bundle.published.v1`;
- `replay.status.changed.v1`;
- `audit.integrity.finding.v1`.

Ogni evento include publication ID, source, time, subject opaco, tenant scope autorizzato, schema URI e correlation. Consumer implementano deduplica; notification non è l'autorità dello stato, che resta nella resource API.

## 21. Errori di dominio

| Code | HTTP | Significato | Retry |
|---|---:|---|---|
| `HHC-CTRL-SCOPE-MISMATCH` | 404/403 | ownership non coerente | no |
| `HHC-CTRL-STALE-VERSION` | 412 | ETag non corrente | dopo reread |
| `HHC-CTRL-DEPENDENCY-NOT-ACTIVE` | 409 | asset richiesto non promuovibile | no |
| `HHC-CTRL-APPROVAL-REQUIRED` | 409 | manca approval valida | no |
| `HHC-CTRL-IDEMPOTENCY-COLLISION` | 409 | key riusata con body diverso | no |
| `HHC-CTRL-RUNTIME-INCOMPATIBLE` | 422 | target non supporta bundle | no |
| `HHC-CTRL-POLICY-DENY` | 403 | policy nega l'azione | no |
| `HHC-CTRL-TARGET-UNREACHABLE` | 503 | runtime temporaneamente offline | sì, bounded |
| `HHC-CTRL-OPERATION-IN-PROGRESS` | 409 | mutazione concorrente esclusiva | poll |

Ogni errore usa RFC 9457 e correlation ID senza dettagli sensibili.

## 22. Performance e scala

Baseline per tenant enterprise di riferimento:

- ≥100 organization;
- ≥1.000 facility;
- ≥10.000 endpoint;
- ≥20.000 flow;
- query indicizzate p95 ≤500 ms nella reference topology;
- pagination cursor per collection grandi;
- deploy fan-out asincrono, bounded e backpressured;
- inventory/health read models separati dalle transazioni;
- artifact download via content-addressed distribution, non proxy applicativo centrale.

Performance test include hot tenant, 10.000 Runtime Agent reconnect, rollout simultanei, audit burst, query di impact graph e perdita di una zona. Quote impediscono a un tenant di saturare worker, DB connection o job scheduler condivisi.

## 23. Disponibilità, BC e DR

Control Plane: SLO 99,9%, RTO ≤4 ore, RPO ≤15 minuti. Implementazione enterprise:

- gateway e service replica multi-zona;
- metadata DB HA con fencing e PITR;
- artifact store versionato/replicato, checksum e immutabilità;
- audit ledger durabile con buffer e replica;
- signing key protetta da KMS/HSM e recovery ceremony;
- DNS/certificate/identity recovery inclusi nel runbook;
- backup cifrati, immutabili e restore testato;
- clean-room recovery contro compromissione.

Durante outage:

- runtime continua ≥24 ore con release locale;
- change/deploy/replay/export amministrativi sono bloccati;
- eventuali read cache sono marcate stale;
- runtime audit/health si bufferizzano entro capacità;
- nessun fallback a bundle non firmato o secret statico.

Recovery riconcilia operation, deployment target, runtime active digest, revoche, audit e outbox prima di riabilitare write API. In split brain un solo writer è autorizzato; fencing precede failover/failback.

## 24. Observability e allarmi

SLI:

- availability e latency per operation/class/tenant class;
- authn/authz deny e policy evaluation latency;
- DB/object store/signing dependency;
- operation queue age e completion rate;
- deployment convergence e target unhealthy;
- Runtime Cell last contact/config drift;
- audit/outbox lag e integrity finding;
- artifact/signature failure;
- certificate/config/approval expiry;
- rate limit, hot tenant e saturation.

Alert critici: bundle signature invalid, cross-scope finding, audit gap, unauthorized publisher, deployment Tier 0 incoerente, restore failure e config attiva non registrata. Dashboard non usa PHI o label ad alta cardinalità.

## 25. Testing e qualification

- authorization matrix su ogni resource/action/field;
- cross-tenant, parent spoofing e ID enumeration;
- ETag/concurrency/idempotency e crash boundary;
- lifecycle/state-machine/model-based test;
- dependency graph e incompatibilità N/N-1;
- bundle signature/scope/revocation/tamper;
- canary failure e rollback;
- Control Plane offline 24 ore;
- fan-out, hot tenant e query p95;
- audit completeness e PHI leakage;
- backup/restore/failover/failback;
- penetration test indipendente sulle API privilegiate.

Il release gate richiede evidence secondo [test-strategy.md](../testing/test-strategy.md).

## 26. Casi d'uso sanitari europei

### CTRL-UC-01 — Onboarding di gruppo ospedaliero

Un tenant crea tre aziende, quindici facility e più identity domain con ID locali sovrapposti.

**Esito:** ownership validata; policy inheritance; residency per facility; endpoint/trust separati; nessun accesso cross-organization; inventory e audit completi.

### CTRL-UC-02 — Flusso risultati critici

Un LIS invia risultati critici a EHR e sistema di allerta in due facility.

**Esito:** flow Tier 0, ACK_ON_DURABLE, mapping/terminology pinnati, dual approval, canary, synthetic risultato, rollback e lineage delle versioni.

### CTRL-UC-03 — Country pack transfrontaliero

Patient Summary/ePrescription adottano un profilo nazionale aggiornato.

**Esito:** pack isolato dal core; package/digest; impact analysis; partner qualification; rollout per paese; nessun claim EHDS/MyHealth@EU oltre l'evidenza.

### CTRL-UC-04 — Revoca connector compromesso

Il SOC rileva una release di terza parte compromessa su più facility.

**Esito:** publisher/version revocati; nuove attivazioni negate; instance isolate; flow alternativi; evidence conservata; runtime offline riceve revoca alla riconnessione secondo policy di emergenza.

### CTRL-UC-05 — Rotazione certificati PACS

Una CA di dispositivi imaging viene sostituita senza fermare l'attività.

**Esito:** trust overlap, target impact, staged rollout, association test, revoke finale, audit e rollback del trust bundle.

### CTRL-UC-06 — Replay di ADT post-guasto

Dopo outage del destinatario, 500.000 ADT sono pending.

**Esito:** preview/conteggio; live traffic priority; rate concordato; idempotency/dedup; pause/resume; reconciliation; nessuna duplicazione di merge/unmerge.

### CTRL-UC-07 — Facility isolata dalla WAN

Una struttura territoriale perde connettività centrale per 24 ore.

**Esito:** flow attivi continuano; bundle/terminologia locale; audit/spool capacity; change bloccati; reconnect progressivo e config reconciliation.

### CTRL-UC-08 — Rollback di mapping laboratorio

Il canary rileva conversione UCUM errata.

**Esito:** promozione arrestata; predecessor riattivato; eventi canary identificati; semantic reprocessing separato; notifica clinical safety; audit immutabile.

### CTRL-UC-09 — Evidence per audit multi-azienda

Un auditor richiede change/deploy/access evidence per una sola organization.

**Esito:** filtro scope prima della paginazione; export firmato e watermarked; nessun record di altre aziende; integrity verify; TTL e download auditato.

### CTRL-UC-10 — DR del Control Plane

Il sito primario è indisponibile e la replica potrebbe essere stale.

**Esito:** fencing; restore/failover entro RTO/RPO; artifact/signature/audit verification; runtime active digest reconciliation; canary admin operation; failback controllato.

## 27. Anti-pattern vietati

- payload clinico centralizzato per comodità della UI;
- write diretto su DB o artifact store;
- bundle con alias `latest` o dipendenza mutabile;
- secret value in config/API;
- deploy sincrono su migliaia di target;
- heartbeat come unica health evidence;
- approvazione riusata dopo cambio digest;
- rollback che riscrive la storia;
- admin globale per operatori locali;
- Runtime Agent che accetta push non firmato;
- delete fisico di asset referenziato;
- failover senza fencing.

## 28. Fonti ufficiali e data di verifica

Fonti verificate il **1 settembre 2026**:

- OpenAPI Specification 3.2.0: <https://spec.openapis.org/oas/v3.2.0.html>
- RFC 9110 HTTP Semantics: <https://www.rfc-editor.org/rfc/rfc9110.html>
- RFC 9457 Problem Details: <https://www.rfc-editor.org/rfc/rfc9457.html>
- RFC 9700 OAuth 2.0 Security BCP: <https://www.rfc-editor.org/rfc/rfc9700.html>
- RFC 9745 Deprecation: <https://www.rfc-editor.org/rfc/rfc9745.html>
- AsyncAPI 3.1.0: <https://www.asyncapi.com/docs/reference/specification/latest>
- CloudEvents 1.0.2: <https://github.com/cloudevents/spec>
- SLSA Specification 1.2: <https://slsa.dev/spec/v1.2/>
- OpenTelemetry Specification 1.60.0: <https://opentelemetry.io/docs/specs/otel/>
- Regolamento (UE) 2025/327 EHDS: <https://eur-lex.europa.eu/eli/reg/2025/327/oj/>
- Commissione europea, EHDS e calendario: <https://health.ec.europa.eu/ehealth-digital-health-and-care/european-health-data-space-regulation-ehds_en>

Le fonti disciplinano il formato e il contesto; il comportamento HHC è definito da questo contratto e verificato sulla release effettiva.
