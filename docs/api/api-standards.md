# Standard API e contratti

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ambito | API HHC di Control, Data, Semantic e Analytics Plane |
| Target | Produzione multiazienda e multifacility nella sanità europea |
| Ultimo aggiornamento | 1 settembre 2026 |
| Responsabili | API Platform, Architecture, Security, Interoperability, SRE |
| Revisione minima | A ogni major/minor API e almeno trimestrale |

## 1. Finalità

Questo documento è il contratto trasversale per tutte le API HHC. Definisce regole verificabili per progettazione, sicurezza, compatibilità, prestazioni, resilienza, audit e lifecycle. I documenti specialistici possono restringere queste regole, mai indebolirle senza ADR e accettazione formale del rischio.

Le API non espongono tabelle interne e non aggirano i confini architetturali. Il Control Plane governa il desired state ma non entra nel percorso sincrono dei messaggi clinici; le Runtime Cell continuano con bundle firmati durante l'indisponibilità centrale; l'Analytics Plane non rallenta l'ACK clinico.

## 2. Linguaggio normativo

`DEVE`, `NON DEVE`, `DOVREBBE`, `PUÒ` hanno valore prescrittivo. Ogni deviazione da un `DEVE` richiede identificativo, requisito/rischio, perimetro, durata, controllo compensativo, approvazione Architecture/Security, scadenza e test di chiusura.

Non sono derogabili isolamento cross-tenant, authorization a livello di oggetto, ACK coerente con durabilità, protezione dei secret, audit delle azioni privilegiate e assenza di perdita silenziosa.

## 3. Baseline degli standard

| Ambito | Baseline HHC al 1 settembre 2026 | Regola |
|---|---|---|
| HTTP | RFC 9110 e famiglia HTTP corrente | Semantica indipendente da HTTP/1.1, HTTP/2 o HTTP/3 |
| REST contract | OpenAPI 3.2.0 | Toolchain qualificato; 3.1.2 ammesso per consumer non compatibili |
| Event contract | AsyncAPI 3.1.0 | Binding e schema dichiarati per canale |
| JSON Schema | Draft 2020-12 | Dialect esplicito tramite `$schema` |
| Errori HTTP | RFC 9457 Problem Details | `application/problem+json` |
| Event envelope esterno | CloudEvents 1.0, testo stabile 1.0.2 | Non sostituisce l'Integration Envelope HHC |
| Tracing HTTP | W3C Trace Context | Header validati al trust boundary |
| OAuth security | RFC 9700 | Best Current Practice vincolante |
| Token binding | mTLS RFC 8705 o DPoP RFC 9449 | Secondo client e rischio |
| JWT | RFC 8725 | Algoritmo, issuer, audience e tipo rigidamente verificati |
| Deprecation | RFC 9745 e Sunset RFC 8594 | Segnale machine-readable più migration guide |
| Telemetria | OpenTelemetry Specification 1.60.0 | Versioni pinnate per release |

La baseline non aggiorna automaticamente la produzione. Ogni upgrade passa per contract diff, tool qualification, N/N-1 test, canary e rollback.

## 4. Classi di API

| Classe | Esempi | Scrittura | Disponibilità |
|---|---|---|---|
| Control API | tenant, asset, flow, deployment, policy | desired state e governance | Control Plane, 99,9% |
| Runtime management API | health, bundle status, diagnostic summary | comandi limitati e firmati | locale alla Runtime Cell |
| Clinical protocol API | FHIR, IHE, DICOMweb, REST clinico | dati/workflow clinici | eredita Tier 0/1 del flow |
| Semantic API | lookup, validate, expand, translate | authoring separato dal runtime | snapshot locale |
| Analytics API | dataset, cohort, feature, quality, lineage | job governati | Tier 2, 99,9% |
| Evidence API | audit, lineage, export firmato | append/query controllati | classe del dominio probatorio |

Le API amministrative e cliniche usano hostname, gateway, credential audience, rate budget e policy distinti. Nessun endpoint decide il tenant da un campo fornito nel body.

## 5. Identità e scope

La gerarchia canonica è `Tenant → Organization → Facility → Application → Endpoint`. `Runtime Cell` è il failure e scaling domain, non ownership clinica.

### 5.1 Derivazione dello scope

Lo scope effettivo deriva dall'intersezione di:

