# Connector SDK

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ambito | Connector ufficiali HHC e di terze parti |
| Target | Runtime Cell multiazienda e multifacility di produzione |
| Ultimo aggiornamento | 1 settembre 2026 |
| Responsabili | Connector Platform, Runtime, Security, Interoperability, SRE |
| Compatibility | SDK SemVer; matrice Runtime N/N-1 dichiarata |

## 1. Finalità

Il Connector SDK è il confine stabile tra il Flow Runtime HHC e protocolli, prodotti o infrastrutture esterni. Astrae lifecycle, configurazione tipizzata, capability, streaming, ingress/egress, receipt, health, retry classification, metriche, audit e sicurezza. Apache Camel, HAPI, IPF, dcm4che, driver JDBC e client vendor sono dipendenze sostituibili dietro questo confine.

Un connector traduce il protocollo; non decide autonomamente tenancy, policy, mapping clinico, retention, replay o successo business. Le regole API sono in [api-standards.md](../api/api-standards.md) e il lifecycle degli eventi nell'[Integration Envelope](../canonical-model/integration-envelope.md).

## 2. Obiettivi non negoziabili

1. Un connector guasto o malevolo non compromette altre Runtime Cell, tenant o flow.
2. Un ACK positivo non precede il boundary di durabilità dichiarato.
3. Timeout e response perse producono stato `UNKNOWN`, non successo o retry cieco.
4. Input raw è preservato; parsing/normalizzazione non lo sovrascrivono.
5. Backpressure è esplicita e bounded; nessun buffer infinito.
6. Secret e PHI non entrano in config artifact, log, metriche o exception.
7. Capability e limiti sono machine-readable e qualificati.
8. Artifact è firmato, content-addressed, con SBOM e provenienza.
9. Upgrade/rollback non cambiano semantica di eventi in volo senza regola.
10. Ogni connector ha contract test, security test, performance profile e runbook.

## 3. Modello di estensione

### 3.1 Porte logiche

Il runtime espone porte, non classi interne:

| Porta | Responsabilità |
|---|---|
| `ConnectorFactory` | validare manifest e creare instance |
| `Lifecycle` | initialize/start/drain/stop/close |
| `IngressSource` | ricevere frame e consegnarli al runtime |
| `EgressSink` | inviare delivery e produrre receipt |
| `RequestReply` | correlare request/response entro deadline |
| `HealthContributor` | readiness/liveness/dependency health |
| `MetricsContributor` | metriche bounded-cardinality |
| `DiagnosticContributor` | support bundle redatto |
| `ConfigValidator` | schema più invariant e connection test |

L'implementazione può essere in-process per connector ufficiali altamente fidati o out-of-process/sidecar per isolamento. Il wire contract dell'out-of-process adapter è versionato e usa local authenticated transport, message size limit, deadline e cancellation.

### 3.2 Modalità di esecuzione

| Modalità | Uso | Controlli |
|---|---|---|
| Built-in trusted | connector ufficiale critico | dependency allowlist, code review, Runtime release |
| Isolated process | terza parte o parser ad alto rischio | OS identity, filesystem/network policy, cgroup, seccomp equivalente |
| Dedicated Runtime Cell | rischio/blast radius massimo | infrastruttura, queue e secret separati |

Plug-in arbitrario nello stesso processo non è il default enterprise.

## 4. Manifest

```yaml
apiVersion: connectors.hhc.example/v1
kind: ConnectorDefinition
metadata:
  id: hl7v2-mllp
  version: 1.4.0
  publisher: hhc-official
artifact:
  digest: sha256:...
  signatureRef: sigstore-or-enterprise-signature
  sbomRef: artifact://sbom/...
sdk:
  range: ">=1.3.0 <2.0.0"
runtime:
  range: ">=1.8.0 <3.0.0"
capabilities:
  - INGRESS
  - EGRESS
  - STREAMING
  - ACK_ON_DURABLE
permissions:
  network:
    mode: declared-endpoints-only
  filesystem:
    mode: none
  secrets:
    - tls-client
resources:
  memoryMiB: 512
  cpuMillis: 500
  maxConcurrency: 64
configSchemaRef: schema://connector/hl7v2-mllp@1.4.0
testPackRef: testpack://connector/hl7v2-mllp@1.4.0
```

