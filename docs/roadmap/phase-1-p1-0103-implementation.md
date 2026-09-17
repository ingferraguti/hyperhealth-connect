# P1-0103 — Scope Tenant/Facility in repository, service e API

| Campo | Valore |
|---|---|
| Stato | IMPLEMENTED |
| Work package | WP1-01 |
| Requisiti | FR-TEN-001, FR-TEN-002, NFR-SEC-001, NFR-SCL-001 |
| Decisioni | [ADR-009](../adr/ADR-009-identity-and-tenancy.md), [ADR-025](../adr/ADR-025-phase-1-technology-baseline.md) |
| Data | 14 settembre 2026 |
| Dati di test | esclusivamente sintetici |

## Risultato

P1-0103 introduce il primo percorso verticale di lettura nel quale lo scope Tenant/Facility è obbligatorio e mantiene lo stesso tipo dal boundary HTTP fino alla query PostgreSQL:

```text
scope verificato server-side
    → argument resolver API
    → ScopedInventoryService
    → ScopedEndpointRepository
    → prepared statement con tenant_id + facility_id + endpoint_id
```

Il vertical slice legge un Endpoint perché è il livello più profondo della gerarchia e consente di provare contemporaneamente Tenant, Facility, ancestry completa e non-enumerabilità. Creazione, collection, pagination, filtro, ETag e idempotenza sono state successivamente consegnate da [P1-0106](phase-1-p1-0106-implementation.md).

## Invarianti

1. nessun metodo pubblico del repository può essere invocato senza `VerifiedFacilityScope` come primo parametro;
2. service e controller non definiscono scope globale, default o nullable;
3. Tenant e Facility usano ID tipizzati, non stringhe arbitrarie;
4. il repository confronta Tenant, Facility e resource ID nella stessa query preparata;
5. lo scope non viene derivato da header, query string, path o body controllati dal client;
6. scope mancante interrompe la richiesta prima dell'accesso persistence;
7. risorsa inesistente, dismessa o esterna allo scope produce la stessa risposta `404`;
8. una response negata non contiene ID, display name o altri campi della risorsa richiesta;
9. gli errori sono Problem Details con codice stabile, correlation ID server-side e nessun dettaglio SQL;
10. il pool JDBC è bounded e, alla consegna di P1-0103, la feature era fail-closed/disabilitata in attesa del boundary OIDC poi realizzato da P1-0104.

## Contratto dello scope

`VerifiedFacilityScope` contiene soltanto `TenantId` e `FacilityId` non null. Il nome “verified” descrive la provenance attesa: il valore deve essere prodotto da un trust boundary server-side dopo autenticazione e verifica dei grant.

`VerifiedFacilityScopeArgumentResolver` cerca il valore in un attributo request interno identificato dal nome canonico della classe. Non legge `X-Tenant-ID`, `X-Facility-ID` o equivalenti. Un client HTTP non può impostare un request attribute del servlet; gli header omonimi restano dati non autorevoli e vengono ignorati.

[P1-0104](phase-1-p1-0104-implementation.md) ha successivamente realizzato il producer dell'attributo tramite OIDC/workload identity. `hhc.inventory.enabled=false` e `hhc.security.oidc.enabled=false` restano default indipendenti: l'API è deny-all finché non viene configurato un trust profile completo, e il controller inventory non viene pubblicato finché il relativo vertical slice non è abilitato.

## Repository

`ScopedEndpointRepository` espone un solo metodo:

```java
Optional<ScopedEndpoint> findEndpoint(
        VerifiedFacilityScope scope,
        EndpointId endpointId);
```

`JdbcScopedEndpointRepository` usa una statement preparata e applica i predicate prima della materializzazione:

```sql
WHERE tenant_id = ?
  AND facility_id = ?
  AND endpoint_id = ?
  AND lifecycle_state <> 'DECOMMISSIONED'
```

L'Endpoint ID è globalmente univoco, ma il predicate non si affida a questa proprietà per l'isolamento. Tenant e Facility restano visibili nella query e nel read model, permettendo verifica e successive policy defense-in-depth. Il database di P1-0102 garantisce che l'ancestry persistita non possa essere cross-tenant.

Il repository restituisce `Optional.empty()` per record assente, dismesso o non appartenente allo scope. Non esegue prima un lookup unscoped per distinguere i casi: questo evita un data oracle e garantisce che display name e ancestry non vengano caricati fuori scope.

## Service

`ScopedInventoryService` richiede lo scope nel metodo pubblico, lo valida prima di chiamare il repository e traduce un risultato vuoto in `InventoryResourceNotFoundException`. L'eccezione non incorpora resource ID o scope; controller e log futuri non ricevono quindi informazioni esterne al perimetro autorizzato.

Non esiste un overload senza scope. Un test riflessivo blocca l'introduzione di nuovi metodi repository che non abbiano `VerifiedFacilityScope` come primo parametro.

## API

Il vertical slice espone:

```text
GET /api/v1/endpoints/{endpointId}
```

L'ID usa la forma canonica `ep-{uuid}`. La response positiva contiene solo metadata inventory:

```json
{
  "endpointId": "ep-00000000-0000-4000-8000-000000000001",
  "tenantId": "t-00000000-0000-4000-8000-000000000002",
  "organizationId": "o-00000000-0000-4000-8000-000000000003",
  "facilityId": "f-00000000-0000-4000-8000-000000000004",
  "applicationId": "a-00000000-0000-4000-8000-000000000005",
  "displayName": "HHC-SYNTHETIC LIS inbound",
  "lifecycleState": "ACTIVE",
  "rowVersion": 7
}
```

Non vengono esposti address, credential reference, secret o payload clinici. La tabella seguente definisce gli errori consegnati da P1-0103:

| Condizione | HTTP | Codice | Proprietà |
|---|---:|---|---|
| scope verificato assente | 401 | `HHC-INV-401-001` | repository non invocato |
| ID non canonico | 400 | `HHC-INV-400-001` | nessun lookup DB |
| assente, dismesso o cross-scope | 404 | `HHC-INV-404-001` | dettaglio uniforme |

Ogni errore usa `application/problem+json`, `type` stabile, `instance` opaca, `retryable=false` e un correlation ID generato dal server, riportato anche in `X-Correlation-ID`.

## Pool e configurazione

La feature si abilita con `HHC_INVENTORY_ENABLED=true` e richiede URL JDBC, username e password forniti dall'ambiente o dal secret provider:

| Proprietà | Default | Controllo |
|---|---:|---|
| maximum pool size | 20 | intervallo 1–256 |
| minimum idle | 2 | tra 0 e maximum |
| pool wait timeout | 5 s | almeno 250 ms e bounded |
| connection validation timeout | 2 s | almeno 250 ms e inferiore al pool wait |
| max lifetime | 30 min | almeno 30 s e maggiore del keepalive |
| keepalive | 5 min | almeno 30 s e inferiore al max lifetime |

HikariCP viene inizializzato fail-fast entro il connection timeout, identifica le sessioni come `hhc-control-plane` e abilita TCP keepalive. Il dimensionamento definitivo dipende da replica count, limite connessioni del writer, workload e headroom per migration/operations; non si moltiplica il default per replica senza capacity review.

Il runtime principal è distinto dal migration principal e non deve avere DDL, ownership dello schema o facoltà di disabilitare trigger. P1-0103 non esegue Flyway all'avvio dell'applicazione: la migration resta un'operazione single-writer controllata come definito da P1-0102.

Per HA l'URL JDBC e il servizio di discovery sono configurazione dell'ambiente qualificato. Pool e retry non sostituiscono writer fencing, timeout end-to-end, replica monitoring o restore. In caso di Platform DB indisponibile il Control Plane fallisce la lettura; le Runtime Cell continuano dal bundle locale secondo ADR-028, senza allargare lo scope.

## Verifica

| Suite | Scenari |
|---|---|
| `JdbcScopedEndpointRepositoryTest` | PostgreSQL 18.6 reale, scope valido, sibling facility, altro Tenant, coppia Tenant/Facility incoerente, dismesso, percorso end-to-end fino all'API |
| `ScopedEndpointControllerTest` | scope server-side, spoofed header, `401`, `404` uniforme, ID non canonico, response positiva e correlation ID |
| `ScopedInventoryServiceTest` | null scope negato prima della persistence e firma scope-first dei metodi repository |
| `ScopedInventoryConfigurationTest` | pool bounded e relazioni timeout coerenti |
| `ControlPlaneApplicationTest` | inventory non pubblicata quando la feature è disabilitata per default |

Tutti i fixture sono marcati `HHC-SYNTHETIC`. Il test PostgreSQL usa la stessa immagine 18.6 digest-pinned della baseline; non usa H2 o un sostituto SQL.

## Prestazioni, resilienza e auditabilità

- lookup per ID usa primary key e predicate scoped nella stessa statement;
- statement preparata evita SQL dinamico e riduce rischio injection;
- il pool è bounded per evitare esaurimento del writer da parte di una singola replica;
- timeout e failure di connessione non attivano fallback unscoped o cache globale;
- il correlation ID consente di collegare errore API e futura telemetria senza includere PHI;
- P1-0103 non includeva audit; P1-0107 ha poi consegnato gli accessi Endpoint e P1-0108 ha qualificato i deny pre-controller, la cui registrazione indipendente resta WP1-10;
- benchmark, noisy-neighbor e piano `EXPLAIN` sul seed multi-tenant restano parte di P1-0110/G2.

## Confini deliberati

Non sono inclusi:

- autenticazione OIDC, RBAC minimo e workload identity, successivamente introdotti da P1-0104; l'ABAC completo resta nei work item di policy/authorization successivi;
- secret reference scoped, schema senza valore/locator ed export con rebinding obbligatorio, successivamente introdotti da P1-0105; resolver provider e rotazione end-to-end restano nei work item runtime/deployment;
- create/update/decommission, collection, pagination, ETag e idempotenza, successivamente consegnati da P1-0106;
- audit journal Endpoint, successivamente consegnato da P1-0107;
- matrice negativa per i layer del vertical slice, consegnata da P1-0108; RLS defense-in-depth resta un hardening separato non ancora implementato;
- seed e benchmark su scala, previsti da P1-0110.

P1-0103 non chiudeva Gate G2 e non rendeva utilizzabile l'API senza P1-0104. Dopo P1-0104 il vertical slice può essere abilitato soltanto con OIDC configurato; P1-0105 ha aggiunto la persistence scoped delle secret reference, P1-0106 le collection/mutazioni Endpoint, P1-0107 il relativo journal e P1-0108 la matrice negativa. G2 rimane aperto per P1-0109, P1-0110 e la qualification prevista.

## Fonti ufficiali

Verificate il 14 settembre 2026:

- [Spring Framework JDBC core](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html), per statement preparate, mapping e gestione JDBC;
- [Spring Framework 7.0.9](https://github.com/spring-projects/spring-framework/releases/tag/v7.0.9), versione governata dal BOM Spring Boot 4.1.1;
- [HikariCP 7.0.2](https://github.com/brettwooldridge/HikariCP/releases/tag/HikariCP-7.0.2) e [configurazione ufficiale](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby), per semantica del pool e timeout;
- [pgJDBC connection parameters](https://jdbc.postgresql.org/documentation/use/), per proprietà della connessione PostgreSQL;
- [RFC 9457 — Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html), per il formato degli errori HTTP.
