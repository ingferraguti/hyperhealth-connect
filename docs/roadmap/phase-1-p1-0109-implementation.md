# P1-0109 — Unicode, timezone, locale e conservazione degli identificativi

**Stato:** IMPLEMENTED

**Baseline verificata:** 17 settembre 2026

**Ambito:** Control Plane, vertical slice inventory `Endpoint` facility-scoped

**Evidence machine-readable:** [`phase-1-internationalization-matrix.yml`](../testing/phase-1-internationalization-matrix.yml)

## 1. Esito e finalità

P1-0109 qualifica l'internazionalizzazione del vertical slice consegnato da P1-0101…P1-0108 senza modificare il suo boundary di autorizzazione. La soluzione ora dimostra che:

1. i nomi visualizzati accettano Unicode multilingue ed emoji validi;
2. la lunghezza è misurata in code point e non in unità UTF-16;
3. sequenze UTF-16 malformate e caratteri di controllo sono respinti prima della persistence;
4. la sequenza Unicode accettata attraversa service, PostgreSQL, JDBC e JSON senza normalizzazione implicita;
5. gli ID tecnici sono canonici, immutabili, ASCII e indipendenti dalla locale;
6. ogni istante di dominio è `timestamptz` e mantiene lo stesso `Instant` al variare della timezone di sessione;
7. l'overlap DST europeo non altera lifecycle, scadenza idempotency o audit chain;
8. il comportamento non cambia con locale JVM italiana, inglese, francese o turca né con `Accept-Language`.

La modifica di produzione è deliberatamente piccola: il service verifica esplicitamente che `displayName` sia Unicode ben formato. Il resto del contratto era strutturalmente corretto ed è ora protetto da test e gate anti-regressione. P1-0110 ha successivamente consegnato il seed di scala; Gate G2 resta pending per CI/evidence e qualification indipendente.

## 2. Perimetro e affermazioni ammesse

### 2.1 Incluso

- `Endpoint.displayName`, unico testo libero del vertical slice pubblico attuale;
- ID `Tenant`, `Organization`, `Facility`, `Application`, `Endpoint` e `RuntimeCell`;
- enum di lifecycle ricevuto dalla API;
- encoding JSON e PostgreSQL;
- timestamp inventory, secret reference, idempotency e journal audit;
- sessioni PostgreSQL in `UTC`, `Europe/Rome`, `Europe/Helsinki` e `America/New_York`;
- overlap dell'ora legale del 25 ottobre 2026;
- JVM default locale e `Accept-Language`.

### 2.2 Escluso, senza falsa certificazione

Questo incremento non gestisce payload clinici, identificativi paziente, accession number, MPI, FHIR Identifier, HL7 CX/EI, codici di terminologia, ricerca linguistica o traduzioni. Non esiste ancora una API amministrativa per gli altri livelli della gerarchia. P1-0109 verifica i loro value object ID, non una persistence/API non implementata.

La conservazione degli “identificativi originali” in questa attività significa conservazione esatta degli ID tecnici canonici del Control Plane. Gli identificativi business o clinici originari dovranno essere modellati in raw envelope/canonical model con `system`, `value`, `assigningAuthority`, provenienza, eventuale tipo e periodo; non devono essere sostituiti, translitterati o deduplicati in base al solo testo. Questa responsabilità appartiene a WP1-02 e ai work package semantic/mapping.

## 3. Contratto Unicode

### 3.1 Rappresentazione e normalizzazione

`displayName` è una sequenza Unicode, trasportata come JSON UTF-8 e memorizzata in PostgreSQL UTF-8. Il service applica nell'ordine:

1. presenza obbligatoria;
2. `String.strip()` sul solo bordo;
3. non-vuoto;
4. verifica di coppie surrogate UTF-16 corrette;
5. rifiuto dei caratteri ISO control;
6. limite massimo di 256 code point.

