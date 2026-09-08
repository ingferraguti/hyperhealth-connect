# HHC Integration Envelope

Stato: specifica baseline 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Target: contratto tecnico stabile per ogni evento, file, documento e oggetto gestito da HHC

## 1. Finalità

L'Integration Envelope rende ogni unità di lavoro identificabile, isolata, idempotente, tracciabile e riproducibile indipendentemente dal payload. Non è un clinical model e non duplica i dati sanitari. Envelope, raw payload, derivati, ledger, audit e lineage hanno lifecycle separati.

Il formato HHC è ispirato ai principi CloudEvents 1.0.2 e usa W3C Trace Context, ma la conformità CloudEvents è dichiarata solo da binding dedicati. I campi HHC aggiuntivi sono necessari per tenancy, service tier, privacy, mapping e delivery.

## 2. Invarianti

1. `messageId` identifica una singola ricezione HHC e non cambia.
2. Il raw associato è immutabile; checksum e byte count sono obbligatori al punto `DURABLE`.
3. Tenant e source endpoint derivano da identità/configurazione attendibili, non dal solo payload.
4. Ogni versione applicata è esatta e immutabile, mai `latest`.
5. Le transizioni di stato sono append-only e monotone per attempt, salvo compensazioni esplicite.
6. Un replay crea un nuovo processing run e conserva l'evento originale.
7. Dati clinici non compaiono nei campi indicizzabili dell'envelope.
8. Delivery è at-least-once; idempotenza e receipt sono per destinazione.
9. Ogni timestamp ha semantica e precisione dichiarate.
10. L'envelope resta interpretabile durante almeno l'intera retention dei dati referenziati.

## 3. Schema logico

```yaml
envelopeVersion: hhc-envelope/v1
messageId: urn:uuid:...
eventName: clinical.result.received
eventVersion: "1.0"
correlationId: opaque-id
causationId: optional-message-id
conversationId: optional-opaque-id

scope:
  environmentId: prod-eu
  tenantId: t-...
  organizationId: o-...
  facilityId: f-...
  applicationId: a-...
  sourceEndpointId: ep-...
  runtimeCellId: rc-...

source:
  connectorType: hl7v2-mllp
  connectorVersion: exact-build
  authenticatedPrincipalId: workload-...
  protocolMessageId: redacted-or-tokenized-value
  sequenceKeyHash: optional-hmac

content:
  standard: HL7V2|FHIR|CDA|DICOM|JSON|XML|FILE|OTHER
  standardVersion: exact
  profileIds: [canonical-id|version]
  messageType: ORU_R01
  mediaType: application/hl7-v2
  charset: UTF-8
  payloadRef: opaque-object-reference
  payloadBytes: 1234
  payloadChecksum:
    algorithm: SHA-256
    value: base64url
  compression: none|gzip|other
  encryptionContextId: opaque-id

time:
  receivedAt: 2026-09-01T10:15:30.123456Z
  durableAt: 2026-09-01T10:15:30.130000Z
  sourceTime:
    value: original-lexical-or-normalized
    precision: second|minute|day|unknown
    offsetKnown: true
  traceTimeQuality: synchronized|estimated|unknown

processing:
  flowId: flow-...
  flowVersion: immutable-release
  serviceTier: TIER_0|TIER_1|TIER_2
  priority: 0..9
  orderingKeyHash: optional-hmac
  idempotencyKey: opaque-id
  ackPolicy: DURABLE|VALIDATED|DELIVERED
  deadlineAt: optional-instant
  replayOf: optional-message-id
  processingRunId: run-...

governance:
  classification: HEALTH_DATA
  purposeIds: [care-delivery]
  legalPolicyRef: immutable-reference
  consentPolicyRef: optional-reference
  retentionPolicyRef: immutable-reference
  residencyPolicyRef: immutable-reference
  handlingCaveats: [no-cross-border]

versions:
  parser: exact
  schema: exact
  technicalMapping: exact-or-null
  semanticMappingRelease: exact-or-null
  terminologySnapshot: exact-or-null
  policyBundle: exact
  softwareBuild: exact

trace:
  traceparent: W3C-value
  tracestate: redacted-safe-value

integrity:
  envelopeChecksum: sha256
  previousLedgerHash: optional
  signatureRef: optional-batch-signature
```

