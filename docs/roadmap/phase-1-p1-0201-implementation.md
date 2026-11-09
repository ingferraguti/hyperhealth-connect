# P1-0201 — HHC Integration Envelope v1 identity core

**Stato:** IMPLEMENTED — human review pending

**Baseline verificata:** 9 novembre 2026

**Ambito:** WP1-02, Integration Envelope; requisito FR-EVT-001

**Classe di rischio:** A3 — tenancy, attribuzione e lineage di eventi sanitari

**Manifest di test:** [`phase-1-envelope-v1-manifest.yml`](../testing/phase-1-envelope-v1-manifest.yml)

## 1. Esito

P1-0201 sostituisce lo stub fondativo dell'Integration Envelope con un contratto v1 immutabile, tipizzato e fail-closed. Ogni istanza accettata contiene:

- versione major esatta `hhc-envelope/v1`;
- `messageId` tecnico globale in forma UUID URN canonica;
- `correlationId` tecnico bounded;
- `causationId` opzionale e distinto dal messaggio corrente;
- scope immutabile Tenant/Facility;
- Flow logico e relativa versione immutabile esatta;
- `receivedAt` come `Instant` al trust boundary HHC.

Il contratto eseguibile è implementato da `IntegrationEnvelope`, pubblicato come JSON Schema Draft 2020-12 e accompagnato da fixture sintetica, test positivi/negativi e gate CI. Non include byte raw, riferimento all'object store, checksum, idempotency key, ledger o politica ACK: questi appartengono alle attività P1-0202 e successive e non sono dichiarati implicitamente completati.

## 2. Artifact consegnati

| Artifact | Finalità |
|---|---|
| `IntegrationEnvelope.java` | modello dominio immutabile e validazione fail-closed |
| `hhc-envelope-v1.schema.json` | contratto machine-readable dell'identity core |
| `hhc-envelope-v1-synthetic.json` | esempio privo di PHI e visibilmente sintetico |
| `IntegrationEnvelopeTest` | oracle positivi e negativi del dominio |
| `phase-1-envelope-v1-manifest.yml` | invarianti, automazione e confine di qualification |
| `phase1-p1-0201-gate.ps1` | digest schema, shape, minimizzazione, test ed evidence |
| `P1-0201.yml` | Agentic Change Manifest A3 con review umana pendente |

Lo schema ha digest SHA-256 versionato nel manifest. Ogni modifica del contratto richiede aggiornamento intenzionale del digest, test di compatibilità e motivazione; il gate rifiuta drift silenzioso.

## 3. Modello eseguibile

La forma logica consegnata è:

```yaml
envelopeVersion: hhc-envelope/v1
messageId: urn:uuid:01991d95-0000-7000-8000-000000000001
correlationId: cor-HHC-SYNTHETIC-episode-42
causationId: urn:uuid:01991d94-0000-7000-8000-000000000001 # opzionale
scope:
  tenantId: t-10000000-0000-4000-8000-000000000001
  facilityId: f-30000000-0000-4000-8000-000000000001
processing:
  flowId: flow-synthetic-lab-results
  flowVersion: 1.0.0
time:
  receivedAt: 2026-11-09T10:15:30.123456Z
```

`Scope`, `Processing`, `Timing`, `MessageId` e `CorrelationId` sono record Java immutabili. Il costruttore canonico valida sempre le invarianti; le factory `root` e `causedBy` rendono esplicita la differenza fra un ingresso senza causa HHC precedente e un evento derivato/corretto.

## 4. Identità del messaggio

### 4.1 Forma e generazione

Il wire value è `urn:uuid:<uuid-canonico-minuscolo>`. P1-0201 accetta UUIDv4 e UUIDv7 nella variante IETF definita da RFC 9562:

- UUIDv4 è adatto a generazione pseudocasuale distribuita;
- UUIDv7 consente locality temporale senza usare un contatore globale;
- il Nil UUID è riservato e rifiutato;
- UUIDv1 è rifiutato per non introdurre identificatori di nodo/MAC e una semantica storica non necessaria;
- maiuscole, UUID abbreviati o forma priva di `urn:uuid:` sono rifiutati invece di essere normalizzati silenziosamente.