Non viene applicata normalizzazione NFC/NFD/NFKC/NFKD. Una `é` precomposta e una `e` seguita da combining acute possono essere canonicamente equivalenti, ma restano sequenze differenti. Questa scelta conserva l'input approvato ed evita che una trasformazione non dichiarata cambi firme, digest, audit o round-trip. Il test registra esplicitamente una stringa NFD, ne verifica i byte UTF-8 nel database e dimostra che non viene convertita in NFC.

Il trim al bordo è una trasformazione API già documentata e non equivale alla conservazione byte-per-byte dell'intera request. Spazi interni, incluso lo spazio non separabile, sono preservati. Eventuali future esigenze di conservare anche il bordo originale richiederanno un campo raw separato e immutable, non un cambio silenzioso del contratto.

### 3.2 Unicode malformato e limiti

Java espone UTF-16: una surrogate alta non seguita da una surrogate bassa, o una bassa isolata, non rappresenta un valore scalare Unicode. La validazione le respinge con un errore safe prima di repository e audit. Non si delega il risultato a serializzatore, driver o database, perché i comportamenti di sostituzione potrebbero differire tra implementazioni.

Il limite usa `codePointCount`, quindi un emoji supplementary conta uno e non due. Il gate prova 256 emoji accettati e 257 respinti. Il limite resta un controllo di disponibilità e bounded storage; non è un limite in grapheme cluster e non promette che ogni carattere visuale abbia larghezza uno.

### 3.3 Ricerca e collation

P1-0109 non introduce ricerca o ordinamento per `displayName`. Nessuna collation deterministica o linguistica viene quindi dichiarata qualificata. Le collection continuano a usare chiavi tecniche e keyset pagination. Una futura ricerca deve specificare per campo:

- uguaglianza binaria, canonica o case-insensitive;
- locale e versione della collation;
- normalizzazione esplicita e campo derivato;
- comportamento di upgrade ICU/PostgreSQL;
- indice coerente e piano di reindex;
- protezione contro confusable e spoofing.

Il valore originale deve restare separato da qualsiasi chiave normalizzata di ricerca.

## 4. Identificativi tecnici

Gli ID applicativi hanno forma chiusa `prefix-lowercase-uuid`, con prefissi `t-`, `o-`, `f-`, `a-`, `ep-`, `rc-`. Il parser richiede l'esatta rappresentazione prodotta da `toString()` e non:

- elimina whitespace;
- converte maiuscole/minuscole;
- normalizza Unicode;
- sostituisce caratteri confusable;
- converte cifre fullwidth.

`END_...`, un prefisso con `е` cirillica o una cifra fullwidth falliscono invece di essere “corretti”. Questo previene alias multipli nelle cache, nei log, nelle policy e nelle chiavi di idempotenza. L'immutabilità è mantenuta dai value object e dal ledger/database di P1-0102.

Gli ID non sono dati localizzati. Il parsing è identico sotto `Locale.ROOT`, italiano, inglese, francese e turco. I nomi visualizzati, invece, possono essere multilingue e non vengono usati come chiave o boundary di autorizzazione.

## 5. Locale e negoziazione HTTP

Il lifecycle accetta esclusivamente il token canonico uppercase e verifica la forma con `toUpperCase(Locale.ROOT)`. Non usa la locale predefinita del processo. `Accept-Language` non modifica ID, lifecycle, timestamp, ordine, authorization o representation dell'inventory v1.

La API non offre ancora traduzione dei messaggi o dei display name. Se verrà introdotta, la locale richiesta sarà input di presentazione allowlisted, con fallback esplicito e `Vary: Accept-Language` dove necessario. Non dovrà entrare in:

- decisioni RBAC/ABAC;
- query di scope;
- ETag, idempotency digest o audit HMAC;
- parsing di numeri/date tecnici;
- ordinamento stabile della pagination.

## 6. Timezone e istanti

### 6.1 Regola di dominio

Tutti i timestamp di dominio sono istanti assoluti. Le migration usano `timestamptz`; JDBC legge `OffsetDateTime` e il dominio converte a `Instant`. Il clock applicativo è iniettato e i timestamp inviati a PostgreSQL sono a offset UTC. PostgreSQL converte l'input in un istante e la timezone di sessione riguarda la rappresentazione di output, non il valore memorizzato.

