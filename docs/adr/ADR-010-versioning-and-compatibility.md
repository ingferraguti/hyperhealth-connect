# ADR-010 — Versionamento e compatibilità

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Architecture Governance

## Contesto

Release miste sono inevitabili in installazioni multi-site. Versionare solo il prodotto non permette di ricostruire quale contratto, mapping o vocabolario abbia prodotto un risultato.

## Decisione

Ogni asset pubblicato ha logical ID, versione immutabile, checksum, lifecycle e compatibility metadata. SemVer è usato per SDK/API dove appropriato; schema/eventi adottano regole backward/forward dichiarate; mapping, flow, terminology snapshot e dataset sono identificati da versioni immutabili anche senza SemVer.

Runtime e Connector SDK supportano almeno N/N-1 nella finestra dichiarata. Upgrade DB usa expand/migrate/contract; breaking change richiede nuova major, dual-read/dual-write o adapter temporaneo e piano di rimozione. Nessun consumer assume “latest”.

## Conseguenze

- ogni envelope/dataset registra versioni effettive;
- compatibility matrix è gate di release;
- downgrade non è promesso se una migration distruttiva è già conclusa;
- deprecation ha data, owner, telemetry d'uso e migration guide;
- artifact revocato resta referenziabile per audit ma non distribuibile.

## Alternative respinte

- versionare solo container: lineage insufficiente;
- compatibilità indefinita/best effort: rischio operativo;
- aggiornamento simultaneo fleet-wide: non realistico e alto blast radius.

## Collegamenti

- [Configuration as Code](../development/configuration-as-code.md)
- [Release plan](../roadmap/release-plan.md)
- [Connector SDK](../connectors/connector-sdk.md)
