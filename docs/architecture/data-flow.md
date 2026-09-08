# Flussi dati e lifecycle dell'evento

Stato: baseline architetturale 1.0  
Data di riferimento: 1 settembre 2026  
Target: correttezza, tracciabilità e continuità enterprise

## 1. Obiettivo

Questo documento definisce il lifecycle di ogni evento HHC, i durability point, le semantiche di ACK, le transizioni di stato, il percorso sincrono/asincrono, il fan-out, la gestione degli errori e il replay. Il fine è impedire perdita silenziosa, doppie consegne non controllate e reinterpretazioni non tracciabili.

## 2. Modello dell'evento

Un evento HHC è composto da:

- **Integration Envelope**: identità tecnica, scope, correlation, standard, stato e versioni;
- **Raw payload reference**: riferimento a oggetto immutabile, checksum, size, media type e encryption context;
- **Derived representations**: parsed, validated, canonical e transformed, ognuna immutabile e versionata;
- **Processing ledger**: step, tentativi, tempi, outcome e errori;
- **Delivery ledger**: destinazioni, request identity, receipt e riconciliazione;
- **Semantic provenance**: mapping, terminology snapshot, confidence/status e quality rules;
- **Lineage edges**: relazioni fra input, derivati, delivery e dataset;
- **Audit references**: operazioni umane o privilegiate, non l'intero log applicativo.

Il raw non viene sovrascritto. Una correzione genera un nuovo evento collegato tramite relazione esplicita.

## 3. Stati principali

```text
RECEIVED
  ↓
DURABLE
  ↓
PARSED ───────────────→ QUARANTINED
  ↓
VALIDATED ────────────→ REJECTED
  ↓
NORMALIZED (optional)
  ↓
TRANSFORMED
  ↓
ROUTED
  ├──→ DELIVERING → DELIVERED
  │                 ↘ PARTIALLY_DELIVERED
  │
  └──→ PROJECTION_QUEUED → PROJECTED

Errori retryable: RETRY_SCHEDULED → stato precedente
Errori terminali: FAILED / DEAD_LETTERED / QUARANTINED
```

Gli stati sono append-only nel ledger. `DELIVERED` non significa che il destinatario abbia usato clinicamente il dato: significa esclusivamente che la receipt prevista dal contratto è stata ottenuta.

## 4. HHC Integration Envelope minimo

```yaml
envelopeVersion: hhc-envelope/v1
messageId: uuid
correlationId: uuid-or-external-correlation
causationId: optional-message-id
tenantId: immutable-id
organizationId: immutable-id
facilityId: immutable-id
sourceApplicationId: immutable-id
sourceEndpointId: immutable-id
flowId: logical-id
flowVersion: immutable-version
receivedAt: utc-instant
sourceTimestamp: optional-original-time
standard: HL7V2|FHIR|CDA|DICOM|JSON|XML|FILE|OTHER
standardVersion: explicit
messageType: explicit
contentType: media-type
payloadRef: opaque-object-reference
payloadChecksum: sha-256-or-approved
classification: health-data-class
serviceTier: TIER_0|TIER_1|TIER_2
traceId: w3c-trace-id
```

Campi aggiuntivi sono definiti per stato, idempotency, mapping, terminology, consent/purpose e retention. L'envelope non contiene nome, MRN, diagnosi o payload clinico salvo eccezione formalmente approvata.

## 5. Ingest e durability

### 5.1 Sequenza

1. Il Connector Gateway accetta la connessione entro rate, size e identity policy.
2. Verifica framing/protocollo minimo sufficiente a delimitare il messaggio.
3. Risolve endpoint e flow senza analizzare contenuto oltre il necessario.
4. Genera `messageId`, `traceId`, receive timestamp e scope.
5. Scrive il raw in streaming, calcola checksum e applica encryption context.
6. Registra envelope e stato `DURABLE` nel ledger.
7. Solo dopo il durability point emette ACK positivo se richiesto dal service tier.
8. Pubblica il processing command mediante outbox o queue durabile.

### 5.2 ACK policy

| Policy | ACK positivo quando | Uso |
|---|---|---|
| `ACK_ON_RECEIVE` | framing valido e buffer volatile | Vietata per Tier 0; solo use case espliciti senza garanzia |
| `ACK_ON_DURABLE` | raw e ledger sono durabili localmente | Default Tier 0/Tier 1 |
| `ACK_ON_VALIDATE` | parse e validazione contrattuale sono completati | HL7/app che richiedono application ACK |
| `ACK_ON_DELIVERY` | destinazione obbligatoria ha confermato | Solo contratti sincroni con time budget compatibile |

Un ACK positivo non può precedere il punto dichiarato. Se raw store centrale è irraggiungibile ma lo spool locale HA è disponibile, il flow può usare `ACK_ON_DURABLE` locale e segnare replica pending.

## 6. Fast path sincrono

