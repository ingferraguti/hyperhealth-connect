# P1-0110 — Seed sintetico e qualification di scala dell'inventory

**Stato:** IMPLEMENTED

**Baseline verificata:** 17 settembre 2026

**Ambito:** WP1-01, Platform DB e API inventory `Endpoint`

**Manifest:** [`phase-1-inventory-scale-manifest.yml`](../testing/phase-1-inventory-scale-manifest.yml)

## 1. Esito

P1-0110 consegna un corpus deterministico, ripetibile e soltanto sintetico per esercitare la gerarchia enterprise multi-azienda/multi-facility su PostgreSQL reale. Il corpus supera il minimo della roadmap e contiene:

| Risorsa | Cardinalità |
|---|---:|
| Tenant | 2 |
| Organization | 2 |
| Facility | 4 |
| Application | 8 |
| Endpoint | 12.000 |
| Runtime Cell | 2 |
| assegnazioni Runtime Cell → Facility | 4 |

La Facility calda contiene 6.000 Endpoint; le altre tre ne contengono 2.000 ciascuna. Il 90% degli Endpoint è `ACTIVE` e il 10% `SUSPENDED`. Tutti i nomi iniziano con `HHC-SYNTHETIC`, tutti gli UUID sono riservati al corpus e nessun valore deriva da pazienti, operatori, strutture o sistemi reali.

La suite prova cardinalità, identity ledger, ancestry, distribuzione lifecycle, keyset pagination, isolamento di scope, audit chain, piani `EXPLAIN (ANALYZE, BUFFERS)` e una soglia di regressione locale. P1-0110 completa le attività di implementazione P1-0101…P1-0110; non dichiara autonomamente superato Gate G2, che richiede ancora CI sul commit candidato, evidence consolidate e review/qualification previste dalla governance.

## 2. Artifact consegnati

| Artifact | Funzione |
|---|---|
| `p1-0110-inventory-scale.sql` | seed atomico e deterministico |
| `InventoryScaleQualificationTest` | oracle PostgreSQL/repository/audit/performance regression |
| `phase-1-inventory-scale-manifest.yml` | contratto machine-readable di corpus, invarianti e limiti |
| `phase1-p1-0110-gate.ps1` | controllo digest, anti-bypass ed evidence runtime |
| job `inventory-scale-gate` | esecuzione CI e retention dei report |

Il seed ha digest SHA-256 versionato nel manifest. Una modifica, anche solo di cardinalità o rappresentazione, richiede aggiornamento esplicito del digest, dei test e della motivazione; il gate impedisce drift silenzioso.

## 3. Disegno del seed

### 3.1 Determinismo e atomicità

Gli UUID sono costanti per le risorse di gerarchia e derivati da `generate_series(1, 12000)` per gli Endpoint. Non vengono usati clock, random, estensioni PostgreSQL o servizi esterni. Il caricamento dati del seed esegue una singola transazione:

1. alloca gli ID nell'identity ledger;
2. crea Tenant e Organization;
3. crea due Facility per Tenant;
4. crea due Application per Facility;
5. crea due Runtime Cell;
6. assegna ogni Runtime Cell alle due Facility del proprio Tenant;
7. alloca e inserisce 12.000 Endpoint;
8. committa soltanto quando constraint e trigger differiti sono soddisfatti.

Dopo il commit, il medesimo script esegue `ANALYZE` sulle tabelle interessate. La preparazione del test completa poi la visibility map con `VACUUM (ANALYZE)` su `endpoint`; queste operazioni di manutenzione non fanno parte della transazione atomica di caricamento.

Il seed non disabilita trigger, foreign key, constraint trigger o `session_replication_role`. Il primo inserimento di una Runtime Cell e la relativa assegnazione convivono nella stessa transazione perché una cella attiva senza scope è vietata anche transitoriamente al commit.

### 3.2 Distribuzione e hot scope

La distribuzione 6.000/2.000/2.000/2.000 crea contemporaneamente:

- un Tenant caldo con 8.000 Endpoint;
- una Facility calda con metà dell'intero corpus;
- Facility sibling popolate nello stesso Tenant;
- Facility popolate in un altro Tenant;
- due Application per scope, necessarie a verificare filtri e indice composto;
- stati `ACTIVE` e `SUSPENDED` senza introdurre risorse terminali nella current view.

Non è una riproduzione statistica di un cliente. È un corpus controllato per regressioni, isolamento e piani query. Il dimensionamento customer-specific deve usare distribuzioni, retention e concorrenza del carico reale senza importare PHI in laboratorio.

## 4. Oracle di integrità

La suite richiede:

- esattamente 12.000 record Endpoint e 12.000 corrispondenti `resource_identity`;
- join completo di ogni Endpoint verso Facility e Application nello stesso Tenant/scope;
- zero display name senza prefisso sintetico;
- 10.800 Endpoint `ACTIVE` e 1.200 `SUSPENDED`;
- quattro assegnazioni attive di Runtime Cell;
- nessun constraint disabilitato durante il caricamento.

Questi controlli intercettano seed parziali, collisioni, ancestry cross-tenant, ID non allocati e dati non marcati. Non si limita la verifica al numero totale: un totale corretto con distribuzione o scope errati fallisce.

## 5. Keyset pagination e audit

Il repository attraversa tutti i 6.000 Endpoint della Facility calda con pagine massime da 100. L'oracle richiede:

1. 60 pagine;
2. nessuna pagina oltre il limite;
3. esattamente 6.000 ID distinti;
4. Tenant e Facility uguali allo scope verificato per ogni riga;
5. nessuna duplicazione o omissione tra cursor successivi;
6. 60 eventi `ENDPOINT_LIST` nella sola catena della Facility chiamante;
7. esito positivo del verifier HMAC della catena.

Il test esercita la repository reale, non una query di test parallela. La pagination non calcola total count e non usa offset: il costo non cresce in funzione del numero di pagine già lette e una collection mutabile non sposta artificialmente le righe precedenti.

## 6. Piani query

Dopo il caricamento viene eseguito `VACUUM (ANALYZE)` della tabella Endpoint per aggiornare statistiche e visibility map. La qualification usa `EXPLAIN (ANALYZE, BUFFERS, SETTINGS)` sulle due forme indicizzate:

- collection per Tenant/Facility → `ix_endpoint_facility_page_active`;
- collection per Tenant/Facility/Application → `ix_endpoint_facility_application_page_active`.

Il piano deve usare il relativo indice e non deve contenere `Seq Scan on endpoint`. L'oracle usa una Facility fredda: in questo modo il covering index evita di attraversare le righe degli scope precedenti.

Sulla Facility calda il planner può legittimamente scegliere `endpoint_pkey` per alcune pagine, perché metà del corpus soddisfa lo scope e la PK più piccola è già ordinata per Endpoint. Questa scelta non è forzata o trattata come errore: PostgreSQL deve poter scegliere il piano meno costoso in base a distribuzione, cursor e statistiche. Si impediscono invece regressioni strutturali che rendano inutilizzabili gli indici scoped nei casi selettivi.

## 7. Misura di latenza e limiti del claim

La suite riscalda la query per 20 iterazioni e raccoglie 100 osservazioni complete di materializzazione di 101 righe. Il p95 deve essere ≤500 ms e ogni statement conserva il timeout di 5 secondi. Il report registra p95 e massimo osservati.

Questa è una soglia di regressione funzionale nel profilo `developer-testcontainers-non-qualifying`, non un benchmark di capacità. Non misura:

- rete client/server reale;
- pool concorrente e saturazione writer;
- replica, WAL, backup o lag;
- cifratura, proxy e service mesh production;
- p99 sotto carico aperto;
- noisy-neighbour distribuito;
- HA, failover, BC/DR o customer sizing.

Il campo `productionCapacityClaim=false` è presente in manifest, test report e gate report. Una pipeline o un documento non può riutilizzare questo valore come SLA. I claim production richiedono l'ambiente fissato in `phase-1-reference-environment.yml`, generatori separati, almeno tre ripetizioni, raw series, saturation curve e independent oracle secondo WP1-12.

## 8. Scalabilità enterprise