Manifest, binary/image, schema, SBOM, provenance e test pack sono immutabili e collegati per digest. Dichiarazioni più ampie dei permessi effettivi causano deny o review.

## 5. Capability model

| Capability | Significato |
|---|---|
| `INGRESS` | accetta dati dal partner |
| `EGRESS` | invia dati al partner |
| `REQUEST_REPLY` | correlazione sincrona nativa |
| `STREAMING` | payload senza materializzazione completa |
| `MANUAL_SOURCE_ACK` | ack/commit sorgente controllabile |
| `ACK_ON_DURABLE` | protocol response dopo callback di commit HHC |
| `ORDERED_SOURCE` | source order disponibile entro scope dichiarato |
| `DELIVERY_RECEIPT` | conferma nativa distinta dal transport accept |
| `TRANSACTIONAL_RECEIVE` | commit/rollback sorgente secondo contratto |
| `SCHEMA_DISCOVERY` | metadata discovery controllata |
| `HEALTH_PROBE` | test dependency non distruttivo |
| `BULK_TRANSFER` | file/object/result manifest |

Capability non significa garanzia end-to-end. `ORDERED_SOURCE` specifica chiave, condizioni e comportamento dopo reconnect; `DELIVERY_RECEIPT` distingue acceptance tecnica da applicazione clinica.

## 6. Lifecycle

Stati instance:

`INSTALLED → VALIDATING → DISABLED → STARTING → RUNNING → DRAINING → STOPPED`

Stati laterali: `DEGRADED`, `FAILED`, `QUARANTINED`, `REVOKED`.

### 6.1 Initialize

- verifica manifest/signature/digest/compatibility;
- applica permission/resource profile;
- valida config e secret reference metadata;
- crea pool senza aprire listener pubblici;
- registra health/metrics;
- non effettua side effect sul partner.

### 6.2 Start

- risolve secret con workload identity;
- apre listener/consumer/client;
- esegue dependency handshake;
- diventa ready solo dopo invariant minimi;
- fencing impedisce due active consumer/listener incompatibili.

### 6.3 Drain

- non accetta nuovo lavoro o arresta source consumption secondo protocollo;
- completa o restituisce al runtime le unità in volo;
- preserva receipt/offset/transaction state;
- scade entro deadline e rende espliciti gli esiti `UNKNOWN`.

### 6.4 Stop/close

Stop è idempotente. Rilascia connection, listener, thread, buffer e secret material in memoria; non cancella spool/ledger HHC. Crash recovery riparte da source/ledger checkpoint.

## 7. Configurazione tipizzata

Config è JSON/YAML validato da JSON Schema Draft 2020-12. Campi comuni:

```yaml
endpointRef: endpoint://ep-...
direction: INGRESS
protocol:
  version: "2.5.1"
connection:
  connectTimeoutMs: 5000
  readTimeoutMs: 30000
  maxConnections: 32
tls:
  mode: MTLS
  trustBundleRef: trust://...
  clientSecretRef: secret://...
limits:
  maxPayloadBytes: 65536
  maxInFlight: 1000
  ratePerSecond: 500
failurePolicyRef: policy://delivery/...
```

### 7.1 Regole

- nessun secret literal;
- address deriva dall'Endpoint Registry e non è liberamente sovrascrivibile;
- unità sempre nel nome o tipo (`Ms`, `Bytes`);
- default espliciti e stabili nella minor version;
- unknown property rifiutata per config critica;
- config active immutabile; update crea release;
- schema include sensitivity e restart impact;
- validation offline distinta da test connection;
- egress destination allowlist contro SSRF.

## 8. Ingress contract

```text
onFrame(InboundFrame frame, IngressContext context) -> CompletionStage<IngressDecision>
```

`InboundFrame` contiene stream/read-only bytes, protocol metadata allowlisted, remote peer identity, receive timestamp, source sequence/offset se presente e connector instance/version. Non contiene tenant scelto dal peer: il runtime risolve scope da endpoint/identity.

`IngressContext` offre:

- deadline/cancellation;
- correlation/trace context filtrato;
- `acceptDurably(raw, metadata)` callback;
- `reject(reason)`;
- bounded temp storage;
- safe logger/metrics;
- schema/profile resolver locale.

### 8.1 Decisioni

