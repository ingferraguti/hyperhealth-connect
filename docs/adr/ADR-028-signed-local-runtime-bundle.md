# ADR-028 — Bundle locale firmato e autonomia della Runtime Cell

Stato: Accepted
Data: 10 settembre 2026
Owner: Runtime Architecture e Release Engineering
Reviewer: Product Security, SRE
Decision class: A3 — configuration integrity and business continuity

## Contesto

Il Data Plane deve continuare i flow già autorizzati quando Control Plane, registry o WAN non sono disponibili. Una semplice cache mutabile non offre provenienza, rollback sicuro o protezione da configurazioni parziali/corrotte. Il prodotto finale richiede 24 ore di autonomia; R0.2 deve provarne almeno quattro senza introdurre un formato usa-e-getta.

## Decisione

Ogni deployment produce un Runtime Bundle immutabile, content-addressed e firmato. Il bundle contiene solo artifact e riferimenti necessari all'esecuzione:

- manifest con bundle ID, schema version, tenant/facility/runtime-cell scope e timestamps;
- flow normalizzato, connector manifest/config non segreta e capability requirements;
- mapping tecnico/semantico per digest, schema e terminology snapshot reference;
- policy ACK/retry/ordering/retention references;
- compatibility range di runtime, SDK e connector;
- secret reference, mai secret value;
- artifact digest, SBOM/provenance reference, previous-good bundle e rollback metadata.

Il Control Plane firma il manifest canonico; la Runtime Cell verifica trust chain, signature, digest di ogni file, scope, compatibility, activation window e revocation state prima dello staging. La promozione è atomica tramite puntatore locale `active`; il precedente bundle valido resta read-only. Un bundle parziale, sconosciuto, scaduto oltre policy, fuori scope o incompatibile non diventa attivo.

In assenza Control Plane:

- i flow attivi continuano per almeno quattro ore in R0.2 e sono progettati per il target enterprise di 24 ore;
- nessuna nuova configurazione, elevazione privilege o secret raw viene accettata;
- retry, ledger, audit e delivery proseguono localmente entro capacità;
- revocation freshness e secret expiry possono imporre safe-stop selettivo secondo rischio;
- lo stato operativo è bufferizzato e riconciliato al ritorno del Control Plane.

## Formato e lifecycle

Il formato R0.2 è un archivio deterministico con `manifest.json` canonicalizzato, file ordinati e digest SHA-256. Il `bundle_id` è il digest del manifest senza signature. Signature e provenance usano profilo definito dalla release pipeline; chiavi private non risiedono nella Runtime Cell. Stati: `downloaded`, `verified`, `staged`, `active`, `previous-good`, `revoked`, `garbage-collectable`.

La garbage collection mantiene active, previous-good e la finestra necessaria a audit/recovery. La retention non elimina un artifact coinvolto in legal hold, incident o release evidence. Il rollback è una nuova azione auditata che ripromuove un bundle già verificato; non modifica il bundle.

## Invarianti

- Scope del bundle deve essere uguale allo scope della Runtime Cell; mismatch è deny.
- Il worker non interroga il Control Plane nel fast path del payload.
- La perdita della cache non autorizza download non verificato o fallback a default insicuri.
- Un bundle non può incorporare PHI, credential o token.
- Clock skew oltre soglia produce alert e impedisce decisioni di expiry ambigue.
- Ogni activation/rollback/rejection conserva actor/workload identity, reason, digests e outcome.

## Alternative respinte

- Configurazione live richiesta per ogni messaggio: viola autonomia e crea single point of failure.
- Directory mutabile sincronizzata: non offre atomicità o provenance.
- Bundle per singolo vendor/orchestratore: impedisce installazioni ibride e on-premise.
- Continuare indefinitamente con configurazione revocabile: rischio sicurezza non bounded.

## Evidenze richieste

G7 richiede test di bundle corrotto, signature errata, scope mismatch, version mismatch, attivazione interrotta, rollback e quattro ore senza Control Plane. La qualification enterprise estende a 24 ore, secret/key rotation, storage pressure, revocation e recovery di sito.

## Collegamenti

- [ADR-001](./ADR-001-control-plane-data-plane.md)
- [ADR-007](./ADR-007-configuration-as-code.md)
- [ADR-010](./ADR-010-versioning-and-compatibility.md)
- [Container e deployment boundaries](../architecture/containers.md)
