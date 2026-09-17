# P1-0106 — API inventory v0.2: pagination, filtering, concurrency e idempotenza

| Campo | Valore |
|---|---|
| Work package | WP1-01 — Tenancy, identità e Platform DB |
| Stato | IMPLEMENTED |
| Data baseline | 16 settembre 2026 |
| Perimetro consegnato | API Endpoint facility-scoped v0.2 |
| Classificazione dati | metadata tecnico; PHI e secret vietati |
| Owner | Platform Engineering |

## 1. Outcome e confine

P1-0106 trasforma il percorso di sola lettura P1-0103 nel primo vertical slice inventory modificabile e utilizzabile da client enterprise. Il percorso copre `Endpoint`, cioè il livello più profondo della gerarchia già persistita, e mantiene obbligatorio lo scope firmato `Tenant/Facility` in controller, service e repository.

Sono consegnati:

1. `GET /api/v1/endpoints/{endpointId}` con ETag forte;
2. `GET /api/v1/endpoints` con keyset pagination, limiti bounded e filtri allowlisted;
3. `POST /api/v1/endpoints` con ID server-side e idempotenza transazionale;
4. `PATCH /api/v1/endpoints/{endpointId}` in JSON Merge Patch con `If-Match` obbligatorio;
5. rename, sospensione, riattivazione e decommission terminale senza hard delete;
6. authority `HHC_INVENTORY_WRITE` distinta dalla lettura;
7. migration additive del registro idempotenza e indici concorrenti per le collection;
8. Problem Details stabili, non enumerabili e privi di SQL, secret e metadata negati.

Il contratto machine-readable è [inventory-endpoints-v1.openapi.yaml](../api/openapi/inventory-endpoints-v1.openapi.yaml); la documentazione narrativa resta autoritativa per failure, rollout e limiti non rappresentabili nello schema.

Il completamento di P1-0106 non dichiara concluse le API amministrative di Tenant, Organization, Facility, Application o Runtime Cell. Queste richiedono scope di autorizzazione superiori a `VerifiedFacilityScope`, deleghe e policy non ancora modellate; pubblicarle ora con il token facility-scoped creerebbe un boundary incoerente. P1-0107 ha successivamente consegnato l'audit append-only, P1-0108 la matrice negativa completa e P1-0109 la qualification Unicode/locale/timezone; P1-0110 (seed di scala) resta successivo.

## 2. Contratto HTTP

### 2.1 Lettura puntuale

```http
GET /api/v1/endpoints/ep-550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <token>
```

Una risposta corrente usa `200`, `Cache-Control: no-store` ed ETag forte derivato dalla versione logica:

```http
ETag: "rv-7"
```

Endpoint assente, dismesso o fuori scope produce lo stesso `404 HHC-INV-404-001`. La query SQL include sempre `tenant_id`, `facility_id` ed `endpoint_id`; l'unicità globale dell'ID non sostituisce lo scope.

### 2.2 Collection, filtri e cursor

```http
GET /api/v1/endpoints?applicationId=a-...&lifecycleState=ACTIVE&limit=50&cursor=...
```

Filtri ammessi:

| Parametro | Regola |
|---|---|
| `applicationId` | ID canonico `a-<uuid>` e parent nello stesso Tenant/Facility |
| `lifecycleState` | `ACTIVE` o `SUSPENDED`; il current view esclude sempre `DECOMMISSIONED` |
| `limit` | default 50, minimo 1, massimo 100 |
| `cursor` | token HMAC-SHA-256, TTL default 15 minuti |

La risposta non calcola un total count, che sarebbe costoso e potrebbe diventare un side channel:

```json
{
  "items": [],
  "page": {
    "nextCursor": null,
    "limit": 50,
    "hasMore": false
  }
}
```

L'ordinamento è `endpoint_id ASC`; la query legge `limit + 1` e usa l'ultimo ID restituito come boundary esclusivo. Il cursor contiene soltanto versione, expiry e boundary UUID. La firma lo lega a subject, authorized party, Tenant, Facility, filtri, limite e ordinamento: una modifica, il riuso da parte di altro soggetto/facility o la scadenza produce `400`. Non contiene scope in chiaro, PHI o secret.