La suite interroga lo stesso dato con quattro timezone di sessione e richiede lo stesso `Instant`. Il caso è fissato a `2026-10-25T01:30:00Z`, durante l'overlap europeo di fine ora legale, così da intercettare conversioni ambigue basate su local date-time.

### 6.2 Superfici qualificate

La prova copre:

- `endpoint.created_at`, `updated_at` e `decommissioned_at`;
- `inventory_api_idempotency.expires_at`;
- `inventory_audit_event.occurred_at` e verifica della chain HMAC;
- introspezione di tutte le colonne temporali dello schema `platform_core`.

Il test esclude soltanto `flyway_schema_history`, tabella tecnica posseduta da Flyway che contiene un proprio timestamp senza timezone e non è dato di dominio. Non è un'eccezione consentita alle future tabelle HyperHealth Connect.

### 6.3 Cosa non viene conservato

Un instant non conserva la zona IANA, l'offset originario o la wall-clock inserita dall'operatore. Per i timestamp tecnici ciò è voluto. Un futuro evento clinico nel quale “09:00 Europe/Rome” è informazione di business dovrà conservare separatamente:

- istante normalizzato quando determinabile;
- local date-time originale;
- offset originale, se presente;
- zone ID IANA, se dichiarata;
- precisione e provenienza;
- regola esplicita per gap/overlap DST.

Non si deve ricostruire la timezone sorgente a partire da un offset o dall'ubicazione della Facility.

## 7. Database, API e audit

PostgreSQL deve operare con `server_encoding=UTF8` e il client con `client_encoding=UTF8`. Il test di migration fallisce in caso contrario. `char_length` e `octet_length` vengono verificati su emoji e NFD per distinguere code point e byte.

JSON segue RFC 8259 e viene emesso come UTF-8. Il round-trip integrato esegue create/service/repository e GET MockMvc con nome NFD, greco, cirillico, arabo, CJK ed emoji, poi confronta la sequenza esatta restituita.

L'audit continua a non copiare il display name: registra metadati allowlisted e ID tecnici. Questo limita PHI/PII, evita problemi di rendering nei sink e mantiene stabile la chain. Il test DST dimostra che `occurred_at` mantiene lo stesso istante e che il verifier accetta ancora la catena.

## 8. Scalabilità, prestazioni e affidabilità

Le verifiche aggiunte al write path sono lineari sulla stringa e bounded a un massimo di 256 code point; non introducono I/O, regex catastrofiche, lookup di rete o stato condiviso. La lettura non normalizza né duplica il campo. La pagination resta ancorata agli ID tecnici, quindi non dipende dalla collation e produce un ordine stabile tra repliche e locale diverse.

Per installazioni multiazienda e multifacility:

- ogni replica usa lo stesso contratto indipendentemente dalla locale del nodo;
- il pool può avere session timezone diverse senza cambiare gli istanti;
- backup, replica e restore conservano i byte UTF-8 e i `timestamptz`;
- un failover non richiede ricostruzione locale di timestamp;
- il gate viene eseguito dopo upgrade JDK, PostgreSQL, driver JDBC o modifica di serializzazione.

P1-0109 non è un benchmark. P1-0110 ha successivamente consegnato seed ≥10.000 Endpoint, hot Facility, piani e p95 developer non qualificante. Capacity test, p99 sotto carico, soak e failover restano WP1-11/WP1-12.

## 9. Business continuity e disaster recovery

In promozione di replica o restore:

1. verificare encoding server/client e checksum delle migration;
2. verificare timezone configurata senza assumere che sia UTC;
3. eseguire il gate P1-0109 e i test PostgreSQL;
4. confrontare campioni Unicode tramite hash/byte UTF-8, non solo rendering UI;
5. verificare expiry idempotency e audit chain attorno a un cambio DST;
6. riabilitare le mutation soltanto dopo esito verde.

La replica logica/fisica, il backup e il restore devono essere byte-safe e non passare attraverso CSV con encoding implicito. Se uno smoke test rileva normalizzazione, caratteri sostitutivi o instant divergenti, il sito resta read-only e viene trattato come incidente di integrità, non come difetto cosmetico.

