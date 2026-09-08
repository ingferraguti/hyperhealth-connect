# ADR-004 — Eventi raw immutabili

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Data Architecture e Security

## Contesto

Parsing, mapping e standard cambiano. Senza il payload ricevuto non è possibile spiegare una trasformazione, riparare un difetto o riconciliare un evento. Conservare raw aumenta però rischio privacy e costo.

## Decisione

Ogni evento accettato ha un raw object immutabile, cifrato e content-addressed o associato a checksum forte. L'Integration Envelope conserva riferimento, tenant/facility, message ID, hash, classificazione e retention policy, non una copia del payload. Correzioni e derivati creano nuovi oggetti e lineage; nessun update in-place.

La persistenza raw è nel Data Plane/residency boundary autorizzato. Object lock/WORM equivalente è applicato dove richiesto; accesso è separato dall'accesso ai metadata. La cancellazione avviene tramite lifecycle governato e lascia tombstone/audit non contenente PHI.

## Conseguenze

- replay deterministico e forensic analysis diventano possibili;
- duplicazione viene controllata con riferimenti e tiering;
- chiavi, retention e legal hold sono parte del design;
- checksum e restore sono testati continuamente;
- raw non è un data lake liberamente interrogabile.

## Alternative respinte

- conservare solo canonical: perdita di evidenza e impossibilità di reinterpretazione;
- payload nei log: sicurezza, struttura e retention inadeguate;
- modifica del raw dopo correzione: compromette chain of custody.

## Collegamenti

- [Integration Envelope](../canonical-model/integration-envelope.md)
- [Data architecture](../architecture/data-architecture.md)
- [Retention raw](./ADR-023-raw-event-retention.md)