| Decisione | Significato |
|---|---|
| `ACCEPTED_DURABLE` | HHC ha committato raw+ledger secondo ACK policy |
| `ACCEPTED_DEFERRED` | protocollo non richiede ACK sincrono; lavoro persistito |
| `REJECTED_PERMANENT` | richiesta non accettabile, niente retry automatico sorgente |
| `REJECTED_TEMPORARY` | capacità/dipendenza temporanea, sorgente può ritentare |
| `UNKNOWN` | connessione/protocollo impedisce esito affidabile |

Il connector costruisce ACK/status nativo soltanto dalla decisione runtime e dal profilo. Non può simulare `ACCEPTED_DURABLE` perché il parser ha avuto successo.

## 9. Egress contract

```text
deliver(OutboundDelivery delivery, EgressContext context) -> CompletionStage<DeliveryReceipt>
```

`OutboundDelivery` include `deliveryId` stabile, payload stream/reference, content type, protocol contract, idempotency/business key, deadline, attempt number, target endpoint e trace context minimizzato.

### 9.1 Receipt normalizzata

| Stato | Significato |
|---|---|
| `DELIVERED_CONFIRMED` | partner ha confermato secondo il contratto dichiarato |
| `ACCEPTED_NOT_FINAL` | accettazione tecnica, esito business successivo |
| `REJECTED_PERMANENT` | errore non retryable senza modifica/intervento |
| `FAILED_TEMPORARY` | retry ammesso secondo policy |
| `THROTTLED` | partner/quota chiede attesa |
| `UNKNOWN` | side effect possibile, risposta non affidabile |
| `CANCELLED` | annullato prima di una conferma; side effect specificato |

Receipt contiene native code redatto, timestamp, remote receipt ID, retry-after, bytes e evidence reference. `UNKNOWN` non viene automaticamente ritentato se il side effect può duplicarsi: Delivery Manager interroga/reconcilia o applica la destination-specific idempotency strategy.

## 10. Error taxonomy

Connector traduce exception/libreria in:

- `CONFIGURATION`;
- `AUTHENTICATION`;
- `AUTHORIZATION`;
- `PROTOCOL_NEGOTIATION`;
- `VALIDATION_SYNTACTIC`;
- `VALIDATION_SEMANTIC`;
- `REMOTE_REJECTED`;
- `REMOTE_THROTTLED`;
- `NETWORK_TEMPORARY`;
- `TIMEOUT_AMBIGUOUS`;
- `RESOURCE_EXHAUSTED`;
- `INTERNAL_BUG`;
- `SECURITY_VIOLATION`;
- `DATA_INTEGRITY`.

Ogni error code è stabile, severity/retryability separate, native detail redatto. `INTERNAL_BUG`, `SECURITY_VIOLATION` e `DATA_INTEGRITY` possono quarantinare l'instance e aprire alert; nessun retry loop nasconde il difetto.

## 11. Retry, circuit breaker e backpressure

Il connector classifica; la policy di flow/runtime decide.

- timeout per connect/write/read/overall;
- exponential backoff con full jitter;
- max attempts, max elapsed e retry budget;
- rispetto di `Retry-After` entro policy;
- circuit breaker per destination/credential/path, non globale;
- half-open con probe limitata;
- concurrency semaphore e connection pool;
- bounded source prefetch;
- ingress overload rifiuta prima dell'ACK se non può persistere;
- egress slow consumer accumula in coda durabile HHC, non heap;
- live traffic e replay hanno pool/quota distinti.

## 12. Ordering, idempotenza e transazioni

- ordering scope dichiarato: connection, partition, patient/order, file row o nessuno;
- parallelism non supera lo scope senza resequencing esplicito;
- source offset/ack è committato dopo durability prevista;
- `deliveryId` resta stabile tra retry;
- duplicate detection usa business key/profile e hash, non solo timestamp;
- same key/different hash è collisione e quarantena;
- XA generico con sistemi legacy è vietato salvo supporto/qualifica espliciti;
- transactional source non crea exactly-once con destination esterna;
- lost response produce `UNKNOWN` e reconciliation.

## 13. Streaming e gestione payload

- API stream-oriented con backpressure/cancellation;
- checksum calcolato durante lettura/scrittura;
- limite prima e durante decompressione;
- temp file cifrato, scoped e con cleanup/TTL;
- niente copie multiple non necessarie;
- DICOM/file grandi non attraversano heap integralmente;
- parser può leggere view ma raw originale resta immutabile;
- zero-copy è ottimizzazione, non deroga a checksum/audit;
- payload non compare in exception/toString/debug.

