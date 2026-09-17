# P1-0102 — Schema SQL, migration e indici del Platform DB

| Campo | Valore |
|---|---|
| Stato | IMPLEMENTED |
| Work package | WP1-01 |
| Requisito primario | FR-TEN-001 |
| Requisiti collegati | FR-TEN-002, FR-TEN-003, NFR-SEC-001, NFR-SCL-001, NFR-BC-001 |
| Decisioni | [ADR-009](../adr/ADR-009-identity-and-tenancy.md), [ADR-025](../adr/ADR-025-phase-1-technology-baseline.md) |
| Baseline database | PostgreSQL 18.6 |
| Migration engine | Flyway Community 13.5.0 |
| Data di implementazione | 14 settembre 2026 |
| Ambito dati di test | esclusivamente sintetico |

## Risultato

P1-0102 rende persistente il modello di inventory introdotto da P1-0101. Lo schema `platform_core` rappresenta la gerarchia completa `Tenant → Organization → Facility → Application → Endpoint` e mantiene `Runtime Cell` come failure e scaling domain indipendente, associato a uno o più scope espliciti.

Le garanzie fondamentali non dipendono dalla sola correttezza del codice applicativo:

1. ogni identificativo UUID viene allocato una sola volta in un ledger append-only;
2. uno stesso UUID non può cambiare tipo né essere riutilizzato dopo una dismissione;
3. ogni relazione figlio–parent viene validata insieme alla sua ancestry completa;
4. ID e colonne di ancestry sono immutabili;
5. le righe inventory non sono eliminabili fisicamente: la dismissione è uno stato esplicito;
6. una Runtime Cell attiva deve avere almeno un'assegnazione di scope attiva;
7. le assegnazioni di scope sono storicizzate e non sovrascritte;
8. i principali access path scoped e operativi hanno indici dedicati e validati.

Al momento della consegna questo incremento non implementava repository, API, authorization OIDC/RBAC, secret reference, audit journal o row-level security. Il percorso scoped repository/service/API è stato successivamente introdotto da [P1-0103](phase-1-p1-0103-implementation.md), il boundary OIDC/RBAC da [P1-0104](phase-1-p1-0104-implementation.md), le secret reference scoped da [P1-0105](phase-1-p1-0105-implementation.md), la API Endpoint modificabile da [P1-0106](phase-1-p1-0106-implementation.md), il journal Endpoint da [P1-0107](phase-1-p1-0107-implementation.md) e la matrice negativa estesa da [P1-0108](phase-1-p1-0108-implementation.md). Il database impedisce ancestry incoerenti, ma non sostituisce la decisione autorizzativa prima dell'accesso al payload.

## Artifact consegnati

| Artifact | Funzione |
|---|---|
| `V001__expand__platform_core_inventory.sql` | introduce tabelle, check, foreign key inizialmente `NOT VALID`, funzioni e trigger di immutabilità nello schema creato da Flyway |
| `V002__migrate__validate_inventory_constraints.sql` | valida in modo esplicito tutti i constraint aggiunti nella fase expand |
| `V003__expand__inventory_query_indexes.sql` | crea gli indici operativi con `CREATE INDEX CONCURRENTLY` |
| `V003__expand__inventory_query_indexes.sql.conf` | esegue V003 fuori da una transaction block, come richiesto da PostgreSQL |
| `V004__expand__scoped_secret_references.sql` | aggiunta P1-0105: reference e binding Endpoint scoped, senza valore o locator provider, con indici e lifecycle di revoca |
| `V005__expand__inventory_api_idempotency.sql` | aggiunta P1-0106: claim idempotenza scoped, digest e response snapshot Endpoint allowlisted |
| `V006__expand__endpoint_api_indexes.sql` | aggiunta P1-0106: indici concorrenti covering per keyset page di Facility e Application |
| `PlatformCoreMigrationTest` | prova fresh install, upgrade N-1, catalogo, constraint, immutabilità e Runtime Cell su PostgreSQL reale |
| `governance/platform-db-schema.yml` | pubblica digest aggregato e SHA-256 di migration/configurazione per evidenza e drift detection |
| `scripts/phase1-p1-0102-gate.ps1` | verifica policy, naming, DDL non distruttivo e digest; produce evidenza JSON in CI |