P1-0110 dimostra che il modello non cambia passando da fixture puntuali a migliaia di risorse:

- scope Tenant/Facility resta parte del predicate;
- la current view usa indici partial e covering;
- la pagina resta bounded;
- il cursor resta keyset e scope-bound;
- l'audit è sequenziato per Facility, non globalmente;
- il Tenant caldo non modifica l'identity o la visibilità degli altri scope;
- i nomi non sono usati come chiavi di routing;
- la generazione non aumenta memoria applicativa proporzionalmente all'intero corpus.

Il target enterprise finale di 100 Organization, 1.000 Facility e 10.000 Endpoint per Tenant resta un requisito di capacity qualification, non è ridotto da questo seed. Il corpus P1-0110 verifica almeno 12.000 Endpoint complessivi con due Tenant; non prova ancora contemporaneamente tutte le cardinalità massime per singolo Tenant.

## 9. Alta disponibilità, BC/DR e recovery

Il seed è deterministico e ripetibile partendo da un database vuoto, ma non è idempotente su un database già popolato e non usa `ON CONFLICT` per nascondere collisioni. In restore/failover qualification può essere usato come dataset noto per:

1. creare una baseline con digest dichiarato;
2. eseguire backup logico/fisico;
3. ripristinare in un ambiente isolato;
4. ricontare gerarchia e identity ledger;
5. rieseguire pagination e piani;
6. verificare catene audit e checksum evidence;
7. confrontare RPO/RTO misurati.

P1-0110 non esegue tale drill e non dichiara RPO/RTO. Backup/restore, promozione replica, fencing e failback restano WP1-11/WP1-12. La separazione evita che un semplice reload Testcontainers venga presentato come prova di disaster recovery.

## 10. Sicurezza, privacy e auditabilità

- dataset e credenziali container sono sintetici;
- nessun patient ID, accession number, payload HL7/FHIR/DICOM o segreto production è presente;
- il prefisso sintetico rende evidente l'uso improprio in screenshot/export;
- il seed non contiene locator segreti o binding provider;
- il gate verifica il digest e l'assenza dei meccanismi noti di bypass dei constraint;
- il test usa il medesimo audit journal HMAC del vertical slice;
- report e Surefire XML sono pubblicati come evidence CI con retention limitata.

La scala non autorizza logging ad alta cardinalità di Endpoint/Tenant raw. Il report contiene solo conteggi, indici, tempi e digest del seed.

## 11. Casi d'uso sanitari europei 2026

### SCALE-UC-01 — Gruppo ospedaliero con Facility calda

Un Tenant gestisce due presidi; il maggiore ha 6.000 Endpoint tecnici tra LIS, EHR, gateway e istanze logiche, il secondo 2.000. Un secondo gruppo sanitario usa altre due Facility. La lista del presidio caldo attraversa 60 pagine senza duplicati, omissioni o righe di altri presidi.

**Oracle:** 6.000 ID unici, scope invariato, pagina ≤100 e chain audit valida.

### SCALE-UC-02 — Filtro applicativo in una Facility fredda

Un operatore filtra gli Endpoint di una specifica Application nel quarto presidio. PostgreSQL usa l'indice composto Tenant/Facility/Application/Endpoint, evitando la scansione degli altri Tenant e delle Facility sibling.

**Oracle:** indice `ix_endpoint_facility_application_page_active`, nessuna sequential scan Endpoint.

### SCALE-UC-03 — Onboarding atomico della Runtime Cell

Una Runtime Cell viene registrata insieme alle Facility autorizzate. Se una qualsiasi assegnazione viola l'ancestry o manca, l'intera transazione di seed fallisce e non rimane una cella attiva senza scope.

**Oracle:** constraint differito verificato al commit; nessun bypass di trigger/constraint.

### SCALE-UC-04 — Upgrade PostgreSQL/JDBC

Dopo un upgrade del database o del driver, CI ricrea il corpus, aggiorna le statistiche, verifica i piani e confronta la regressione locale. Un cambio legittimo del planner può essere accettato solo conservando scope, bounded page e soglia; non si congela ciecamente il testo completo di `EXPLAIN`.