## 14. Threading e resource ownership

- nessun thread non gestito che sopravvive a close;
- callback non blocca event loop con I/O lungo;
- executor bounded e nominato per connector/instance;
- context tenant/trace non affidato a thread-local non pulito;
- client/pool non condiviso tra tenant se credenziali o blast radius differiscono;
- memory/CPU/file descriptor/temp/disk/network quota;
- cancellation e interruption testate;
- leak test in soak ≥24 ore per stateful connector.

## 15. Health model

```json
{
  "status": "DEGRADED",
  "ready": true,
  "checks": [
    {"name": "listener", "status": "UP"},
    {"name": "destination", "status": "DEGRADED", "since": "..."}
  ],
  "activeConfigDigest": "sha256:...",
  "lastSuccessfulExchangeAt": "..."
}
```

- liveness: processo/event loop non bloccato, non chiama partner remoto;
- readiness ingress: può accettare e durare secondo policy;
- readiness egress: può ricevere lavoro dal dispatcher entro circuit policy;
- dependency health: stato separato, non causa restart storm;
- synthetic probe: protocol-specific, marcata test e non crea dato clinico;
- health detail redatto e scoped.

## 16. Observability

Metriche SDK comuni:

- connection/association/session active e failure;
- frame/message/byte rate;
- receive/durable/ACK e delivery latency p50/p95/p99;
- in-flight, prefetch, queue age/lag;
- retry, circuit state, unknown receipt e reconciliation age;
- parser/validation/native status;
- throttled/rate deny;
- config/signature/secret/certificate expiry;
- resource saturation e restart;
- drop count, che deve restare zero per eventi accettati.

Label ammesse: connector definition/version, instance ID opaco, protocol, direction, outcome, error class e tenant/facility solo secondo cardinality policy. Vietati patient ID, accession, message control ID, SOP UID, filename clinico, query e payload.

Trace attraversa connector/runtime/destination quando consentito. Header/baggage dal partner sono non fidati, limitati e rigenerati. Sampling non elimina audit.

## 17. Audit

Audit per install/upgrade/revoke, config/version, enable/disable, test connection, secret/trust binding, privileged diagnostic, manual ACK/offset action e security deny.

Event exchange audit minimo è nel runtime ledger: connector/version, instance, endpoint, message/delivery ID opaco, native outcome, timestamp, attempt e correlation. Non duplica PHI.

## 18. Sicurezza del connector

### 18.1 Permission manifest

Default deny per:

- network e DNS;
- filesystem/temp;
- secret categories;
- process execution;
- environment variables;
- metadata service/cloud API;
- Control Plane/database access;
- outbound telemetry.

Il runtime materializza solo permessi approvati. Network è limitata agli Endpoint Registry risolti con protezione SSRF/DNS rebinding. Terze parti non ricevono token Control Plane.

### 18.2 Parsing ostile

- fuzzing e corpus di regressione;
- nesting/length/field/segment/item limit;
- XXE/deserialization/script disabled;
- decompression bomb e malformed multipart;
- DICOM private tag/pixel size;
- HL7 escape/delimiter/charset;
- FHIR reference/extension/query complexity;
- file path traversal/symlink/archive bomb;
- JDBC metadata/result size e query template allowlist.

### 18.3 Supply chain

- artifact digest e trusted publisher;
- signature/provenance verificate prima del load;
- SBOM e license inventory;
- dependency pin e vulnerability monitoring;
- reproducible/hermetic build dove applicabile;
- no runtime download di codice/dipendenze;
- revocation list e emergency disable;
- retention degli artifact per riproduzione/forensics.

## 19. Secret e certificati

Connector riceve handle/credential material a tempo limitato, non legge secret per nome arbitrario. Rotation supporta overlap, new connection adoption, drain e revoke. Private key non è serializzata in diagnostic. Certificate validation verifica chain, SAN/identity, EKU/purpose, expiry e revocation policy; “trust all” è vietato.

KMS/HSM/secret manager outage ha comportamento dichiarato: connection esistenti possono continuare entro policy, nuove connection falliscono chiuse quando non possono autenticarsi. Nessuna credenziale embedded come fallback.

## 20. Packaging e compatibility

