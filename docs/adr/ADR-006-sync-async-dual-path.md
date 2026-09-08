# ADR-006 — Percorso sincrono e asincrono

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Runtime Architecture e SRE

## Contesto

Alcuni workflow richiedono risposta protocollo immediata; altri richiedono durabilità, fan-out, recupero e analytics. Un unico percorso imporrebbe latenza del broker a tutti o rinuncerebbe a resilienza.

## Decisione

HHC offre due execution mode dichiarati nel flow. Il fast path sincrono usa time budget end-to-end, cancellation e dipendenze minime. L'event path persiste prima dell'accettazione prevista, disaccoppia elaborazione/delivery e supporta retry, DLQ e replay.

Entrambi producono Integration Envelope, audit, correlation e failure outcome. L'ACK positivo è legato al durability boundary del contratto, non alla sola ricezione socket. Analytics/OMOP non partecipano mai al percorso di ACK clinico.

## Conseguenze

- il designer dichiara completion semantics e timeout;
- passaggio sync↔async è breaking behavior e richiede qualification;
- esiti `UNKNOWN` vengono riconciliati, non ritentati ciecamente;
- code Tier 0, standard e batch hanno quote separate.

## Alternative respinte

- broker obbligatorio per tutto: overhead e failure dependency sul fast path;
- solo sincrono: impossibile assorbire outage e replay in sicurezza;
- ACK sempre immediato: falso successo e perdita potenziale.

## Collegamenti

- [Data flow](../architecture/data-flow.md)
- [Reliability model](../operations/reliability-and-error-model.md)
- [Performance e chaos](../testing/performance-and-chaos.md)