Le migration sono sotto `platform/control-plane/src/main/resources/db/migration`. Sono forward-only: dopo la pubblicazione non vanno modificate; qualsiasi correzione richiede una nuova versione e preserva la checksum history Flyway.

## Modello fisico

### Ledger delle identità

`resource_identity` è il registro globale delle allocazioni. La primary key è il solo `resource_id`, mentre la coppia `(resource_id, resource_type)` è referenziata da ogni tabella concreta. Ne derivano due proprietà distinte:

- unicità globale dell'UUID tra tutti i tipi di risorsa;
- impossibilità di collegare, per esempio, un'identità `TENANT` a una riga `ORGANIZATION`.

La riga del ledger non viene cancellata. `allocated_at` e `decommissioned_at` consentono di conservare il tombstone tecnico senza utilizzare identificativi sanitari o contenuto clinico. UUID nil, tipi sconosciuti e timestamp di dismissione antecedenti all'allocazione sono rifiutati.

### Inventory gerarchica

| Tabella | Chiave | Ancestry persistita | Parent constraint |
|---|---|---|---|
| `tenant` | `tenant_id` | — | — |
| `organization` | `organization_id` | `tenant_id` | FK a Tenant |
| `facility` | `facility_id` | `tenant_id`, `organization_id` | FK composita a Organization |
| `application` | `application_id` | `tenant_id`, `organization_id`, `facility_id` | FK composita a Facility |
| `endpoint` | `endpoint_id` | ancestry completa fino ad `application_id` | FK composita a Application |

La duplicazione controllata dell'ancestry è intenzionale. Permette predicate scoped espliciti e indici left-prefix senza join preliminari, ma soprattutto fa sì che una Facility non possa riferire un'Organization appartenente a un altro Tenant. Lo stesso controllo si propaga fino a Endpoint.

Ogni tabella espone `lifecycle_state`, `row_version`, `created_at`, `updated_at` e `decommissioned_at`. Lo stato appartiene al vocabolario chiuso `ACTIVE`, `SUSPENDED`, `DECOMMISSIONED`; quest'ultimo richiede un timestamp di dismissione, mentre gli altri lo vietano. `row_version` è consumato dall'optimistic concurrency P1-0106: il write path Endpoint lo incrementa atomicamente con il predicate `row_version`, senza trigger implicito.

Display name vuoti o oltre 256 caratteri e sequenze temporali incoerenti sono rifiutati. I display name non sono chiavi e possono coincidere tra aziende o facility.

### Runtime Cell e assegnazioni

`runtime_cell` non contiene un parent gerarchico. `runtime_cell_scope_assignment` associa la cella a uno dei cinque livelli, conserva tutte le colonne ancestor necessarie e applica una shape chiusa:

| `scope_level` | Colonne obbligatorie oltre `tenant_id` | Colonne che devono essere `NULL` |
|---|---|---|
| `TENANT` | nessuna | organization, facility, application, endpoint |
| `ORGANIZATION` | organization | facility, application, endpoint |
| `FACILITY` | organization, facility | application, endpoint |
| `APPLICATION` | organization, facility, application | endpoint |
| `ENDPOINT` | organization, facility, application, endpoint | nessuna |

Le foreign key composite verificano che lo scope esista davvero nell'ancestry indicata. L'indice univoco parziale con `NULLS NOT DISTINCT` impedisce due assegnazioni attive identiche anche ai livelli che contengono colonne nulle. Una revoca valorizza `revoked_at`; una nuova assegnazione produce una nuova sequenza e conserva la storia.

Due constraint trigger differibili consentono di creare in un'unica transazione una Runtime Cell e il primo scope, ma negano il commit se una cella non dismessa rimane senza assegnazioni attive. La differibilità evita stati intermedi falsamente invalidi senza permettere uno stato invalido dopo il commit.

## Constraint e semantica di modifica

### Integrità referenziale

Le foreign key sono aggiunte in V001 con `NOT VALID`. PostgreSQL controlla comunque le righe nuove e modificate; evita invece la scansione immediata delle righe già presenti. V002 esegue `VALIDATE CONSTRAINT` in una finestra osservata, separando l'introduzione della regola dalla scansione del pregresso.