## 10. Casi d'uso sanitari europei 2026

### UC-01 — Facility e sistemi con nomi multilingue

Un gruppo ospedaliero italiano gestisce una Facility con reparti e sistemi nominati usando italiano, sloveno, greco e arabo. L'Endpoint “Café LIS – Αθήνα – مخبر – 🧪” viene creato con forma NFD e letto da un operatore con browser turco. Il nome torna con gli stessi code point; scope, ID, lifecycle ed ETag non cambiano con la lingua del browser.

**Oracle:** exact Unicode round-trip, `Accept-Language` non semantico, nessun uso del nome come authorization key.

### UC-02 — Identificatore tecnico anti-spoofing

Un input contiene `еnd_...` con lettera cirillica simile alla `e` latina oppure una cifra fullwidth. Il parser non lo normalizza verso un Endpoint esistente e restituisce errore safe. Nessuna query cross-facility, evento audit target o idempotency claim viene creato.

**Oracle:** reject-not-rewrite; l'unica forma valida è l'ASCII canonico.

### UC-03 — Cambio dell'ora legale e retry amministrativo

Durante l'overlap DST del 25 ottobre 2026 un operatore aggiorna un Endpoint e il client ritenta la richiesta. `updated_at`, scadenza della chiave idempotency e `occurred_at` sono lo stesso istante su nodo italiano, nodo finlandese e replica UTC. Il retry non scade anticipatamente e la chain audit resta valida.

**Oracle:** equality come `Instant`, non come stringa localizzata; nessuna doppia mutation.

### UC-04 — Failover tra regioni europee

Il Control Plane passa da una regione con session timezone `Europe/Rome` a una con `UTC`. Le operazioni correnti mantengono timestamp, ordering e scadenze; il gate post-promozione verifica encoding, colonne temporali e round-trip prima di riaprire i write.

**Oracle:** nessuna deriva di istante o byte UTF-8; RTO/RPO restano quelli del piano infrastrutturale e non vengono ridefiniti da P1-0109.

### UC-05 — Migrazione LIS e conservazione del nome originale

Un LIS legacy usa una sequenza decomposed mentre il nuovo pannello mostra visivamente la stessa parola precomposta. HyperHealth Connect conserva la sequenza ricevuta; non deduplica due Endpoint per equivalenza visiva. La riconciliazione usa ID tecnico e provenienza, evitando merge silenziosi.

**Oracle:** NFD resta NFD; nessuna identità è inferita dal display name.

### UC-06 — Timestamp clinico senza timezone

Un messaggio futuro riporta soltanto `2026-10-25 02:30`, ambiguo in Europa/Roma. Il team non lo inserisce direttamente nei timestamp tecnici inventory. Il raw envelope conserva il valore originario; la pipeline clinica successiva applicherà una policy di ambiguità e conserverà zona/offset/provenienza.

**Oracle:** P1-0109 non inventa un instant clinico e non estende falsamente il proprio scope.

## 11. Test, gate ed evidence

| Evidence | Copertura |
|---|---|
| `InventoryInternationalizationTest` | multiscript, NFD, emoji, code-point boundary, surrogate malformate, control e whitespace |
| `InventoryIdTest` | round-trip dei sei ID, quattro locale e reject di case/whitespace/confusable |
| `JdbcScopedEndpointRepositoryTest` | round-trip service/PostgreSQL/JDBC/JSON, byte UTF-8, session timezone, DST, idempotency e audit |
| `PlatformCoreMigrationTest` | encoding e introspezione delle colonne temporali di dominio |
| `phase-1-internationalization-matrix.yml` | catalogo machine-readable di invarianti, casi e oracle |
| `phase1-p1-0109-gate.ps1` | presenza evidence, coverage minima e guardrail sul production source |

Comandi di accettazione:

```powershell
./scripts/phase1-p1-0109-gate.ps1
./mvnw.cmd -B -ntp clean verify
```

Il workflow CI pubblica `target/phase1-p1-0109-evidence/internationalization-gate-report.json`. Dati e ID di test sono sintetici; non sono usati record sanitari reali.

