# ADR-005 — FHIR non è il modello universale interno

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Interoperability e Domain Architecture

## Contesto

FHIR è centrale nell'interoperabilità moderna, ma forzare ogni MLLP, DICOM, file o workflow proprietario attraverso risorse FHIR introdurrebbe costo, perdita informativa e dipendenza da profili non applicabili.

## Decisione

FHIR è un protocollo/modello supportato e una possibile rappresentazione normalizzata, non il modello universale obbligatorio. Il fast path può trasformare direttamente source→target quando il canonical semantic layer non aggiunge valore. Il Canonical Semantic Event rappresenta fatti semantici selezionati e provenance, senza replicare l'intero FHIR Resource model.

FHIR package, versione e Implementation Guide sono pinnati per interfaccia. Mapping FHIR↔CSE è esplicito, versionato e loss-aware; elementi non rappresentati restano disponibili nel raw/parsed artifact.

## Conseguenze

- minore latenza e accoppiamento per integrazioni legacy;
- supporto FHIR richiede comunque validation e conformance dedicate;
- nessun claim “FHIR-native” generale senza capability provate;
- mapping non può inventare equivalenze tra modelli.

## Alternative respinte

- tutto convertito in FHIR: costo e perdita semantica non giustificati;
- nessun livello canonico: riuso semantico e analytics frammentati;
- FHIR solo come formato JSON: ignora profili, terminology e workflow.

## Collegamenti

- [FHIR](../standards/fhir.md)
- [Canonical Semantic Event](../canonical-model/canonical-semantic-event.md)
- [Data flow](../architecture/data-flow.md)
