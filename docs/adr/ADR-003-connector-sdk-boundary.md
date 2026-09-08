# ADR-003 — Confine del Connector SDK

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Connector Platform

## Contesto

Protocolli sanitari e prodotti vendor evolvono con velocità diversa dal runtime. Dipendenze come HAPI, Camel, IPF e dcm4che non devono diventare API HHC né consentire a plug-in di aggirare tenancy e reliability.

## Decisione

Il Connector SDK espone porte HHC versionate per lifecycle, capability, configurazione tipizzata, ingress/egress, receipt, health, telemetry e failure classification. Un connector traduce protocollo e non decide tenant, mapping clinico, retention, replay o successo business.

Connector ufficiali qualificati possono operare in-process; terze parti o parser ad alto rischio usano processo isolato/sidecar o Runtime Cell dedicata. Manifest dichiara SDK/runtime range, permission, endpoint, secret reference, resource limit, SBOM, firma e publisher.

## Conseguenze

- nessuna classe di libreria upstream nei contratti pubblici;
- compatibility N/N-1 e contract test kit obbligatori;
- hot upgrade solo se lifecycle e stato lo permettono, altrimenti drain/restart;
- failure di un connector non deve propagarsi a tenant o flow non correlati;
- artifact non firmato o capability non dichiarata viene rifiutato.

## Alternative respinte

- script arbitrari nel runtime: superficie e audit insufficienti;
- fork del core per ogni vendor: manutenzione e upgrade ingestibili;
- plug-in sempre in-process: blast radius non accettabile.

## Collegamenti

- [Connector SDK](../connectors/connector-sdk.md)
- [Catalogo connector](../connectors/connector-catalog.md)
- [Conformance](../testing/conformance-and-interoperability.md)