1. identità autenticata e trust domain;
2. audience e grant del token/workload identity;
3. assegnazioni nel tenant registry;
4. resource path e ownership risolta server-side;
5. purpose, consent e policy applicabili;
6. residency e segregazione.

Header, query o body selezionano solo uno scope già autorizzato. `tenantId`, `facilityId` o `datasetId` forniti dal client non provano appartenenza.

### 5.2 Identificativi

- ID HHC opachi, immutabili, non sequenziali e non riutilizzati;
- nessuna PHI nell'ID;
- ID esterni con system/namespace/assigning authority;
- enumerazione impedita da authorization, non dalla sola casualità;
- URI canonici stabili per asset versionati;
- correlation/operation ID distinti dagli identificativi clinici.

## 6. Convenzioni URI e naming

Base path pubblico: `https://{api-host}/api/{major}/{resource}`.

- nomi pluralizzati, minuscoli e in `kebab-case`;
- path per risorse, non verbi, salvo comandi non modellabili come risorsa;
- major nel path; minor/patch nel contratto;
- niente estensioni `.json` o dettagli di storage;
- nested path solo per ownership stabile e utile all'authorization;
- profondità raccomandata massima di tre risorse;
- query parameter in `camelCase`;
- timestamp RFC 3339 con offset, UTC quando l'offset sorgente non va preservato;
- decimal solo con precisione/range definiti; identificatori numerici come stringhe.

```text
GET  /api/v1/tenants/{tenantId}/facilities
POST /api/v1/deployments
GET  /api/v1/operations/{operationId}
POST /api/v1/replay-jobs
GET  /api/v1/dataset-versions/{datasetVersionId}
```

## 7. Semantica HTTP

| Metodo | Uso HHC | Retry |
|---|---|---|
| GET | lettura senza side effect business | sicuro entro policy cache/rate |
| HEAD | metadata senza representation | come GET |
| POST | creazione server-ID o comando/job | solo con idempotency contract |
| PUT | sostituzione completa a URI noto | idempotente, con precondizione |
| PATCH | modifica parziale esplicita | con ETag; formato dichiarato |
| DELETE | richiesta di lifecycle/delete | idempotente come intento, auditata |

GET non attiva deployment, replay, export o mutazioni. DELETE di asset governati avvia normalmente decommission/tombstone: non cancella evidenze soggette a retention.

### 7.1 Status code

| Codice | Uso |
|---|---|
| 200 | lettura o comando sincrono completato |
| 201 | risorsa creata; `Location` obbligatorio |
| 202 | job accettato; operation URL obbligatoria |
| 204 | successo senza representation |
| 304 | conditional GET non modificato |
| 400 | sintassi/parametri invalidi |
| 401 | autenticazione assente/non valida |
| 403 | identità valida ma azione negata |
| 404 | assente o non divulgabile allo scope |
| 409 | conflitto di stato/idempotency collision |
| 412 | precondizione/ETag fallita |
| 413 | payload oltre limite |
| 415 | media type non supportato |
| 422 | sintassi valida, semantica non accettabile |
| 428 | precondizione obbligatoria assente |
| 429 | quota/rate limit; `Retry-After` quando noto |
| 503 | dipendenza o capacità temporaneamente indisponibile |

Un `202` non significa successo business: il client segue l'operation fino a stato terminale.

## 8. Header comuni

| Header | Direzione | Obbligo |
|---|---|---|
| `Authorization` | request | token previsto dal profilo |
| `traceparent` | entrambe | W3C; rigenerato se invalido/non fidato |
| `tracestate` | entrambe | allowlist e limiti; mai PHI |
| `X-Correlation-ID` | entrambe | opaco; generato se assente |
| `Idempotency-Key` | mutation selezionate | obbligatorio per POST retryable |
| `If-Match` | mutation concorrenti | obbligatorio su asset governati |
| `ETag` | response | versione resource/representation |
| `Location` | response 201/202 | URI risorsa/operation |
| `Retry-After` | response 429/503 | secondi o HTTP-date |
| `Deprecation` | response | RFC 9745 |
| `Sunset` | response | RFC 8594 |

Header di tenant/facility non sono trusted identity. PHI, token, secret e patient ID non devono comparire in header di telemetria o URL.

## 9. Idempotenza

Ogni operazione mutabile dichiara:

- `naturally-idempotent` — PUT/DELETE deterministico;
- `keyed-idempotent` — POST con `Idempotency-Key`;
- `non-retryable` — retry automatico vietato, reconciliation su esito ambiguo.

Per `keyed-idempotent`, il server persiste nello stesso boundary transazionale subject/client, tenant, operation, chiave, hash canonico della richiesta, stato/response e scadenza. Stessa chiave e hash restituiscono l'esito precedente; stessa chiave con hash diverso produce `409`. La chiave è scoped e non sostituisce la business key.

Timeout o connessione chiusa non dimostrano che l'operazione non sia avvenuta. Il client interroga operation/resource, riconcilia l'effetto e ritenta solo se consentito. HHC non promette exactly-once end-to-end generico: usa at-least-once, idempotenza, ledger e riconciliazione.

## 10. Concorrenza e consistenza

- asset governati espongono ETag forte sulla versione logica;
- PATCH/PUT/DELETE richiedono `If-Match`; assenza `428`, conflitto `412`;
- list/query possono essere eventualmente consistenti dichiarando freshness;
- command acceptance e read model possono convergere: `operationId` governa il comando;
- nessuna last-write-wins silenziosa per flow, mapping, policy, deployment o consent;
- batch multi-risorsa è atomico solo se dichiarato, altrimenti esito per item.

## 11. Error model RFC 9457

```json
{
  "type": "https://docs.hhc.example/problems/precondition-failed",
  "title": "Precondition failed",
  "status": 412,
  "detail": "The resource changed after it was read.",
  "instance": "urn:hhc:problem:01...",
  "code": "HHC-API-412-001",
  "correlationId": "01...",
  "retryable": false,
  "violations": [
    {"path": "/displayName", "code": "conflict", "message": "Value is stale"}
  ]
}
```

- `type` è URI stabile e documentata;
- `code` è machine-readable e non riutilizzato;
- `detail` non contiene stack, SQL, hostname interno, token o PHI;
- deny può apparire come 404 per evitare enumeration;
- `retryable` deriva dalla failure taxonomy, non dal solo status;
- protocolli clinici mantengono l'errore nativo e una normalizzazione interna correlata;
- la localizzazione non cambia `type` o `code`.

## 12. Validazione e protezione risorse

- content type/charset allowlist;
- limiti a body, header, URI, nesting, array, field count e decompression ratio;
- parser sicuro: niente XXE, script o deserializzazione polimorfica non autorizzata;
- JSON Schema/OpenAPI più invariant business;
- normalizzazione Unicode senza alterare il raw clinico;
- timeout/complexity budget per regex, query, expansion e filter;
- upload streaming con checksum e scan dove applicabile;
- SSRF protection per URL/reference con scheme/host/port allowlist e difesa DNS rebinding.

Il raw clinico accettato è immutabile; la validazione produce finding e non modifica silenziosamente il messaggio.

## 13. Collection, ricerca e paginazione

Default per collection mutabili:

```json
{
  "items": [],
  "page": {"nextCursor": "opaque-signed-value", "limit": 100, "hasMore": true}
}
```

Il cursor è opaco, firmato, scoped a subject/tenant/query/sort e con TTL; non contiene PHI. Offset pagination è ammessa per cataloghi piccoli e stabili, non per audit/eventi.

Filtri e sort usano allowlist, sort deterministico con tie-breaker, limite massimo/default, query timeout e cost budget. SQL/espressioni arbitrarie sono vietati. Il count totale è opzionale perché costoso o sensibile.

## 14. Operazioni asincrone

Deploy, replay, export, cohort, DQD e characterization restituiscono `202` e una operation:

```json
{
  "operationId": "op-01...",
  "type": "deployment",
  "status": "QUEUED",
  "scope": {"tenantId": "tn-..."},
  "requestedAt": "2026-09-01T10:00:00Z",
  "requestedBy": "subject-opaque",
  "progress": {"completed": 0, "total": null},
  "links": {"self": "/api/v1/operations/op-01..."}
}
```

Stati comuni: `QUEUED → RUNNING → SUCCEEDED | FAILED | CANCELLED | EXPIRED`; è ammesso `CANCELLING`. Cancel è best effort e non implica rollback degli effetti committati. Ogni job ha deadline, checkpoint, owner, priorità, quota, result manifest e failure detail. Poll usa backoff; webhook/event callback è firmato e idempotente.