Il modello non genera autonomamente ID: gateway e test devono fornire il generatore approvato. In questo modo entropy source e clock restano controllabili e qualificabili separatamente.

### 4.2 Unicità e autorizzazione

Il formato riduce collisioni accidentali ma non costituisce una capability di sicurezza. Possedere o indovinare un `messageId` non autorizza lettura del raw, query del ledger o replay. Ogni operazione futura continuerà a richiedere Tenant, Facility, purpose e policy autorizzativa.

Il controllo definitivo delle collisioni richiede persistenza/unique constraint nel ledger P1-0204; P1-0201 valida forma e semantica, non dichiara unicità globale osservata senza repository.

## 5. Correlation e causation

I tre identificatori hanno funzioni distinte:

| Campo | Significato | Regola P1-0201 |
|---|---|---|
| `messageId` | singola ricezione/evento HHC | obbligatorio, immutabile, globalmente utilizzabile |
| `correlationId` | raggruppamento tecnico di una conversazione o operazione | obbligatorio, 1–128 caratteri ASCII sicuri |
| `causationId` | messaggio HHC immediatamente causale | opzionale, stesso tipo di `messageId`, mai self-reference |

`correlationId` accetta UUID URN o token opachi autorizzati, ma rifiuta whitespace, newline, control character e valori oltre 128 caratteri. Questo riduce log forging e cardinalità senza trasformarlo in etichetta metrica ad alta cardinalità. Non deve contenere MRN, accession number, codice fiscale, patient ID o altri business identifier.

P1-0201 impedisce il ciclo diretto `messageId == causationId`. Cicli indiretti e riferimenti a messaggi di un altro scope richiedono lookup nel ledger e saranno verificati quando la persistenza esisterà; non vengono simulati in memoria.

## 6. Scope Tenant/Facility

Lo scope usa le rappresentazioni esterne già definite da WP1-01:

- Tenant: `t-` seguito da UUID canonico non-nil;
- Facility: `f-` seguito da UUID canonico non-nil.

I prefissi rendono rilevabile uno scambio accidentale Tenant/Facility. Il modello rifiuta Nil UUID, caratteri maiuscoli, whitespace e prefisso errato. Non effettua trim o case folding dipendente dal locale. Non restringe artificialmente la versione UUID degli ID inventory: rispetta il contratto WP1-01, che genera attualmente UUIDv4 ma accetta qualsiasi UUID canonico non-nil per compatibilità e migrazione.

La presenza dello scope non sostituisce l'autorizzazione: il gateway deve derivarlo da endpoint registry e workload identity attendibili. Un valore proveniente dal payload clinico o da un header non autenticato non è autorevole. L'ancestry Facility→Tenant rimane responsabilità del resolver scoped e dei constraint WP1-01.

## 7. Flow e riproducibilità

`flowId` è un token canonico minuscolo con prefisso `flow-`, massimo 69 caratteri. `flowVersion` è obbligatoria, bounded a 128 caratteri e preservata esattamente. Gli alias mobili `latest`, `current`, `head` e `snapshot` sono rifiutati senza distinzione fra maiuscole e minuscole; non esiste un default implicito nel modello.

La coppia consente di attribuire un messaggio alla configurazione logica selezionata; non dimostra ancora che l'artifact di Flow esista, sia firmato o sia autorizzato nello scope. Queste verifiche appartengono a WP1-06/G7. Il modello vieta valori vuoti o non bounded affinché il futuro lookup non debba interpretare input ambiguo.

## 8. Tempo tecnico

`receivedAt` è l'istante in cui HHC accetta l'unità di lavoro al trust boundary. È un `java.time.Instant`, quindi non dipende da timezone JVM, locale o overlap DST.

Non rappresenta:

- timestamp clinico della sorgente;
- tempo effettivo dell'osservazione;
- `durableAt`;
- orario di delivery o receipt.