```text
Source
  │ request
  ▼
Gateway → durable ingest → parse/validate → map/route → Destination
  │                           │                          │
  │                           └── trace + state          │ response/ACK
  └──────────────── response within time budget ◄───────┘
```

Regole:

- il flow dichiara end-to-end deadline e budget per step;
- chiamate esterne hanno timeout minore del deadline residuo;
- semantic enrichment remoto e analytics non entrano nel fast path salvo requisito indispensabile e fallback definito;
- retry sincroni sono pochi, bounded e con jitter; il resto passa ad asincrono se il protocollo lo consente;
- il timeout non implica che la destinazione non abbia elaborato: il delivery ledger marca outcome `UNKNOWN` e avvia riconciliazione;
- la risposta preserva semantica del protocollo senza esporre stack trace o dettagli interni.

## 7. Percorso asincrono ed event fan-out

L'event path usa topic/queue distinti per fase e service tier. Il partizionamento viene scelto per ordering key, non per semplicità operativa.

```text
ingest.accepted
  ├── operational.delivery.<domain>
  ├── semantic.processing.<domain>
  ├── projection.fhir.<profile>
  ├── projection.omop.<domain>
  └── governance.quality.<domain>
```

Ogni consumer:

- possiede consumer group, retry topic/queue e DLQ propri;
- aggiorna un ledger idempotente;
- non committa l'offset prima del durability point successivo;
- applica max in-flight e rate limit per destinazione;
- misura lag e **oldest event age**, più rilevante del solo numero di messaggi;
- usa tombstone o cancellazioni soltanto secondo retention e contratto.

## 8. Parse, validation e trasformazioni

### 8.1 Parse

Il parser selezionato è registrato nell'envelope. Errori di syntax non cancellano il raw. Informazioni recuperate parzialmente sono marcate incomplete e non usate per routing clinico se la policy richiede validità completa.

### 8.2 Validation

I livelli sono:

1. protocol/framing;
2. syntax/schema;
3. profile/Implementation Guide;
4. business rule locale;
5. semantic/terminology;
6. privacy/security policy.

Ogni regola produce `PASS`, `WARN`, `FAIL` o `NOT_APPLICABLE`, con versione. Severity e disposition sono distinte: un errore grave può essere `quarantine`, mentre un warning può consentire delivery ma bloccare la proiezione analitica.

### 8.3 Mapping tecnico

Il mapping crea un derivato e registra mapping ID/version, input checksum e output checksum. I lookup esterni hanno snapshot o response evidence. Funzioni non deterministiche, come `now()`, sono vietate o ricevono un processing instant fissato nel context.

### 8.4 Mapping semantico

Un mapping produce:

- source code/system/display;
- target concept/system/version;
- mapping rule e relationship;
- vocabulary snapshot;
- status `MAPPED`, `UNMAPPED`, `AMBIGUOUS`, `INVALID_DOMAIN`, `LOCAL_ONLY`;
- provenance e reviewer.

`UNMAPPED` non diventa concept `0` senza le convenzioni esplicite della proiezione target.

## 9. Routing e fan-out

Il flow dichiara completion policy:

- `ALL_REQUIRED`: tutte le destinazioni obbligatorie devono confermare;
- `ANY_REQUIRED`: almeno una destinazione del gruppo;
- `PRIMARY_ONLY`: la primaria determina il response, le altre sono asincrone;
- `INDEPENDENT`: ogni destinazione ha outcome autonomo.

Destinazioni best-effort non possono essere usate per dati clinici critici senza risk acceptance. `PARTIALLY_DELIVERED` resta uno stato operativo con alert e non viene compresso in `DELIVERED`.

## 10. Idempotenza e deduplica

La chiave di idempotenza è costruita da campi dichiarati, per esempio:

```text
tenant + source endpoint + source message control ID + business event ID
```

Il checksum da solo non basta quando due eventi identici sono legittimi; il source ID da solo non basta se il mittente lo ricicla. La policy definisce finestra, scope e comportamento:

- `DROP_WITH_RECEIPT` per duplicato certo già consegnato;
- `LINK_AND_PROCESS` per retransmission che richiede nuova delivery;
- `QUARANTINE` per collisione con payload diverso;
- `ALLOW` quando eventi identici sono ammessi.

Il dedup store è durable per almeno la finestra massima di retry/replay applicabile.

## 11. Ordering

Ordering globale non è promesso. Il flow definisce partition key, tipicamente paziente, episodio, ordine, studio o device. Per eventi clinici correlati:

- sequence/version originaria è preservata;
- gap e out-of-order sono rilevati;
- buffer di riordino è bounded nel tempo;
- superata la finestra si applica policy: process-with-warning, quarantine o wait;
- correzioni/cancellazioni usano causation e business version, non l'ordine di arrivo soltanto.

## 12. Errori, retry, DLQ e quarantena

