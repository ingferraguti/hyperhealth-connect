# ADR-026 — Transazione di ingest, durability point e ACK

Stato: Accepted
Data: 10 settembre 2026
Owner: Runtime Architecture e Data Architecture
Reviewer: SRE, Product Security, Clinical Safety Officer
Decision class: A3 — safety/reliability critical

## Contesto

Raw object e processing ledger risiedono in sistemi distinti; non esiste una transazione ACID distribuita affidabile e portabile fra PostgreSQL e un object store S3-compatible. Un ACK positivo emesso nel punto sbagliato può rendere irrecuperabile un evento clinico. Tentare un exactly-once generico introdurrebbe false garanzie.

## Decisione

Il durability point di `ACK_ON_DURABLE` è raggiunto soltanto quando sono vere e verificabili entrambe le condizioni:

1. i byte originali sono stati scritti come oggetto raw con chiave deterministica, checksum SHA-256 e conditional create; una lettura HEAD/metadata conferma chiave, lunghezza e checksum;
2. PostgreSQL ha committato l'`ingest_record` nello stato `DURABLE`, con raw reference, checksum, tenant/facility scope, envelope ID, idempotency key, received time e policy version.

L'ordine è raw-first, ledger-second. Dopo il commit del ledger, l'adapter può emettere l'ACK positivo. Se il processo cade dopo la scrittura raw ma prima del commit ledger, un reconciler trova l'oggetto orfano tramite prefix/journal di staging e crea o completa il record soltanto dopo averne verificato scope e checksum. Se cade dopo il commit ma prima dell'ACK, il mittente può ritentare: l'idempotency contract restituisce lo stesso outcome senza creare un secondo raw logico.

L'oggetto usa una chiave opaca derivata da scope interno, data di ingest ed envelope ID; non contiene patient identifier o business identifier. L'upload incompleto non è pubblicato come durable: multipart upload scaduti sono abortiti dal lifecycle e non sono referenziati dal ledger.

## State machine

`RECEIVED -> RAW_STORED -> DURABLE -> PROCESSING -> terminal/pending state`

- `RECEIVED` e `RAW_STORED` sono stati interni non positivamente acknowledged.
- `DURABLE` è monotono e richiede il commit delle due prove sopra.
- Stati ambigui producono NACK/timeout secondo protocollo, mai ACK positivo inventato.
- La cancellazione retention crea tombstone governato; non modifica la storia del ledger.

Le policy ACK hanno significato distinto:

- `ACK_ON_RECEIVE`: consentita solo per protocolli/use case che accettano esplicitamente perdita prima della durabilità; vietata nei due vertical slice R0.2 e nei flow Tier 0.
- `ACK_ON_DURABLE`: default R0.2; segue il durability point di questo ADR.
- `ACK_ON_DELIVERY`: emessa solo dopo receipt applicativa downstream interpretata dal connector e registrata nel delivery ledger.

## Invarianti transazionali

- La coppia `(tenant_id, facility_id, flow_id, idempotency_key)` è unica nella finestra configurata.
- Un raw digest non sostituisce la scope key: payload identici da facility diverse restano eventi distinti.
- Nessun updater può cambiare raw reference, checksum o scope dopo `DURABLE`.
- Il reconciler è idempotente, scoped, rate-limited, auditato e non emette ACK retroattivi.
- La conferma del client non dipende da mapping, FHIR, OMOP, telemetry backend o Control Plane.
- Backup e restore devono preservare una recovery frontier comune fra ledger e object store; gli scostamenti sono riconciliati prima della riapertura.

## Failure matrix minima

| Crash/fault point | Risposta protocollo | Stato recuperabile | Azione |
|---|---|---|---|
| prima del raw write | nessun ACK positivo | nessun evento durable | retry client |
| durante upload | timeout/NACK | multipart non referenziato | abort lifecycle e retry |
| dopo raw, prima del ledger | nessun ACK positivo | raw orfano | reconciler verifica e completa/espira |
| durante commit ledger | timeout/NACK | commit determinato interrogando la key | retry idempotente |
| dopo commit, prima dell'ACK | ACK perso | evento `DURABLE` | retry restituisce outcome precedente |
| dopo ACK, prima del processing | ACK già valido | evento `DURABLE` | worker riprende dal ledger |
| checksum mismatch | NACK/quarantine | raw non promosso | security/data integrity event |
| object store non disponibile | timeout/NACK | nessun ACK positivo | backpressure e circuit breaker |

## Alternative respinte

- Commit ledger prima del raw: può lasciare un record durable che punta a byte inesistenti.
- Best-effort dual write con ACK dopo una sola scrittura: viola RPO locale 0.
- XA/two-phase commit fra DB e S3: non portabile e aumenta i failure mode.
- Payload raw nel database: crea coupling, costi e blast radius non accettabili.
- ACK dopo la sola ricezione socket: falsa accettazione.

## Conseguenze

Esisteranno temporaneamente raw orfani e ACK persi, ma entrambi sono rilevabili e riconciliabili. La latenza include object write più commit DB; il performance test deve misurare questa realtà. L'implementazione deve fornire crash hook deterministici e un invariant checker indipendente.

## Gate ed evidenza

G3 è bloccato finché la failure matrix non è verde su almeno tre run per punto, con conteggi `accepted = durable` per ACK emessi, zero mismatch e restore verificato. Ogni modifica dell'ordine, del checksum, della state machine o del meccanismo ACK riapre ADR e hazard HZ-03/HZ-04.

## Collegamenti

- [ADR-004](./ADR-004-immutable-raw-events.md)
- [ADR-006](./ADR-006-sync-async-dual-path.md)
- [ADR-017](./ADR-017-replay-and-reprocessing.md)
- [Risk register](../roadmap/risk-register.md)