Il valore clinico originale, precisione, offset e timezone saranno conservati separatamente dal raw/canonical model. P1-0201 non riscrive un timestamp sorgente ambiguo usando `receivedAt`.

## 9. JSON Schema e compatibilità

Lo schema usa JSON Schema Draft 2020-12 e applica:

- `const` per `hhc-envelope/v1`;
- proprietà core e nested chiuse con `additionalProperties=false`;
- required list esatta per identity, correlation, scope, Flow e tempo;
- `causationId` opzionale;
- pattern coerenti con le validazioni Java;
- `date-time` per la rappresentazione wire di `receivedAt`.

P1-0201 non introduce un codec JSON framework-specifico. Il JSON Schema è l'autorità wire; adapter JSON, Avro, Protobuf o CloudEvents devono dimostrare equivalenza con contract test e non modificare il modello di dominio.

Una major sconosciuta fallisce chiusa. L'espansione del v1 con gruppi successivi richiede un nuovo schema artifact, compatibility test N/N-1 e aggiornamento del digest. Non è ammesso riutilizzare lo stesso campo con una semantica diversa.

## 10. Sicurezza e minimizzazione

- nessun payload o frammento clinico è incorporato;
- nessun ID paziente o business è richiesto;
- correlation e Flow sono bounded e privi di whitespace/control character;
- gli ID non sono normalizzati silenziosamente;
- la fixture contiene il marker `HHC-SYNTHETIC`;
- lo schema rifiuta proprietà core non dichiarate;
- il modello non include URL, credential, secret reference o locator dell'object store;
- `messageId` non è un'autorizzazione né deve essere usato come metrica time-series label.

Il metadata può comunque essere personale per correlabilità in un sistema reale. Accesso, retention e audit rimangono governati anche in assenza di payload.

## 11. Scalabilità, resilienza e continuità

Il core è un value object privo di I/O, stato globale, cache o lock. La creazione è O(1), non effettua lookup cross-tenant e non dipende dal Control Plane al runtime. Questo è coerente con Runtime Cell locali e installazioni multi-azienda/multi-facility.

P1-0201 non prova:

- collision handling persistente;
- durability raw+ledger;
- replica, failover o restore;
- RPO/RTO;
- throughput/p95/p99 production;
- ordinamento o idempotenza;
- emissione di ACK.

La sola ricostruzione dello stesso value object dopo restart non è una prova di business continuity o disaster recovery. Tali claim richiedono P1-0202…P1-0210, WP1-11 e WP1-12.

## 12. Casi d'uso sanitari europei 2026

### ENV-UC-01 — Ricezione ORU root da LIS

Un connettore MLLP autenticato riceve un messaggio sintetico da un LIS. Il gateway risolve Tenant, Facility e Flow dal registry, genera `messageId`, usa un correlation ID tecnico e crea un envelope root senza causation.

**Oracle:** versione v1 esatta, scope tipizzato, Flow pin-nato e `causationId` assente. Nessun campo paziente è copiato.

### ENV-UC-02 — Risultato corretto

Una nuova ricezione rappresenta la correzione di un risultato precedente. Riceve un nuovo `messageId`, conserva la correlation della conversazione e collega il messaggio immediatamente precedente tramite `causationId`.

**Oracle:** nuovo ID distinto; causation presente; self-causation rifiutata. La relazione clinica di supersession resta da validare nel profilo/ledger, non viene inferita dal solo link.

### ENV-UC-03 — Tentativo di scope spoofing

Un payload dichiara una Facility diversa da quella associata al certificato del sender. L'envelope deve usare lo scope risolto dall'identità affidabile; lo scope dichiarato nel payload non sovrascrive Tenant o Facility.

**Oracle:** tipo/prefisso validi e successivo resolver scoped; nessuna correzione automatica cross-facility.

### ENV-UC-04 — Rollout di Flow in due Facility

