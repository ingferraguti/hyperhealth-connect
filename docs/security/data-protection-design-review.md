# Data Protection Design Review — Fase 0

Stato: approvato con azioni vincolanti per il Technical MVP  
Ultimo aggiornamento: 7 settembre 2026  
Owner: Data Protection Officer

## Decisione

La fondazione è idonea a procedere perché separa identità, contenuto clinico, metadati operativi e telemetria; vieta dati reali negli ambienti non produttivi; rende tenant e facility obbligatori; adotta minimizzazione e logging per allowlist. Questa review non sostituisce la DPIA del trattamento concreto: ogni installazione deve documentare titolare/responsabile, basi giuridiche, finalità, categorie di interessati, trasferimenti, conservazione e diritti.

## Regole di progetto

- Privacy by default: payload e identificativi non entrano in log, metriche, trace, ticket, prompt o artifact CI.
- Separazione: la chiave di pseudonimizzazione è esterna al data plane e distinta per dominio/tenant; il linking cross-tenant è vietato salvo base giuridica e policy esplicita.
- Residenza: placement di storage, backup, replica e osservabilità è configurabile per regione; nessun failover extra-SEE implicito.
- Conservazione: raw event, canonical event, audit e OMOP hanno schedule diverse, legal hold esplicito e cancellazione verificabile.
- Accesso: workload identity, least privilege, purpose binding per operazioni sensibili, break-glass a durata breve e doppia revisione post-evento.
- Trasparenza e diritti: lineage consente ricerca, rettifica logica, export e prova delle trasformazioni senza riscrivere l’audit immutabile.
- Automazione agentica: dati reali non sono inviati a modelli o servizi non approvati; output generati non approvano mapping o modifiche clinicamente rilevanti.

## Flussi valutati

| Flusso | Dati | Finalità | Controlli | Rischio residuo |
|---|---|---|---|---|
| HL7/FHIR/DICOM → raw store | sanitari e identificativi | interoperabilità/continuità di cura | cifratura, immutabilità, scope, retention | medio, da validare per installazione |
| raw → canonical | dati clinici normalizzati | trasformazione governata | lineage, versioni, quarantena | medio |
| canonical → OMOP | pseudonimi e dati clinici | ricerca/analytics autorizzati | separazione chiavi, accesso purpose-bound | medio |
| data plane → telemetry | soli metadati allowlist | affidabilità e sicurezza | redazione e budget cardinalità | basso |
| CI/test | sintetici | verifica software | marker sintetico e scanner | basso |

## Gate successivi

Prima di un pilot con dati reali sono obbligatori DPIA firmata, RoPA, DPA, retention approvata, transfer impact assessment se applicabile, verifica diritti, penetration test indipendente e restore drill. Ogni nuovo connector completa una mini-DPIA e classifica campi, log ed errori.

## Fonti verificate

- [Regolamento (UE) 2016/679 — GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj), testo ufficiale consultato il 7 settembre 2026.
- [EDPB Guidelines 4/2019 on Data Protection by Design and by Default](https://www.edpb.europa.eu/our-work-tools/our-documents/guidelines/guidelines-42019-article-25-data-protection-design-and_en), consultate il 7 settembre 2026.
- [Regolamento (UE) 2025/327 — European Health Data Space](https://eur-lex.europa.eu/eli/reg/2025/327/oj), consultato il 7 settembre 2026; l’applicabilità è progressiva e va verificata per ogni release e Stato membro.