La chiave di firma deve essere uguale tra repliche e siti attivi che servono la stessa API. È iniettata a runtime tramite `HHC_INVENTORY_CURSOR_SIGNING_KEY` in base64url, almeno 256 bit; non è memorizzata nel Platform DB né esportata. In produzione proviene dal secret manager tramite il meccanismo di delivery locale, viene ruotata con overlap di verifica in un incremento futuro e non viene inserita in manifest, log o evidence.

### 2.3 Creazione idempotente

```http
POST /api/v1/endpoints
Content-Type: application/json
Idempotency-Key: <UUIDv4 canonico generato per la richiesta>

{
  "applicationId": "a-...",
  "displayName": "LIS principale - inbound"
}
```

La chiave è un UUIDv4 canonico e casuale. La prima esecuzione restituisce `201`, `Location`, ETag e representation; lo stesso attore/client, scope, chiave e request digest restituisce esattamente lo snapshot originale, ancora con `201`, e aggiunge:

```http
Idempotency-Replayed: true
```

Il riuso della stessa chiave con body canonico diverso restituisce `409 HHC-CTRL-IDEMPOTENCY-COLLISION`. La finestra default è 24 ore, configurabile tra 1 ora e 7 giorni. Scaduta la finestra il record può essere eliminato e la chiave non deve essere riutilizzata dal client.

Il boundary PostgreSQL è una sola transazione:

1. rimuove l'eventuale claim scaduto con la stessa chiave scoped;
2. prova un `INSERT ... ON CONFLICT DO NOTHING` sul vincolo univoco;
3. se possiede il claim, verifica l'Application nello stesso Tenant/Facility, alloca l'ID, inserisce Endpoint e snapshot di risposta;
4. se perde il race, attende la decisione del vincolo univoco e legge l'esito committato;
5. un errore o crash prima del commit annulla claim, identità ed Endpoint insieme.

Il database conserva digest SHA-256 domain-separated di subject, chiave e request; non conserva subject o chiave raw. Lo snapshot ha colonne allowlisted per la sola representation Endpoint e non può contenere body arbitrari, payload clinici, locator o secret. `authorized_party` è conservato perché è un client ID allowlisted e serve a separare i namespace di retry.

### 2.4 PATCH e optimistic concurrency

```http
PATCH /api/v1/endpoints/ep-...
Content-Type: application/merge-patch+json
If-Match: "rv-7"

{
  "displayName": "LIS principale - inbound v2",
  "lifecycleState": "SUSPENDED"
}
```

Sono mutabili soltanto `displayName` e `lifecycleState`. Campi sconosciuti, valori JSON non stringa e `null` espliciti sono rifiutati atomicamente; ID, ancestry, secret binding e versioni non sono patchabili. `If-Match` accetta un solo ETag forte canonico `"rv-N"`, senza segno o zeri iniziali salvo `rv-0`: wildcard, weak tag e liste sono rifiutati.

| Condizione | Esito |
|---|---|
| `If-Match` assente | `428 HHC-INV-428-001` |
| ETag malformato | `400 HHC-INV-400-002` |
| versione non corrente | `412 HHC-CTRL-STALE-VERSION` |
| assente/fuori scope/dismesso | `404 HHC-INV-404-001` |
| transizione non ammessa | `409 HHC-INV-409-002` |

Le transizioni sono `ACTIVE ↔ SUSPENDED` e `ACTIVE|SUSPENDED → DECOMMISSIONED`. Decommission incrementa atomicamente `row_version`, valorizza i timestamp di Endpoint e identity ledger e rende il record invisibile al current view. Non esistono hard delete o riattivazione di un Endpoint dismesso.

## 3. Authorization e minimizzazione

| Ruolo | Lettura | POST/PATCH |
|---|---:|---:|
| `FacilityOperator` human | sì | sì |
| `Auditor` human | sì | no |
| `RuntimeAgent` workload | sì | no |
| `FlowDeveloper` human | no nel slice | no |

`HHC_INVENTORY_READ` e `HHC_INVENTORY_WRITE` sono capability interne, non scope OAuth accettati alla cieca. Lo scope continua a provenire esclusivamente dai claim firmati installati da P1-0104. Body, query o header `X-Tenant-ID`/`X-Facility-ID` non possono ampliare lo scope.

Le representation Endpoint non espongono address, credenziali o secret reference. `displayName` è metadata tecnico e il contratto vieta PHI/patient identifier. Tutte le risposte hanno `Cache-Control: no-store`; errori e deny non riportano ID negati, body, SQL, hostname, token o digest.