| Stato | Significato | Azione |
|---|---|---|
| `RETRY_SCHEDULED` | causa transitoria e retry budget disponibile | backoff con jitter |
| `CIRCUIT_OPEN` | destinazione protetta dopo errori ripetuti | probe controllati, niente storm |
| `DEAD_LETTERED` | retry esauriti o errore non retryable tecnico | operatore/runbook/replay |
| `QUARANTINED` | rischio semantico, identity, privacy o collisione | revisione autorizzata |
| `REJECTED` | input non accettato secondo contratto | risposta negativa e audit |
| `UNKNOWN` | side effect remoto non determinabile | riconciliazione prima del retry |

La DLQ non è un archivio permanente. Età, crescita e contenuto sono monitorati; l'operatore non può modificare manualmente il payload e reinserirlo come se fosse originale.

## 13. Replay e reprocessing

### 13.1 Delivery replay

Riutilizza lo stesso output e stessa versione, crea un nuovo attempt ID e applica idempotency policy della destinazione.

### 13.2 Transformation replay

Riparte dal raw con una nuova flow/mapping version e genera una nuova derivazione. Non sovrascrive l'output precedente.

### 13.3 Semantic reprocessing

Ricalcola canonical event e mapping con snapshot dichiarati. Deve esplicitare se usa la terminologia storica o aggiornata.

### 13.4 OMOP rebuild

Costruisce una nuova dataset version in staging, la valida e la pubblica solo dopo quality gate. Il dataset precedente rimane `SUPERSEDED`, non viene mutato.

Ogni replay richiede scope, motivo, preview, impact count, autorizzazione, rate limit, finestra e audit. Replay massivi o cross-facility richiedono four-eyes.

## 14. Data protection nel flow

- Il raw è accessibile solo tramite servizio autorizzato, non con URL diretto permanente.
- Log e trace contengono identificativi tecnici, non patient ID o payload.
- Un patient token di ricerca è keyed, tenant-scoped, ruotabile e non esportato al SIEM se non necessario.
- Free-text viene classificato e può essere quarantinato prima dell'analytics.
- Pseudonimizzazione avviene al boundary dichiarato; la re-identification key è separata.
- Restrizioni e permit sono valutati prima delle proiezioni secondarie.
- Retention metadata accompagna raw e derivati; legal hold impedisce cancellazione soltanto nello scope autorizzato.

## 15. Esempio: HL7 ORU verso FHIR e OMOP

```text
LIS ORU_R01
  → MLLP framing + endpoint authentication
  → raw durable + ACK
  → HAPI HL7 parse / profile validation
  → technical mapping
  ├──→ FHIR Observation projection → FHIR Server
  └──→ canonical LaboratoryObservation
        → terminology mapping LOINC/unit
        → OMOP staging MEASUREMENT
        → dataset quality gate → READY
```

Il FHIR delivery e l'OMOP projection hanno stati indipendenti. Un mapping OMOP fallito non trasforma in errore la consegna clinica FHIR già valida; genera quality alert e backlog semantico.

## 16. Reconciliation

Job periodici confrontano:

- ingress count vs raw committed;
- raw committed vs processing commands;
- routed vs delivery ledger;
- broker offset vs persisted outcome;
- canonical produced vs projection input;
- projection input vs staging rows;
- staging vs published dataset manifest;
- audit sequence vs exported sequence.

Ogni differenza genera un finding con scope, first/last seen, impact estimate e runbook. La riconciliazione è parte della correttezza, non soltanto dell'osservabilità.

## 17. Telemetria minima per evento e flow

- throughput e success/failure rate;
- processing latency p50/p95/p99 per step;
- end-to-end latency e delivery freshness;
- queue depth, oldest age e consumer lag;
- retry, circuit state, DLQ e quarantine;
- duplicate/collision rate;
- validation e mapping failure;
- semantic coverage e projection lag;
- raw/metadata/audit durability error;
- version distribution durante rollout.

Label ad alta cardinalità come message ID sono usate nei trace/log protetti, non nelle metriche time-series.

## 18. Invarianti verificabili

1. Ogni ACK positivo Tier 0 corrisponde a un evento durabile.
2. Ogni derivato ha input, algoritmo/config versione e checksum.
3. Ogni delivery ha attempt ID e outcome normalizzato.
4. Nessun evento cambia tenant/facility durante la pipeline senza una route esplicita e autorizzata.
5. Analytics e OMOP non bloccano il percorso clinico.
6. Nessun replay elimina la storia precedente.
7. Un errore non è successo senza policy esplicita.
8. Ogni accesso al raw è autorizzato e auditato.
9. Ogni dataset `READY` ha manifest e quality evidence.
10. Ogni gap fra contatori persistenti è rilevabile dalla riconciliazione.

## 18. Fonti ufficiali

Consultate o riconfermate il 5 settembre 2026:

- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [CloudEvents 1.0.2](https://github.com/cloudevents/spec/tree/v1.0.2)
- [RFC 9110 HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110)
- [OpenTelemetry specification](https://opentelemetry.io/docs/specs/otel/)