**Oracle:** indici richiesti sui casi selettivi, nessun seq scan, p95 developer ≤500 ms.

### SCALE-UC-05 — Preparazione di un restore drill

SRE usa il seed e il suo digest come baseline nota prima di un futuro backup/restore. Il ripristino deve conservare 12.000 identity, quattro scope e le catene audit generate dalla scansione, ma il successo sarà attestato soltanto dal runbook WP1-11.

**Oracle:** P1-0110 fornisce il corpus; non anticipa il verdetto BC/DR.

## 12. Evidence e CI

Esecuzione locale:

```powershell
./mvnw.cmd -B -ntp -pl platform/control-plane -am "-Dtest=InventoryScaleQualificationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
./scripts/phase1-p1-0110-gate.ps1
```

Evidence generate:

- `platform/control-plane/target/phase1-p1-0110-evidence/inventory-scale-test-report.json`;
- `target/phase1-p1-0110-evidence/inventory-scale-gate-report.json`;
- Surefire XML di `InventoryScaleQualificationTest`.

Il job CI dedicato ricrea sempre il database da zero, applica V001–V007, carica il seed, esegue i tre oracle, valida il gate e pubblica i report. Nessuna evidence runtime generata localmente viene committata.

## 13. Criteri di completamento

- [x] almeno due Tenant e quattro Facility;
- [x] gerarchia completa e due Runtime Cell;
- [x] almeno 10.000 Endpoint, con 12.000 consegnati;
- [x] hot Facility esplicita;
- [x] seed deterministico, atomico e con digest;
- [x] soli dati sintetici marcati;
- [x] constraint e trigger sempre attivi;
- [x] keyset pagination completa e bounded;
- [x] audit scoped e chain verificata;
- [x] `EXPLAIN ANALYZE BUFFERS` sui due indici collection;
- [x] 100 campioni warm e threshold p95 developer;
- [x] evidence JSON, gate CI e documentazione;
- [x] assenza esplicita di claim production/HA/DR.

## 14. Stato WP1-01 dopo P1-0110

Tutte le attività P1-0101…P1-0110 sono implementate. WP1-01 passa da `IN_PROGRESS` a `VERIFYING`: il codice e le evidence locali sono completi, mentre Gate G2 resta `PENDING` fino a:

- esito CI del commit candidato;
- consolidamento dell'evidence bundle G2;
- review security/IAM indipendente prevista dalla RACI;
- qualification IdP/JWKS/revoca sul profilo dichiarato;
- verifica dei grant runtime/migration e del backup logico secondo il gate;
- chiusura o accettazione formale dei finding residui.

Questo stato è coerente con il modello a singolo maintainer: non inventa una seconda approvazione e non abbassa il gate.

## 15. Fonti ufficiali

Fonti ricontrollate il **17 settembre 2026**:

1. PostgreSQL Global Development Group, [PostgreSQL 18 — EXPLAIN](https://www.postgresql.org/docs/18/sql-explain.html) — semantica di `ANALYZE`, `BUFFERS`, `SETTINGS`, costi e side effect.
2. PostgreSQL Global Development Group, [PostgreSQL 18 — Using EXPLAIN](https://www.postgresql.org/docs/18/using-explain.html) — interpretazione di scan, statistiche reali e preparazione con `VACUUM ANALYZE`.
3. PostgreSQL Global Development Group, [PostgreSQL 18 — Multicolumn Indexes](https://www.postgresql.org/docs/18/indexes-multicolumn.html) — importanza delle colonne leftmost negli indici B-tree.
4. PostgreSQL Global Development Group, [PostgreSQL 18 — Index-Only Scans and Covering Indexes](https://www.postgresql.org/docs/18/indexes-index-only-scans.html) — colonne `INCLUDE`, visibility map e condizioni per index-only scan.
5. PostgreSQL Global Development Group, [PostgreSQL 18 — Performance Tips](https://www.postgresql.org/docs/18/performance-tips.html) — popolamento, statistiche e analisi dei piani.

Le fonti spiegano planner e strumenti. Cardinalità, distribuzione, threshold developer e confine dei claim sono decisioni di qualification HyperHealth Connect, versionate nel manifest.