## 4. Cardinalità e regole

| Gruppo | Obbligatorio a `RECEIVED` | Obbligatorio a `DURABLE` | Note |
|---|---:|---:|---|
| identity/event | sì | sì | ID globalmente unico e event name registrato |
| scope | sì | sì | facility può essere `unknown` solo in quarantine |
| source | sì | sì | principal autenticato o explicit legacy risk |
| content metadata | parziale | completo | payloadRef/checksum/bytes al durability point |
| time | receivedAt | durableAt | UTC; source precision distinta |
| processing | flow/tiers | run/idempotency | versioni immutabili |
| governance | classification | policy refs | default-deny se non risolto |
| versions | policy/build | parser/schema | mapping può arrivare allo step relativo |
| trace | sì | sì | nessuna PHI |

Un campo sconosciuto nel consumer è ignorabile solo se non marcato `critical`. Le estensioni sono namespaced `x-<owner>-<name>` e registrate nello Schema Registry. I consumer rifiutano major version non supportate.

## 5. Serializzazione e binding

La rappresentazione di controllo preferita è JSON UTF-8 canonico; Avro/Protobuf può essere usato sul broker se generato dallo stesso schema logico. Il payload grande non è inline. Un binding CloudEvents mappa almeno `id`, `source`, `type`, `specversion`, `time`, `subject`, `datacontenttype` e usa extension attribute HHC; `source + id` resta univoco.

Regole di compatibilità:

- aggiunta di campo opzionale: minor;
- restrizione, rename o cambio semantico: major;
- enum extensible con unknown-safe behavior;
- schema artifact firmato e pin-nato nel release bundle;
- producer e consumer supportano rolling upgrade N/N-1;
- canonical serialization usata solo per checksum/firma, non l'ordine ricevuto dal payload.

## 6. Identità e scope multi-tenant

La gerarchia è Tenant → Organization → Facility → Application → Endpoint. `runtimeCellId` identifica il failure domain, non ownership clinica. Uno scope mismatch tra identity, endpoint registry e payload causa `SECURITY_SCOPE_MISMATCH`; non viene “corretto” dal mapper.

Gli ID sono opachi. MRN, codice fiscale, accession number e UID clinici non sono envelope identifier pubblici. Quando servono dedup/ordering, sono HMAC con key scoped e rotation version; il valore raw resta nel payload/canonical protetto.

## 7. Tempo

Si distinguono:

- `sourceTime`: tempo dichiarato dal sistema clinico, con precisione/offset;
- `receivedAt`: ingresso al trust boundary HHC;
- `durableAt`: commit locale;
- step start/end nel processing ledger;
- delivery request/receipt;
- event/effective time nel canonical model.

NTP/PTP/CT sono monitorati. Clock skew oltre soglia genera finding ma non riscrive il tempo sorgente. Sort e SLO usano timestamp appropriati; source time non misura latenza se l'offset è ignoto.

## 8. Stato e ledger

Gli stati di alto livello sono `RECEIVED`, `DURABLE`, `PARSED`, `VALIDATED`, `NORMALIZED`, `TRANSFORMED`, `ROUTED`, `DELIVERING`, `DELIVERED`, `PARTIALLY_DELIVERED`, `PROJECTION_QUEUED`, `PROJECTED`, `RETRY_SCHEDULED`, `QUARANTINED`, `REJECTED`, `FAILED`, `DEAD_LETTERED`.

L'envelope conserva stato corrente materializzato per query, ma il ledger append-only è autorevole. Ogni transition include sequence, attempt ID, actor/build, input/output refs, versioni, start/end, outcome, error category, retryable e hash chain. Transizioni concorrenti usano optimistic concurrency.

## 9. Idempotenza, retry e ordering

`messageId` distingue ricezioni; `idempotencyKey` identifica l'effetto desiderato. Ogni destination ha `deliveryId` stabile per retry. Se la risposta è persa, lo stato è `UNKNOWN` finché receipt/read-back/reconciliation non risolve.

Ordering è limitato a `orderingKeyHash` e partition. I flow dichiarano: strict, bounded-reorder o unordered. Un poison event non blocca indefinitamente la partition: dopo policy va in quarantine mantenendo gap evidence.

Retry è bounded, exponential backoff con jitter, retry budget e deadline. Un errore permanent non viene ritentato. DLQ non è archivio: ha owner, alert, retention e workflow.