## 15. Bulk transfer

- export/import sempre come job governati;
- manifest con part, content type, record/byte count, checksum ed expiry;
- URL prefirmati con scope/TTL minimi, mai nei log;
- resume/range solo con integrità per part;
- cifratura, residency e purpose ereditati;
- cancel/revoke non finge di cancellare copie già scaricate;
- grandi dataset FHIR usano Bulk Data solo se qualificato;
- export analitico applica disclosure control e permit, mai dump SQL.

## 16. Autenticazione

### 16.1 Utenti

- OIDC/OAuth 2.0 authorization code con PKCE;
- MFA/step-up per privilegi;
- sessioni brevi e revocabili, CSRF protection per cookie;
- redirect URI esatte, state/nonce e issuer validation;
- niente Resource Owner Password Credentials;
- refresh token rotation o sender constraint secondo rischio.

### 16.2 Workload

- workload identity e mTLS interni;
- OAuth client credentials/private key JWT per partner;
- audience specifica, TTL breve e scope minimo;
- mTLS-bound token o DPoP per client ad alto rischio compatibili;
- key rotation sovrapposta e revoca esercitata;
- API key semplice mai unico controllo per PHI/amministrazione.

### 16.3 Token

Algoritmi allowlist; rifiuto di `none` e algorithm confusion; `iss`, `aud`, `exp`, `nbf`, subject/client, token type e signature obbligatori. JWKS cache ha freshness e fail behavior espliciti. Token non è inoltrato indiscriminatamente.

## 17. Autorizzazione e policy

Authorization deny-by-default su ruolo/capability, gerarchia tenant, ownership, field sensitivity, purpose, consent/legal basis, environment/posture, step-up/separation of duties, residency, export e volume.

La decisione registra policy version, permit/deny, obligation e decision ID. Le list API filtrano prima della paginazione. Break-glass è limitato, motivato, temporalmente circoscritto, notificato e auditato.

## 18. Privacy e dati sanitari

- minimizzazione per response e scope;
- field projection solo da allowlist;
- PHI vietata in URI, metric label, trace baggage ed errori;
- response sensibili non condivisibili in cache; `Cache-Control` esplicito;
- export con purpose, dataset, destinatario, scadenza e approvazione;
- support bundle redatto e classificato;
- accesso raw eccezionale e auditato ad alta severità;
- dati sintetici per test;
- diritti, retention, legal hold e cancellazione come workflow governati.

## 19. Event API

AsyncAPI descrive applicazione, server, channel, operation, message, schema, security e binding. CloudEvents può standardizzare l'envelope esterno, ma HHC mantiene l'[Integration Envelope](../canonical-model/integration-envelope.md) come autorità di tenancy, lifecycle, lineage e delivery.

| CloudEvents | HHC |
|---|---|
| `id` | publication ID, non necessariamente `messageId` |
| `source` | URI producer HHC non PHI |
| `type` | event type versionato |
| `subject` | riferimento opaco, mai patient ID in chiaro |
| `time` | publication time; event time resta nell'envelope |
| `dataschema` | contract URI immutabile |
| `traceparent` | trace context filtrato |

Il contratto dichiara partition/ordering key, delivery class, dedup window, schema compatibility, max size, retention, retry, DLQ, replay e authorization. CloudEvents non definisce il processing model e non crea exactly-once.

## 20. Versionamento e compatibilità

- SemVer per contratti e SDK;
- major nel path REST;
- type/schema version per eventi;
- package/version/digest per FHIR IG e terminologie;
- artifact pubblicati immutabili.

Compatibile in minor: nuovo campo response opzionale, endpoint/operation, enum solo se consumer tollera unknown, rilassamento non-security di un limite. Breaking: rimozione/rinomina/tipo, request field reso required, cambio semantica/authorization/side effect, riduzione limiti incompatibile, enum chiuso, cambio precisione/unità/significato clinico.

Il registry esegue diff sintattico e semantico. HHC supporta N/N-1 per Control Plane/Runtime secondo support matrix, non indefinitamente.

### 20.1 Deprecation

Ogni deprecazione include `Deprecation`, eventuale `Sunset`, link `rel="deprecation"`, replacement, esempi/test, telemetria consumer senza PHI, periodo di supporto, avviso ai consumer critici e prova di ritiro/rollback.

