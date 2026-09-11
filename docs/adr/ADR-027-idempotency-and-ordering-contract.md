# ADR-027 — Contratto di idempotenza e ordering

Stato: Accepted
Data: 10 settembre 2026
Owner: Runtime Architecture
Reviewer: Integration Architecture, SRE, Clinical Informatics
Decision class: A3 — reliability and clinical correctness

## Contesto

HL7 MLLP, HTTP e i sistemi destinatari possono perdere ACK, ritentare o duplicare messaggi. Non tutti i partner forniscono un identificatore globale affidabile e non tutti gli effetti downstream sono idempotenti. L'ordinamento globale ridurrebbe la scalabilità e non rappresenta i reali confini clinici.

## Decisione

HHC garantisce at-least-once transport e idempotent processing entro uno scope dichiarato. L'idempotency key logica è:

`tenant_id | facility_id | flow_id | source_endpoint_id | message_identity | semantic_revision`

`message_identity` usa, in ordine di preferenza, un identificatore di protocollo validato (per HL7 v2: sending application/facility, message control ID e message profile/version), una client idempotency key firmata/autorizzata, oppure un fingerprint SHA-256 dei byte raw più metadati di framing stabili. `semantic_revision` distingue correzioni/cancellazioni legittime da duplicati; non viene inventata se il protocollo non la espone.

La key fisica è un HMAC-SHA-256 canonico con chiave per tenant e versione del key schema. I valori clinici originali non sono copiati in indice, log o metrica. Collisioni logiche o reuse ambiguo della key causano quarantine e non deduplica silenziosa.

La duplicate window è configurata per flow/partner con default R0.2 di 7 giorni e massimo pari alla retention del ledger. Oltre la finestra l'evento è trattato come nuova consegna ma conserva linkage a precedenti match rilevati. Per side effect downstream, il delivery token è stabile per `event_id + destination_id + delivery_generation`; il connector lo propaga quando il protocollo lo supporta.

L'ordering è garantito soltanto entro la `partition_key` dichiarata nel flow. Per ADT è normalmente patient/business-subject pseudonymous key entro facility; per ORU è order/specimen/observation stream secondo profilo. La chiave include sempre tenant e facility. Un evento senza partition key richiesta viene quarantinato; hot key e assenza di progresso producono backpressure scoped, non bypass dell'ordine.

## Outcome duplicati

- duplicato prima del processing: restituisce il receipt del primo evento e incrementa un contatore non-PHI;
- duplicato durante processing: attende/bounded-polls lo stato o risponde `in progress`, senza secondo worker concorrente;
- ACK perso dopo durabilità: restituisce lo stesso ACK applicativo coerente col ledger;
- correzione valida: crea nuovo event ID e causation link;
- stessa key con digest diverso: `IDEMPOTENCY_CONFLICT`, quarantine, audit security/clinical;
- replay delivery: riusa generation solo quando si verifica l'assenza di side effect; altrimenti crea generation auditata e richiede policy/human authorization.

## Invarianti

- Nessuna deduplica attraversa tenant, facility, flow o source endpoint.
- Il raw di ogni tentativo accettato è conservato o referenziato secondo policy; il duplicate outcome non cancella evidence.
- Cache di deduplica è un acceleratore, mai il system of record.
- Retry budget e ordering non bloccano tenant/facility non correlati.
- Exactly-once end-to-end è dichiarabile solo per uno specifico adapter e contratto provato, mai come proprietà generale HHC.

## Alternative respinte

- Hash del payload come unica key: confonde eventi identici legittimi e non modella correzioni.
- Message Control ID globale: molti mittenti lo riusano e manca lo scope.
- Deduplica in memoria: perde stato al restart e non scala.
- Ordinamento globale: crea single bottleneck e coupling cross-tenant.
- Retry cieco su esito `UNKNOWN`: può duplicare side effect clinici.

## Evidenze richieste

Contract e fault suite coprono duplicati simultanei, reuse key con payload diverso, ACK perso, restart, eviction cache, scadenza finestra, correzione, hot partition, reorder e replay. G4 richiede zero side effect non spiegato e reconciliation completa.

## Collegamenti

- [ADR-026](./ADR-026-durable-ingest-transaction-and-ack.md)
- [ADR-017](./ADR-017-replay-and-reprocessing.md)
- [Reliability model](../operations/reliability-and-error-model.md)