## 4. Persistenza, indici e scalabilità

| Migration | Contenuto | Rollout |
|---|---|---|
| `V005__expand__inventory_api_idempotency.sql` | registro ephemeral scoped, vincoli digest/completion/expiry, response snapshot allowlisted | transazionale e additive |
| `V006__expand__endpoint_api_indexes.sql` | indice Tenant/Facility/Endpoint e indice Tenant/Facility/Application/Endpoint | `CREATE INDEX CONCURRENTLY`, fuori transazione |

Gli indici sono partial sul current view e covering per le colonne restituite. La keyset pagination evita il costo crescente e lo shift di pagina degli offset su collection mutabili. Ogni statement JDBC ha timeout di 5 secondi; il pool resta bounded come in P1-0103. Il benchmark p95, hot tenant, explain plan su seed ≥10.000 Endpoint e tuning dei timeout sono gate P1-0110/qualification, non sono dedotti dai test funzionali.

Il registro idempotenza è parte del Platform DB e quindi del suo backup/PITR. Un failover su replica sincrona o un restore al recovery point conserva la stessa decisione di retry disponibile a quel punto. Se RPO > 0 perde un record recente, il client deve riconciliare la resource/business key prima di riprovare; HHC non dichiara exactly-once end-to-end. La pulizia dei record scaduti è bounded e separabile; non deve bloccare la path di creazione.

## 5. Failure behaviour, BC e DR

| Failure | Comportamento |
|---|---|
| due POST simultanei con stessa chiave/body | un solo commit; il secondo riproduce lo snapshot |
| stessa chiave/body diverso | nessun secondo effetto, `409` |
| crash prima del commit | nessun claim/identity/Endpoint parziale |
| risposta 201 persa | retry entro retention restituisce stesso ID/body/versione |
| PATCH simultanei | uno aggiorna; l'altro riceve `412` e deve rileggere |
| cursor alterato/scaduto/scope diverso | `400`, nessuna query con boundary non autenticato |
| database non disponibile | `503 HHC-INV-503-001`, dettaglio redatto e retry bounded |
| Control Plane indisponibile | nessun effetto sui flow Data Plane già distribuiti |

Il cursor signing key è requisito di startup quando l'inventory è abilitato: assenza, formato errato o meno di 256 bit fanno fallire chiuso la configurazione. Nei piani BC/DR la chiave è ripristinata dal secret manager; se viene deliberatamente sostituita, i cursor in-flight scadono in modo sicuro e i client ricominciano dalla prima pagina.

## 6. Casi d'uso sanitari europei 2026

### INV-UC-01 — onboarding LIS multi-facility

Un gruppo ospedaliero europeo configura Endpoint distinti per i LIS di quattro presidi sotto Application separate. L'operatore di una Facility vede e crea solo nel proprio scope; un Application ID di una facility sorella produce lo stesso 404 di un ID assente. Un retry dopo perdita della risposta non crea un secondo Endpoint.

### INV-UC-02 — sospensione durante manutenzione

Un `FacilityOperator` legge ETag `rv-12` e sospende l'Endpoint MLLP durante manutenzione. Un secondo operatore, ancora su `rv-12`, tenta un rename e riceve 412. Dopo reread applica intenzionalmente la modifica alla versione corrente; non esiste last-write-wins silenzioso.

### INV-UC-03 — dismissione del sistema legacy

Alla sostituzione di un LIS, l'Endpoint passa a `DECOMMISSIONED`. L'ID non viene riciclato, scompare dal current view e rimane referenziabile per history, binding e audit, successivamente consegnato da P1-0107. Una richiesta di riattivazione restituisce 404 e richiede una nuova risorsa governata.

### INV-UC-04 — inventario enterprise paginato

Un NOC consulta migliaia di Endpoint di una Facility filtrando per Application e stato. Cursor e query sono bounded e indicizzati; cambiare filtro, limite, utente o Facility invalida il cursor. Nessun count globale rivela la dimensione di altri tenant.

### INV-UC-05 — recovery dopo failover

Un POST viene committato ma la connessione cade prima che il client riceva 201. Dopo failover del Platform DB il client ritenta con la stessa chiave. Se il commit è nel recovery point riceve lo stesso snapshot; in caso contrario riconcilia per resource/business context prima di generare una nuova chiave.

