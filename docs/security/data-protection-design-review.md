# Data Protection Design Review — Fase 0 e vertical slice R0.2

Stato: WP1-00 approvato con azioni vincolanti; DPIA di installazione ancora obbligatoria
Ultimo aggiornamento: 10 settembre 2026
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

## Mini-DPIA di design — vertical slice A

**Percorso:** MLLP ADT/ORU sintetico → raw store → parse/validate → mapping tecnico → MLLP/REST → receipt/ledger. In una futura installazione reale i dati possono comprendere identificativi anagrafici, encounter, ordine, risultato, facility, professionista e timestamp; sono categorie particolari ai sensi del GDPR.

**Necessità/minimizzazione:** R0.2 usa soltanto fixture sintetiche. Nel prodotto, il raw conserva esclusivamente i byte necessari a prova, recovery e replay con retention per classe; envelope, indice, DLQ e UI espongono riferimenti opachi e metadata minimi. Patient/order identifiers non diventano object key, metric label o log field.

**Accessi:** workload identity per endpoint e scope; operatore vede metadata di stato per default. Apertura raw richiede ruolo distinto, purpose/reason, strong authentication, time-bound authorization e audit. Replay richiede un'autorizzazione separata dalla lettura.

**Rischi specifici:** routing cross-facility, PHI in error message/ACK, DLQ usata come archivio, replay non autorizzato, retention non propagata, support evidence non redatta. Controlli e test sono collegati a P1-R06, HZ-01/03/04/07, G2/G3/M1/M3/M4.

## Mini-DPIA di design — vertical slice B

**Percorso:** ORU sintetico → raw/canonical Observation → FHIR Observation → proiezione OMOP dimostrativa asincrona. FHIR e OMOP sono derivati con finalità e retention distinte; OMOP non retroagisce sul percorso assistenziale.

**Separazione delle finalità:** la cura e il secondary use non condividono automaticamente identity mapping, key o authorization. La demo OMOP usa pseudonimi sintetici, database e credential separati. In una futura elaborazione reale serve DataUseGrant/permit, base giuridica, DPIA, data minimization, governance HDAB ove applicabile e verifica di re-identification risk.

**Rischi specifici:** linkage eccessivo, codice locale che identifica una struttura/paziente raro, small cells, snapshot terminologico fuori finalità, cancellazione/restrizione non propagata, export agentico. Unknown code resta unknown/quarantine; nessun agente o modello riceve payload reale senza una successiva decisione e valutazione fornitore.

## Data inventory e retention decision per R0.2

| Asset | Contenuto consentito R0.2 | Scope/chiave | Accesso | Retention R0.2 | Divieti |
|---|---|---|---|---|---|
| raw object | messaggio sintetico originale | tenant/facility + opaque event ID, encryption scope | runtime writer; raw-reader JIT | 30 giorni o fine campagna, il più breve | patient/business ID in key, public bucket, update in-place |
| ingest/delivery ledger | ID opachi, digest, stato, timing, reason taxonomy | tenant/facility RLS/application enforcement | runtime e operator scoped | 90 giorni | payload, token, free-text clinico |
| canonical/FHIR evidence | fixture e output sintetico | test/tenant/facility/build | QE/clinical reviewer | release lifetime | uso assistenziale, export non governato |
| OMOP demo | sintetico/pseudonimo non reversibile | dataset build/purpose | analytics role separato | 30 giorni | write-back, direct identifier, condivisione automatica |
| audit | actor/workload ID, action, scope, reason, outcome, digest | append-only separate admin | auditor/security | almeno release lifetime; policy installazione per produzione | payload/secret, modifica in-place |
| telemetry | metriche e trace allowlist | bucket interni a cardinalità bounded | SRE/security | 14 giorni R0.2 | patient/order/accession ID, raw payload |

La cancellazione R0.2 è verificata da lifecycle report e tombstone audit. Backup scadono con finestra documentata; legal hold è non applicabile ai dati sintetici salvo incident evidence, ma il meccanismo è progettato senza cancellazione mutabile della storia audit.

## Privacy acceptance per WP1

- test cross-tenant/facility negano prima dell'accesso ai byte;
- canary identifier sintetici sono cercati in log, trace, metriche, report, issue e artifact CI;
- errori MLLP/HTTP/FHIR non riflettono payload o segreti;
- raw read, export, replay e break-glass producono audit completo;
- lineage consente individuazione di raw, canonical, FHIR e OMOP derivati per subject token senza query cross-purpose;
- outage della telemetria non sposta payload in log locali non governati;
- generatori agentici e strumenti SaaS ricevono solo fixture sintetiche approvate.

Un solo leakage di canary, accesso cross-scope o export privo di purpose fallisce M4/G8. La produzione richiede DPIA e RoPA specifiche del titolare, non può ereditare il verdetto sintetico R0.2.

## Gate successivi

Prima di un pilot con dati reali sono obbligatori DPIA firmata, RoPA, DPA, retention approvata, transfer impact assessment se applicabile, verifica diritti, penetration test indipendente e restore drill. Ogni nuovo connector completa una mini-DPIA e classifica campi, log ed errori.

## Fonti verificate

- [Regolamento (UE) 2016/679 — GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj), testo ufficiale riconfermato il 10 settembre 2026.
- [EDPB Guidelines 4/2019 on Data Protection by Design and by Default](https://www.edpb.europa.eu/our-work-tools/our-documents/guidelines/guidelines-42019-article-25-data-protection-design-and_en), riconfermate il 10 settembre 2026.
- [Regolamento (UE) 2025/327 — European Health Data Space](https://eur-lex.europa.eu/eli/reg/2025/327/oj), riconfermato il 10 settembre 2026; l’applicabilità è progressiva e va verificata per ogni release e Stato membro.