## 21. Contract registry e lifecycle

`DRAFT → REVIEW → QUALIFIED → ACTIVE → DEPRECATED → RETIRED`

Ogni contract contiene owner, version, dialect, digest, signature, data classification, scope, SLO tier, compatibility rule, examples/negative examples, security scheme e Conformance Pack.

Promotion: lint con parser indipendenti, reference resolution controllata, cycle/resource exhaustion check, contract/security/fuzz/compatibility test, threat model, classification, approvazioni e signing/provenance.

OpenAPI consente Markdown e reference esterne: rendering e dereference devono essere sanitizzati e allowlisted contro script, SSRF e resource exhaustion.

## 22. Observability

Ogni richiesta/evento espone senza PHI request/operation/correlation/trace ID, API/version/operation ID, consumer class, scope opaco ammesso, status/error/retryability, latency histogram, byte bucket, auth outcome/policy decision, quota/circuit e dependency health.

Metriche minime: RPS/EPS, success/error, p50/p95/p99, saturation, active request, queue depth/age, rate deny, auth deny, idempotency replay/collision e schema failure.

## 23. Audit

Sono auditati login e privilegi; CRUD di asset/policy; publish/deprecate/retire; deploy/rollback; replay/reprocess/export; accesso raw/PHI/audit/lineage; break-glass; bulk query; secret/trust change; connector install/enable/disable.

Audit append-only/tamper-evident e separato dai log: actor/workload, scope, purpose, action, target, before/after digest, outcome, policy version, timestamp e correlation ID. Non contiene secret o copie non necessarie del payload.

## 24. Affidabilità e business continuity

- gateway/servizi stateless multi-zona;
- datastore con failover, PITR, fencing e restore;
- timeout finiti, retry idempotenti con backoff/jitter/budget;
- circuit breaker e bulkhead per dipendenza/tenant;
- cache con version/freshness/fail behavior;
- graceful shutdown, readiness e drain;
- checkpoint/resume di operation/job;
- rate limit per consumer, tenant, operation e costo;
- nessuna chiamata Control Plane sincrona nel Tier 0.

| Classe | SLO | RTO | RPO |
|---|---:|---:|---:|
| Tier 0 clinical API | 99,99% | ≤30 min | cross-site ≤5 min; locale 0 dopo ACK |
| Tier 1 | 99,95% | ≤2 h | ≤15 min |
| Tier 2 Analytics | 99,9% | ≤8 h | ≤1 h |
| Control Plane | 99,9% | ≤4 h | ≤15 min |

Le Runtime Cell operano almeno 24 ore senza Control Plane. Il DR drill verifica endpoint, identity, policy cache, secret, operation ledger, audit, DNS e client trust, non solo il DB.

## 25. Performance e limiti

- query Control Plane indicizzate p95 ≤500 ms nella reference topology;
- almeno 100 organization, 1.000 facility, 10.000 endpoint e 20.000 flow per tenant di riferimento;
- max body/page/concurrency/timeout/rate/cost pubblicati;
- streaming per oggetti grandi;
- N+1 e hot-tenant test;
- overload esplicito 429/503, non timeout indefinito;
- Analytics/bulk in pool separati.

Il Data Plane segue [performance-and-chaos.md](../testing/performance-and-chaos.md): HL7 v2 ≤64 KiB con overhead HHC p95 ≤50 ms/p99 ≤100 ms e Reference Runtime Cell ≥2.000 eventi/s da 8 KiB per 60 minuti nelle condizioni dichiarate.

## 26. Testing e release gate

OpenAPI/AsyncAPI/Schema lint e parse indipendente; contract positive/negative; consumer-driven; authorization object/function/property; cross-tenant/enumeration; idempotency/concurrency/ambiguous outcome; fuzz/injection/SSRF/exhaustion; fairness; dependency loss/failover/restore/N/N-1; audit coverage/PHI canary; developer portal accessibility; SDK generation smoke test.

Si applicano [test-strategy.md](../testing/test-strategy.md) e [conformance-and-interoperability.md](../testing/conformance-and-interoperability.md).

## 27. Casi d'uso end-to-end

### API-UC-01 — Deploy Tier 0 multifacility

Un Integration Engineer pubblica un flow su venti facility, con canary su due Runtime Cell.