## 12. Criteri di completamento

- [x] Unicode ben formato verificato prima della persistence.
- [x] Limite misurato in code point, incluse supplementary plane.
- [x] NFD/multiscript/emoji preservati in service, DB e JSON.
- [x] PostgreSQL server/client UTF-8 verificati.
- [x] ID originali canonici preservati e varianti respinte.
- [x] Nessuna dipendenza dalla locale predefinita o da `Accept-Language`.
- [x] Nessuna colonna temporale di dominio `timestamp without time zone`.
- [x] DST overlap e quattro timezone di sessione qualificate.
- [x] Idempotency expiry e audit chain timezone-independent.
- [x] Matrice machine-readable, gate CI e fonti aggiornate.

## 13. Limiti residui e ownership

| Tema | Stato dopo P1-0109 | Chiusura |
|---|---|---|
| seed e query regression multi-tenant/multi-facility | consegnati successivamente, senza claim production | [P1-0110](phase-1-p1-0110-implementation.md) |
| ricerca/ordinamento localizzato | non implementato | work item API/search dedicato |
| identificativi e timestamp clinici originali | non nel vertical slice | WP1-02 e semantic/mapping |
| timezone/offset sorgente di eventi business | modello futuro separato | canonical model |
| test browser/font/grapheme/bidi UI | non applicabile alla API corrente | WP1-09 |
| security decision pre-controller nel sink indipendente | invariato | WP1-10 |
| failover reale, restore e capacity | contratto definito, prova E2E residua | WP1-11/WP1-12 |

## 14. Fonti ufficiali

Fonti consultate e ricontrollate il **17 settembre 2026**:

1. Unicode Consortium, [Unicode Standard Annex #15 — Unicode Normalization Forms](https://www.unicode.org/reports/tr15/), Unicode 18.0.0, revisione 58 del 12 agosto 2026 — equivalenza canonica/compatibility e forme di normalizzazione.
2. IETF, [RFC 8259 — The JavaScript Object Notation (JSON) Data Interchange Format](https://www.rfc-editor.org/info/rfc8259/), dicembre 2017 — UTF-8 per interoperabilità e semantica delle stringhe JSON.
3. PostgreSQL Global Development Group, [PostgreSQL 18 — Date/Time Types](https://www.postgresql.org/docs/18/datatype-datetime.html) — conversione e rappresentazione di `timestamp with time zone`.
4. PostgreSQL Global Development Group, [PostgreSQL 18 — String Functions and Operators](https://www.postgresql.org/docs/18/functions-string.html) — `char_length`, `octet_length` e funzioni Unicode.
5. PostgreSQL Global Development Group, [PostgreSQL 18 — Character Types](https://www.postgresql.org/docs/18/datatype-character.html) e [Character Sets](https://www.postgresql.org/docs/18/infoschema-character-sets.html) — storage testuale ed encoding.
6. PostgreSQL Global Development Group, [PostgreSQL 18 — Collation Support](https://www.postgresql.org/docs/18/collation.html) — collation e dipendenze di versione.
7. PostgreSQL Global Development Group, [PostgreSQL 18 — `pg_timezone_names`](https://www.postgresql.org/docs/18/view-pg-timezone-names.html) e [Handling of Invalid or Ambiguous Timestamps](https://www.postgresql.org/docs/18/datetime-invalid-input.html) — nomi IANA e gap/overlap DST.
8. Oracle, [Java SE 21 `String`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html) e [`Locale`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Locale.html) — code point, UTF-16, `strip` e locale.
9. PostgreSQL JDBC Driver, [Using the Driver — Date and Time](https://jdbc.postgresql.org/documentation/query/) — mapping JDBC 4.2 di `TIMESTAMP WITH TIME ZONE` a `OffsetDateTime`.

Le fonti definiscono la semantica tecnica; le scelte di prodotto — conservazione della sequenza, trim al bordo, ID ASCII e assenza di ricerca localizzata — sono decisioni esplicite HyperHealth Connect e sono rese verificabili dai test sopra elencati.