Dopo una migration riuscita non deve esistere in `pg_constraint` alcun constraint non validato nello schema `platform_core`. Il test di integrazione verifica direttamente questa condizione.

### Immutabilità e cancellazione

Trigger `BEFORE` rifiutano:

- modifica di primary key, tipo o ancestry;
- hard delete di identità, risorse inventory e assegnazioni;
- modifica in-place dell'identità di un'assegnazione.

La cancellazione operativa usa la transizione a `DECOMMISSIONED`; la revoca di uno scope usa `revoked_at`. P1-0107 ha successivamente aggiunto il journal tamper-evident alle operazioni Endpoint. I trigger di P1-0102 garantiscono integrità, non costituiscono da soli un audit trail conforme.

## Piano degli indici

| Indice | Predicate/chiavi | Query supportata |
|---|---|---|
| `ix_resource_identity_type_allocated` | tipo, data allocazione, ID | inventory e riconciliazione per tipo/finestra |
| `ix_tenant_active_updated` | aggiornamento, Tenant; solo non dismessi | scansione incrementale Tenant |
| `ix_organization_scope_active` | Tenant, Organization; covering | lookup Organization scoped |
| `ix_facility_scope_active` | Tenant, Organization, Facility; covering | lookup e pagina Facility per azienda |
| `ix_application_scope_active` | ancestry fino ad Application; covering | lookup Application per Facility |
| `ix_endpoint_scope_active` | ancestry completa; covering | lookup Endpoint per Application/Facility |
| `ix_runtime_cell_active_updated` | aggiornamento, Runtime Cell; covering | riconciliazione delle celle attive |
| `ix_runtime_cell_scope_by_cell_history` | cella, creazione, sequenza | storia completa delle assegnazioni |
| `ix_runtime_cell_scope_lookup_active` | ancestry, cella; solo attivi | risoluzione delle celle autorizzate per scope |
| `uq_runtime_cell_scope_assignment_active` | cella, livello e ancestry; `NULLS NOT DISTINCT` | unicità dell'assegnazione attiva |
| `ix_secret_reference_scope_state` | Tenant, Facility, stato, reference; covering | lookup scoped delle reference utilizzabili P1-0105 |
| `uq_endpoint_secret_binding_active` | ancestry Endpoint, purpose, reference; solo attivi | unicità del binding corrente con overlap fra reference differenti |
| `ix_endpoint_secret_binding_reference_active` | Tenant, Facility, reference, Endpoint; solo attivi | risoluzione/revoca dei binding correnti |

Gli indici scoped seguono la gerarchia dal Tenant verso il livello più specifico. I campi `display_name` e `row_version` sono inclusi negli indici di lettura per consentire piani covering quando la visibilità della pagina lo permette. Le clausole parziali escludono risorse dismesse dagli access path correnti senza rimuoverle dallo storico.

Gli indici applicativi sono creati `CONCURRENTLY`: PostgreSQL non consente questa forma dentro una transaction block. Per questo V003 usa `executeInTransaction=false`. Flyway deve inoltre essere configurato con `flyway.postgresql.transactional.lock=false`, così il lock di migration rimane session-level durante lo script non transazionale. Un solo migration principal esegue la pipeline; un indice `INVALID` dopo crash fa fallire la verifica e deve essere rimosso e ricreato da una nuova migration forward, non corretto manualmente senza change record.

## Protocollo expand/migrate/contract

### Expand

1. creare solo oggetti additivi e nomi nuovi;
2. aggiungere constraint sul pregresso con `NOT VALID` quando la scansione immediata può estendere il lock;
3. distribuire codice N e N-1 capace di convivere con gli oggetti aggiunti;
4. creare indici voluminosi in modo concorrente e monitorare progress, replica lag, WAL e durata;
5. non cambiare il significato di una colonna usata in-place.

V001 e V003 realizzavano questa fase per lo schema inventory greenfield. V004 aggiunge su tabelle nuove le secret reference di P1-0105. La creazione delle tabelle resta transazionale; la costruzione concorrente degli indici inventory è deliberatamente separata.

### Migrate e validate