SemVer distingue:

- SDK contract version;
- connector definition version;
- runtime version;
- protocol/profile version;
- underlying library BOM.

Minor SDK aggiunge metodi/campi opzionali con default sicuro; major cambia contract. Runtime supporta N/N-1 dichiarato. Connector dichiara range minimo/massimo e capability negotiation; incompatibile non viene caricato.

Upgrade:

1. verify artifact/signature/SBOM;
2. offline config migration preview;
3. contract/security regression;
4. stage e synthetic health;
5. canary con traffico controllato;
6. drain old instance;
7. activate new version;
8. monitor e rollback.

Config migration è pura/deterministica e non accede a secret/partner. Rollback preserva ledger e payload version association.

## 21. Connector Test Kit

Ogni connector supera:

- manifest/schema/permission validation;
- lifecycle model test e repeated start/stop;
- contract positive/negative;
- retryability/error mapping;
- crash prima/dopo commit/ACK;
- lost response e unknown reconciliation;
- duplicate/reorder/backpressure/slow peer;
- parser fuzz/security payload;
- secret/trust rotation/revoke;
- resource quota/leak/soak;
- hot tenant e isolation;
- upgrade N/N-1/rollback;
- observability/audit/PHI leakage;
- protocol conformance e partner matrix;
- performance baseline e failure injection.

Test Kit produce Conformance Pack firmato secondo [conformance-and-interoperability.md](../testing/conformance-and-interoperability.md).

## 22. Prestazioni

Ogni manifest pubblica envelope qualificato:

- payload min/median/p95/max;
- concurrency e connection;
- sustained/burst throughput;
- connector-only latency;
- CPU/memory/temp/network;
- downstream assumptions;
- failure/backlog recovery rate.

HL7 v2 fast path ≤64 KiB contribuisce al target HHC p95 ≤50 ms/p99 ≤100 ms. Reference Runtime Cell sostiene ≥2.000 eventi/s asincroni da 8 KiB per 60 minuti con CPU media ≤70%, p99 ingest ≤250 ms e zero mismatch; un connector che partecipa deve dichiarare il proprio budget.

## 23. HA, BC e DR

Connector è stateless o mantiene solo protocol state ricostruibile; raw, spool, offset/receipt e ledger sono durabili nel Runtime domain.

Pattern:

- active-active se protocollo/partner/dedup lo consentono;
- active-passive con lease/fencing per listener/consumer singleton;
- per-source partition ownership per broker;
- load balancer health-aware per HTTP/FHIR/DICOMweb;
- connection drain per MLLP/DICOM/JMS;
- checkpoint/offset committato dopo durability.

Tier 0 eredita SLO 99,99%, RTO ≤30 minuti, RPO cross-site ≤5 minuti e RPO locale 0 dopo ACK durevole. Control Plane offline non ferma instance già configurate per ≥24 ore. DR include DNS, route, certificates, partner allowlist, source offset, spool/ledger, duplicate window e synthetic transaction; il failover non è riuscito finché la riconciliazione non chiude.

## 24. Casi d'uso sanitari europei

### SDK-UC-01 — HL7 v2 MLLP inbound Tier 0

Un LIS invia ORU con ACK applicativo.

**Esito:** framing/charset/profile; scope da endpoint; raw+ledger durabili; solo allora AA/CA previsto; crash test; duplicate MSH-10; risultato critico preservato.

### SDK-UC-02 — ACK perso

HHC persiste ADT e invia ACK, ma la connessione cade.

**Esito:** sorgente ritenta; idempotency/hash riconoscono il duplicato; response coerente; nessun doppio side effect; audit di entrambi gli attempt.

### SDK-UC-03 — FHIR client con token rotation

Un connector egress usa SMART Backend Services verso un EHR.

**Esito:** discovery/capability pin; private key JWT; token cache scoped; rotation overlap; 401 non causa retry storm; OperationOutcome normalizzato.

### SDK-UC-04 — DICOM studio multi-gigabyte

Una modalità invia studio multi-frame a VNA.

**Esito:** streaming/checksum; negotiation; temp quota; storage commitment; interruzione; UID collision; nessun heap buffer completo; audit senza SOP UID in metriche.

### SDK-UC-05 — XDS cross-community parziale

Un gateway interroga comunità multiple e una non risponde.