**Verifiche:** ETag, dual control, idempotency, operation asincrona, bundle firmato, residency, stato per target, arresto, rollback, audit e nessun impatto ai flow attivi.

### API-UC-02 — Replay dopo ACK ambiguo

Replay di risultati laboratorio dopo timeout del destinatario.

**Verifiche:** preview, doppia approvazione, ledger, stato `UNKNOWN`, reconciliation prima del resend, quota, idempotenza, raw link, audit e alert.

### API-UC-03 — Uso secondario governato

Un ricercatore avvia una coorte su DatasetVersion OMOP `READY` per scopo approvato.

**Verifiche:** permit/purpose/scadenza, template, job, disclosure control, niente SQL, manifest, export cifrato/TTL, revoca e audit.

### API-UC-04 — Consumer deprecato

Un sistema regionale usa v1 mentre v2 è attiva.

**Verifiche:** coesistenza, Deprecation/Sunset, migration link, telemetria, N/N-1, nessun cambio silenzioso e gate al ritiro.

### API-UC-05 — Hot tenant malevolo

Un tenant invia query costose e payload profondi.

**Verifiche:** complexity/body limit, quota gerarchica, 413/422/429, isolamento, audit e nessun dettaglio interno.

### API-UC-06 — Control Plane offline

WAN centrale interrotta per 24 ore.

**Verifiche:** API admin indisponibili/read-only; Runtime Cell operative; bundle valido; nuove config respinte; audit locale e riconvergenza.

### API-UC-07 — Rotazione identità FHIR

Rotazione CA/chiavi di un connector senza downtime.

**Verifiche:** trust overlap, audience/scope, sender constraint, revoke, drain, alert e niente fallback debole.

### API-UC-08 — Country pack aggiornato

Una IG europea/nazionale cambia versione.

**Verifiche:** package/digest, coesistenza, validator, terminology snapshot, canary partner, isolamento adapter, claim e rollback.

## 28. Anti-pattern vietati

- tenant deciso solo da header/body;
- API admin che accetta PHI non necessaria;
- POST ritentato senza idempotency/reconciliation;
- SQL/tabelle/stack trace esposti;
- offset pagination su audit/eventi;
- token generico multi-audience e lungo;
- breaking change nascosta in minor;
- contract sovrascritto in place;
- log request/response completo di default;
- retry infinito/indifferenziato;
- readiness falsa;
- deprecazione solo via email;
- exactly-once generico non dimostrato.

## 29. Fonti ufficiali e data di verifica

Fonti verificate il **1 settembre 2026**:

- OpenAPI 3.2.0: <https://spec.openapis.org/oas/v3.2.0.html>
- AsyncAPI 3.1.0: <https://www.asyncapi.com/docs/reference/specification/latest>
- JSON Schema 2020-12: <https://json-schema.org/draft/2020-12>
- HTTP Semantics RFC 9110: <https://www.rfc-editor.org/rfc/rfc9110.html>
- Problem Details RFC 9457: <https://www.rfc-editor.org/rfc/rfc9457.html>
- OAuth Security BCP RFC 9700: <https://www.rfc-editor.org/rfc/rfc9700.html>
- OAuth mTLS RFC 8705: <https://www.rfc-editor.org/rfc/rfc8705.html>
- DPoP RFC 9449: <https://www.rfc-editor.org/rfc/rfc9449.html>
- JWT BCP RFC 8725: <https://www.rfc-editor.org/rfc/rfc8725.html>
- Deprecation RFC 9745 e Sunset RFC 8594: <https://www.rfc-editor.org/rfc/rfc9745.html>, <https://www.rfc-editor.org/rfc/rfc8594.html>
- CloudEvents 1.0.2: <https://github.com/cloudevents/spec>
- W3C Trace Context: <https://www.w3.org/TR/trace-context/>
- OpenTelemetry 1.60.0: <https://opentelemetry.io/docs/specs/otel/>
- Semantic Versioning 2.0.0: <https://semver.org/>
- HL7 FHIR Bulk Data Access 3.0.0: <https://hl7.org/fhir/uv/bulkdata/>

Le fonti ufficiali prevalgono su generatori e documentazione vendor. La release BOM registra versione esatta, digest, compatibilità e data di qualificazione degli strumenti realmente usati.