1. eseguire eventuale backfill in chunk idempotenti e riprendibili;
2. misurare righe non conformi senza includere valori sensibili nell'evidenza;
3. validare i constraint introdotti in expand;
4. confrontare conteggi, checksum logiche e invarianti prima/dopo;
5. mantenere compatibilità di lettura e scrittura durante rolling update e failover.

V002 è la fase di validazione della baseline P1-0102. Non serviva backfill perché lo schema era nuovo; il test N-1 inserisce comunque una risorsa dopo V001 e dimostra che V002/V003/V004 la preservano.

### Contract

Non è presente una migration distruttiva: in una baseline greenfield non esistono colonne o oggetti legacy da rimuovere. Un file `DROP` vuoto o artificiale fornirebbe un segnale operativo ingannevole.

Una futura contract migration è ammessa soltanto quando:

- almeno una release completa ha scritto nel nuovo formato;
- telemetria e dependency inventory dimostrano che nessun workload N-1 usa l'oggetto;
- backup e restore point sono verificati e il replica lag è entro soglia;
- il rollback applicativo resta possibile senza ripristino dello schema rimosso;
- il change è approvato, schedulato e dotato di roll-forward;
- `DROP`, riscritture di tipo e `NOT NULL` potenzialmente bloccanti sono in una migration nuova, mai in una versione già applicata.

Non si usano Flyway `clean`, undo automatici o rollback SQL distruttivi in produzione. Se una migration non transazionale fallisce, il runbook identifica lo stato dal catalogo, conserva l'evidenza e riprende tramite una correzione forward controllata.

## Esecuzione enterprise, HA e continuità

Il profilo developer può usare un singolo PostgreSQL. Qualification e produzione richiedono il profilo definito da ADR-025: writer fenced, replica monitorata, backup point-in-time e restore provato. La migration viene eseguita una volta contro il writer dal principal dedicato; nessuna replica o istanza applicativa avvia migration concorrenti.

Prima di una contract migration o di una modifica con riscrittura potenziale sono obbligatori:

1. preflight di versione, capacità, spazio temporaneo, replica lag e sessioni lunghe;
2. backup coerente e restore point nominato;
3. soglie di abort su lock wait, WAL, lag, error rate e saturazione I/O;
4. verifica che il failover non possa produrre due writer;
5. prova di restore nell'ambiente isolato e riconciliazione delle checksum Flyway;
6. change/audit record con operatore, artifact digest, orari ed esito.

La presenza di `IF NOT EXISTS` sugli indici limita gli errori di retry, ma non sostituisce la history Flyway né autorizza drift. `validateOnMigrate=true`, naming validation e `outOfOrder=false` sono parte del profilo. `cleanDisabled=true` è obbligatorio fuori dai database effimeri di test.

## Verifica automatizzata

`PlatformCoreMigrationTest` usa Testcontainers e l'immagine esatta `postgres:18.6-bookworm` fissata per digest nella baseline di governance. Non usa H2 o un emulatore SQL.

| Scenario | Oracle |
|---|---|
| fresh database | applicate esattamente V001–V004; Flyway validate verde |
| catalogo | tutte le tabelle attese presenti; nessun constraint non validato; nessun indice invalido; tutti gli indici applicativi attesi presenti |
| upgrade N-1 | database fermo a V001 con Tenant sintetico; V002/V003/V004 completano senza perdita della riga |
| ancestry valida | gerarchia sintetica completa fino a Endpoint accettata |
| cross-tenant | Facility collegata a Organization di altro Tenant rifiutata con FK violation |
| type confusion | UUID Tenant usato come Organization rifiutato |
| immutabilità | modifica ID e hard delete rifiutati |
| Runtime Cell | cella più scope nella stessa transazione accettati; cella priva di scope negata al commit |
| scope shape/duplicate | shape incoerente, duplicato attivo e revoca dell'ultimo scope rifiutati |
| secret reference P1-0105 | catalogo a colonne allowlisted, enum/UUID/ancestry validati, binding immutabile, hard delete rifiutato |