**Esito:** deadline/circuit per community; partial response esplicita secondo profilo; nessun successo completo falso; trace e audit ATNA/IHE correlati.

### SDK-UC-06 — SFTP file batch

Un laboratorio deposita file mentre è ancora in scrittura.

**Esito:** atomic rename/stability rule; checksum/manifest; duplicate filename diverso hash quarantinato; archive/receipt; path traversal impedito.

### SDK-UC-07 — JDBC legacy extraction

Una fonte legacy espone viste read-only incrementali.

**Esito:** template/query allowlist; watermark consistente; transaction isolation; fetch streaming; credential scope; schema drift; nessuna scrittura al sorgente.

### SDK-UC-08 — Broker consumer rebalance

Un nodo cade durante consumo eventi.

**Esito:** offset dopo durabilità; redelivery; ordering per partition; dedup; rebalance bounded; nessuna perdita; backlog metric.

### SDK-UC-09 — Partner lento multifacility

Un endpoint regionale rallenta mentre altri tenant sono normali.

**Esito:** bulkhead/circuit/queue per destination; live priority; nessun thread/pool globale esaurito; catch-up controllato.

### SDK-UC-10 — Connector terzo compromesso

Il connector tenta filesystem/network non dichiarati.

**Esito:** deny/sandbox alert; instance quarantinata; artifact/publisher revocato; altre cell operative; forensic evidence e replacement.

### SDK-UC-11 — Upgrade HAPI/dcm4che

Una libreria aggiorna parser/dizionario e cambia comportamento.

**Esito:** BOM pin; golden/fuzz/conformance diff; DICOM/HL7 version coverage verificata; canary; no claim basato sul solo numero versione; rollback.

### SDK-UC-12 — DR di una Runtime Cell

Il sito primario cade dopo ACK locali non ancora replicati.

**Esito:** RPO cross-site misurato; recovery dello spool; partner fencing; duplicate-safe reconnect; ledger reconciliation; escalation per eventuale gap entro policy.

## 25. Anti-pattern vietati

- API interne del runtime usate dal connector;
- connector che decide tenant da payload;
- ACK prima della callback durabile;
- retry automatico di `UNKNOWN` non idempotente;
- buffer/queue in heap senza limite;
- secret literal o trust-all TLS;
- log dell'intero payload;
- metric label con ID clinico;
- thread/pool globale condiviso indiscriminatamente;
- codice scaricato a runtime;
- plug-in terzo trusted in-process per default;
- alias dependency `latest`;
- exactly-once dichiarato dal solo connector;
- health check distruttivo o che causa dato clinico.

## 26. Fonti ufficiali e data di verifica

Fonti verificate il **1 settembre 2026**:

- Apache Camel releases; 4.22.0 latest LTS del 11 agosto 2026: <https://camel.apache.org/releases/>
- Apache Camel download/support matrix: <https://camel.apache.org/download/>
- HAPI HL7v2 repository e release: <https://github.com/hapifhir/hapi-hl7v2>, <https://github.com/hapifhir/hapi-hl7v2/releases>
- HAPI FHIR repository; release 8.10.0 rilevata il 21 maggio 2026: <https://github.com/hapifhir/hapi-fhir>
- Open eHealth IPF repository: <https://github.com/oehf/ipf>
- dcm4che repository e release; 5.34.3 rilevata il 23 aprile 2026 con dizionario DICOM 2026a: <https://github.com/dcm4che/dcm4che>, <https://github.com/dcm4che/dcm4che/releases>
- DICOM PS3.2 2026c: <https://dicom.nema.org/medical/dicom/current/output/html/part02.html>
- HL7 FHIR R5: <https://hl7.org/fhir/R5/>
- AsyncAPI 3.1.0: <https://www.asyncapi.com/docs/reference/specification/latest>
- JSON Schema 2020-12: <https://json-schema.org/draft/2020-12>
- SLSA 1.2: <https://slsa.dev/spec/v1.2/>
- OpenTelemetry 1.60.0: <https://opentelemetry.io/docs/specs/otel/>

Il fatto che dcm4che 5.34.3 dichiari un dizionario 2026a mentre lo standard DICOM corrente consultato è 2026c dimostra perché la copertura si qualifica con test e Conformance Statement, non si presume dalla libreria. Tutte le dipendenze sono fissate nel BOM di release con digest e support matrix.