La Facility A usa `flowVersion=1.0.0`, mentre la Facility B è in canary su `1.1.0`. Gli envelope registrano la versione esatta senza alias `latest`, permettendo confronto, rollback e replay riproducibile.

**Oracle:** Flow ID canonico, versione obbligatoria preservata byte-for-byte.

### ENV-UC-05 — Correlazione fra API e pipeline

Una richiesta REST genera un correlation token propagato verso l'ingest. Il token contiene solo caratteri sicuri e non diventa patient identifier o authorization key.

**Oracle:** valore bounded; newline, whitespace e oversize rifiutati; autorizzazione sempre indipendente.

### ENV-UC-06 — Ripristino futuro

Dopo un restore, ledger e raw dovranno ricostruire envelope con gli stessi ID, scope, Flow e tempi. P1-0201 fornisce il contratto da confrontare, ma non esegue backup/restore.

**Oracle:** il contratto è deterministico; il verdetto DR rimane WP1-11/M5.

## 13. Test ed evidence

La suite contiene 13 test che coprono:

- envelope root e caused;
- separazione message/correlation/causation;
- UUIDv4/v7 e wire URN;
- major version sconosciuta;
- self-causation;
- container opzionale nullo;
- correlation unsafe/oversize;
- prefix/type/scope e Nil UUID;
- compatibilità degli ID inventory canonici non-nil con WP1-01;
- Flow ID/version non canonici;
- receive time obbligatorio.

Esecuzione locale:

```powershell
./mvnw.cmd -B -ntp -pl integration-envelope -am "-Dtest=IntegrationEnvelopeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
./scripts/phase1-p1-0201-gate.ps1
```

Evidence:

- Surefire XML di `IntegrationEnvelopeTest`;
- `target/phase1-p1-0201-evidence/envelope-v1-gate-report.json`;
- digest dello schema nel manifest e nel gate report.

## 14. Criteri di completamento

- [x] `messageId` canonico e immutabile;
- [x] correlation distinta, bounded e obbligatoria;
- [x] causation opzionale e non autoreferenziale;
- [x] Tenant/Facility obbligatori e type-safe per prefisso;
- [x] Flow ID canonico e versione esatta;
- [x] versione envelope v1 fail-closed;
- [x] receive time UTC/Instant;
- [x] schema machine-readable con digest;
- [x] fixture solo sintetica;
- [x] test positivi e negativi;
- [x] gate CI ed evidence JSON;
- [x] confine esplicito rispetto a checksum, raw, ledger e ACK;
- [ ] review umana Runtime Engineering e Product Security, richiesta dalla classe A3.

## 15. Stato WP1-02 e passo successivo

P1-0201 è implementata. WP1-02 passa da `READY` a `IN_PROGRESS`; Gate G3 resta `PENDING`.

Il prossimo incremento è P1-0202: calcolare SHA-256 streaming sui byte originali e conservarne algoritmo/versione senza alterare il payload. P1-0202 dovrà estendere il contratto in modo compatibile e non introdurre ancora un ACK positivo prima del durability point definito da ADR-026.

## 16. Fonti ufficiali

Fonti ricontrollate il **9 novembre 2026**:

1. IETF, [RFC 9562 — Universally Unique IDentifiers](https://www.rfc-editor.org/rfc/rfc9562.html) — formato, varianti, UUIDv4/v7, Nil UUID e considerazioni di sicurezza.
2. JSON Schema, [Draft 2020-12](https://json-schema.org/draft/2020-12) — dialect e vocabolari del contratto machine-readable.
3. Cloud Native Computing Foundation, [CloudEvents specification repository](https://github.com/cloudevents/spec) e release 1.0.2 — riferimento per futuri binding; P1-0201 non dichiara conformance CloudEvents.
4. W3C, [Trace Context Recommendation](https://www.w3.org/TR/trace-context/) — distinzione fra correlation applicativa e trace context; il trace binding non è ancora parte del core P1-0201.

Le fonti definiscono formati e standard. Prefissi HHC, limiti, scelta v4/v7 e separazione degli incrementi sono decisioni progettuali HyperHealth Connect.