Il test è incluso nel normale `mvn clean verify`, quindi il required check di build intercetta drift SQL, incompatibilità con PostgreSQL e regressioni dei constraint. P1-0108 ha successivamente consegnato la matrice negativa, P1-0109 la qualification Unicode/locale/timezone e P1-0110 il seed dimensionale con piani query; la qualification completa G2 resta aperta per CI, evidence e review residue.

## Configurazione operativa minima

La configurazione Flyway equivalente al profilo testato deve includere:

```properties
flyway.locations=classpath:db/migration
flyway.schemas=platform_core
flyway.defaultSchema=platform_core
flyway.createSchemas=true
flyway.validateMigrationNaming=true
flyway.validateOnMigrate=true
flyway.outOfOrder=false
flyway.cleanDisabled=true
flyway.postgresql.transactional.lock=false
```

Credential e URL non sono inseriti nelle migration, nei log o nel repository. Il migration principal può creare/alterare gli oggetti di `platform_core`; il futuro runtime principal riceverà soltanto i privilegi DML richiesti e non il diritto di alterare schema o disabilitare trigger.

## Compatibilità e dipendenze

Le versioni dirette sono governate nel parent Maven e in `governance/dependencies.yml`:

- Flyway Core e modulo PostgreSQL 13.5.0;
- PostgreSQL JDBC 42.7.13 in runtime;
- Testcontainers PostgreSQL/JUnit Jupiter 2.0.5 soltanto nei test;
- Jackson Annotations 2.22 come convergenza richiesta dal grafo Flyway, senza introdurre un mapper alternativo.

La compatibilità dichiarata di questo incremento è PostgreSQL 18.x. Distribuzioni compatibili o servizi gestiti devono superare la stessa suite fresh/N-1, catalogo, failover, restore e performance prima di essere dichiarati supportati.

## Rischi residui e attività successive

| Rischio | Controllo presente | Chiusura prevista |
|---|---|---|
| query applicativa dimentica lo scope | repository scoped consegnati da P1-0103/P1-0105 | matrice e gate consegnati da P1-0108 |
| abuso di un ruolo DB privilegiato | constraint e trigger | separation of duties/grant nella deployment baseline |
| modifica lifecycle Endpoint | timestamp, stato ed evento tamper-evident | consegnata da P1-0107; altre risorse in WP1-10 |
| accesso cross-scope autorizzato male | ancestry fisica coerente | OIDC/RBAC core P1-0104; matrice estesa P1-0108 consegnata |
| indice corretto ma piano inefficiente su scala | struttura left-prefix | `EXPLAIN (ANALYZE, BUFFERS)` consegnato sul corpus P1-0110; capacity production in WP1-12/G8 |
| errore durante migration non transazionale | catalog validation e forward-only | runbook operativo e qualification failure/resume M5 |
| perdita del writer o restore incompleto | schema ricreabile e checksum Flyway | HA/backup/PITR/restore qualification WP1-11 |

## Fonti ufficiali

Fonti verificate il 14 settembre 2026:

- [PostgreSQL 18 — Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html), per foreign key, unique e check constraint;
- [PostgreSQL 18 — ALTER TABLE](https://www.postgresql.org/docs/18/sql-altertable.html), per `NOT VALID` e `VALIDATE CONSTRAINT`;
- [PostgreSQL 18 — CREATE INDEX](https://www.postgresql.org/docs/18/sql-createindex.html), per creazione concorrente, indici parziali, colonne incluse e `NULLS NOT DISTINCT`;
- [PostgreSQL 18 — Trigger](https://www.postgresql.org/docs/18/sql-createtrigger.html), per constraint trigger differibili;
- [Flyway — PostgreSQL database reference](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database), per modulo database e transactional lock;
- [Flyway — Script configuration](https://documentation.red-gate.com/flyway/reference/script-configuration), per `executeInTransaction=false`;
- [Flyway 13.5.0 release](https://github.com/flyway/flyway/releases/tag/flyway-13.5.0), per la baseline del migration engine;
- [pgJDBC 42.7.13 release](https://github.com/pgjdbc/pgjdbc/releases/tag/REL42.7.13), per la versione del driver;
- [Testcontainers PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/), per l'esecuzione dei test su PostgreSQL reale;
- [Testcontainers Java 2.0.5 release](https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.5), per la baseline del test harness.