## 7. Verifica ed evidence

La suite usa PostgreSQL 18.6 fissato per digest tramite Testcontainers e verifica:

- fresh migration e upgrade da V001 senza perdita;
- vincoli, indici validi e registro idempotenza;
- isolamento Tenant/Facility già presente sul read path;
- pagine keyset disgiunte, ordinate e filtrate;
- race concorrente con un solo Endpoint committato;
- replay identico e collisione body-differente;
- ETag forte, 428, 412 e decommission terminale;
- cursor round-trip, tamper, subject/scope/query substitution ed expiry;
- separazione read/write di `FacilityOperator`, `Auditor` e workload;
- Problem Details redatti e assenza di count/record cross-scope.

I test non sostituiscono performance qualification, chaos/failover reale, pen test indipendente, audit completeness o restore drill. Queste evidence restano nei gate successivi della Fase 1.

## 8. Configurazione operativa

| Variabile | Default | Regola |
|---|---|---|
| `HHC_INVENTORY_CURSOR_SIGNING_KEY` | nessuno | obbligatoria con inventory abilitato; base64url ≥32 byte |
| `HHC_INVENTORY_CURSOR_TTL` | `15m` | 1m–1h |
| `HHC_INVENTORY_IDEMPOTENCY_RETENTION` | `24h` | 1h–7d |
| `HHC_PLATFORM_DB_MAXIMUM_POOL_SIZE` | `20` | bounded, già validato |

La chiave non deve essere passata come argomento CLI, committata, inserita in ConfigMap o stampata. La telemetria usa solo code, latency, status, operation e scope pseudonimo a cardinalità controllata; non registra body, cursor, ETag ricevuti, Idempotency-Key o subject raw.

## 9. Rischi residui e attività successive

| Tema | Stato dopo P1-0106 | Chiusura |
|---|---|---|
| audit di create/read/list/update/decommission | consegnato da P1-0107 | [record P1-0107](phase-1-p1-0107-implementation.md) |
| deny OIDC/RBAC e invalid request pre-controller | esito qualificato da P1-0108, non ancora journalizzato | WP1-10 |
| matrice negativa per tutte le risorse/layer implementati | consegnata da P1-0108 | mantenimento continuo |
| Unicode/locale/timezone completa | consegnata con matrice e gate | [record P1-0109](phase-1-p1-0109-implementation.md) |
| seed e benchmark ≥10.000 Endpoint | non eseguito | P1-0110 e performance gate |
| cursor key rotation multi-key | singola chiave condivisa | hardening/operations increment |
| cleanup schedulato idempotency | expiry persistita e lazy cleanup per chiave | operations increment |
| API gerarchie superiori | non esposte con scope facility | authorization/admin API increment |
| property authorization secret reference | nessun campo secret esposto | futura secret management API |

WP1-01 e Gate G2 restano aperti fino a P1-0110 e relativa qualification; P1-0108 e P1-0109 sono consegnate.

## 10. Fonti ufficiali e data di verifica

Fonti verificate il **16 settembre 2026**:

- HTTP Semantics, ETag e `If-Match`, RFC 9110: <https://www.rfc-editor.org/rfc/rfc9110.html>
- status `428 Precondition Required`, RFC 6585: <https://www.rfc-editor.org/rfc/rfc6585.html>
- Problem Details, RFC 9457: <https://www.rfc-editor.org/rfc/rfc9457.html>
- PostgreSQL 18 `INSERT`, `ON CONFLICT` e `RETURNING`: <https://www.postgresql.org/docs/18/sql-insert.html>
- PostgreSQL 18 transaction isolation: <https://www.postgresql.org/docs/18/transaction-iso.html>
- PostgreSQL 18 `CREATE INDEX CONCURRENTLY`: <https://www.postgresql.org/docs/18/sql-createindex.html>
- Spring Framework, Problem Details per Spring MVC: <https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html>
- OWASP REST Security Cheat Sheet: <https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html>
- IETF HTTPAPI, `Idempotency-Key` draft history: <https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/history/>

Alla data di verifica il draft IETF `Idempotency-Key` -07 risulta scaduto e non è uno standard RFC. HHC usa quindi un contratto applicativo esplicito e testato, senza dichiarare conformità a uno standard inesistente; header, scope, fingerprint, expiry e collision semantics sono versionati nella propria API baseline.