## 10. Payload reference e integrità

`payloadRef` è opaco e risolto solo dal Raw Event Service. Non è URL esterno né contiene identità clinica. Accesso richiede authorization, purpose, reason e audit. Presigned URL, se usato, ha TTL minimo e non viene loggato.

Checksum è calcolato streaming sui byte canonici ricevuti prima di eventuale decompressione, con checksum separato per derivati. Algoritmi deprecati sono ammessi solo come valore sorgente aggiuntivo, non come controllo HHC. Object e metadata sono riconciliati per evitare orfani.

## 11. Privacy e retention

Envelope è minimizzato ma può restare dato personale per correlabilità. Retention di metadata, raw, audit e trace è distinta. Legal hold non si propaga implicitamente: è registrata per artifact. La cancellazione elimina riferimenti/indici secondo policy e produce evidence priva del contenuto.

`purposeIds` descrive la finalità autorizzata, non crea una base giuridica. Il PDP valuta policy, consent/restriction, role, permit e context. Dati pseudonimizzati restano personali quando re-identificabili.

## 12. Performance, HA e DR

Envelope deve restare piccolo: target ≤16 KiB serializzato senza payload, con limite hard configurato. Campi ripetuti e diagnostic estesi vanno in ledger/artifact. Partition key distribuisce carico evitando hot patient/facility; metriche high-cardinality non usano ID envelope come label.

La scrittura raw+envelope raggiunge durability locale prima dell'ACK. Outbox garantisce pubblicazione successiva. Per Tier 0: RPO locale 0 dopo ACK, cross-site ≤5 minuti, RTO ≤30 minuti, SLO 99,99%. Il restore verifica referential integrity, checksum, ledger sequence e policy/version availability.

## 13. Audit e osservabilità

Envelope ID è correlation pivot per metriche, log, trace, audit e lineage. L'audit registra lettura raw, replay, override, change, export e privileged access. Trace W3C è propagato fra servizi; valori non fidati sono validati e rigenerati al trust boundary. Baggage non contiene PHI.

Alert: envelope senza raw dopo durability, checksum mismatch, scope mismatch, ledger gap, version artifact mancante, event age sopra SLO, replication lag sopra RPO, policy unresolved e ID collision.

## 14. Casi d'uso

### UC-ENV-01 — fan-out clinico e analitico

Un ORU diventa durabile una volta, poi alimenta delivery EHR e proiezione OMOP con run distinti. Accettazione: stessa fonte/checksum, versioni differenti visibili, errore OMOP non cambia ACK clinico.

### UC-ENV-02 — replay dopo correzione mapping

Il data steward pubblica mapping v2. Il replay crea nuovo run collegato a `replayOf`, non altera raw o output v1. Accettazione: confronto v1/v2, approvazione, audit e dataset version separata.

### UC-ENV-03 — disaster recovery

Il sito secondario ripristina raw, envelope e ledger. Accettazione: ogni evento ACKed è presente localmente, perdita cross-site entro RPO, gap/hash/checksum verificati prima della riapertura.

## 15. Test e gate

- JSON/schema compatibility N/N-1 e unknown fields;
- ID collision, HMAC rotation e scope spoofing;
- atomicità object/metadata/outbox e crash in ogni punto;
- duplicate/reorder/retry/unknown receipt;
- envelope size e throughput benchmark;
- encryption/key unavailability e restore;
- audit/trace correlation senza PHI;
- migration e replay riproducibile.

## 16. Fonti ufficiali verificate

- [CloudEvents specification repository](https://github.com/cloudevents/spec) e [release 1.0.2](https://github.com/cloudevents/spec/releases), consultati il 1 settembre 2026. HHC usa i principi; la conformance è solo per binding dichiarati.
- [W3C Trace Context, Recommendation](https://www.w3.org/TR/trace-context/), consultata il 1 settembre 2026.
- [W3C PROV overview](https://www.w3.org/TR/prov-overview/), consultata il 1 settembre 2026 per il raccordo con provenance.

## 17. Collegamenti

- `canonical-semantic-event.md`
- `../architecture/data-flow.md`
- `../architecture/data-architecture.md`
- `../semantic/lineage-and-provenance.md`
- `../operations/replay-and-reprocessing.md`
