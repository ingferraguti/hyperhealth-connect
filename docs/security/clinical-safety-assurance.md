# Clinical Safety Assurance — fondazione e Technical MVP R0.2

Stato: baseline WP1-00 approvata; evidenze cliniche R0.2 ancora da produrre
Ultimo aggiornamento: 10 settembre 2026
Owner: Clinical Safety Officer

## Safety case preliminare

HHC non prende decisioni cliniche autonome: trasporta, valida, trasforma e rende tracciabili informazioni. Può tuttavia causare danno tramite omissione, ritardo, duplicazione, attribuzione errata o alterazione semantica. Per questo la sicurezza clinica è un attributo del ciclo di sviluppo e della release, non un controllo finale.

| Hazard | Evento pericoloso | Severità | Controlli minimi | Evidenza di chiusura |
|---|---|---:|---|---|
| HZ-01 | risultato attribuito a paziente/tenant errato | catastrofica | scope immutabile, identity matching, quarantine | test isolamento, golden corpus, review clinica |
| HZ-02 | unità/codice trasformati senza equivalenza | critica | mapping versionato, UCUM, terminology binding, four-eyes | semantic diff e casi boundary |
| HZ-03 | messaggio urgente perso o ritardato | critica | durable queue, ACK state machine, SLO per priorità, alert | fault injection e backlog recovery |
| HZ-04 | replay produce doppia azione | critica | idempotency key, dedup window, delivery ledger | test replay concorrente |
| HZ-05 | correzione/cancellazione non propagata | critica | lifecycle event, linkage a predecessore, consumer contract | end-to-end amended/entered-in-error |
| HZ-06 | fallback usa configurazione scaduta o non valida | alta | firma, TTL, safe stop, audit | chaos control-plane e signature tests |
| HZ-07 | quarantena non viene presa in carico | alta | queue ownership, ageing SLO, escalation | operational simulation |
| HZ-08 | algoritmo/agent propone modifica clinica non revisionata | critica | no autonomous approval, provenance, human gate | policy test e audit review |

Ogni hazard ha owner clinico e tecnico. Le severity critiche impediscono release finché non esiste evidenza positiva e test negativo. Le metriche operative distinguono “messaggio ricevuto”, “persistito”, “trasformato”, “consegnato” e “riconosciuto”; non si dichiara successo al semplice HTTP 2xx se il contratto richiede conferma applicativa.

## Hazard review WP1-00 per i due vertical slice

| Hazard | Scenario R0.2 | Causa credibile | Controlli di design | Test/Oracle | Gate |
|---|---|---|---|---|---|
| HZ-01 | ADT/ORU viene attribuito al tenant, facility o patient stream errato | scope mancante, cache key incompleta, source endpoint mal configurato | scope immutabile in envelope/key/ledger; deny prima payload; bundle scoped | matrice cross-tenant/cross-facility, hot-scope e bundle mismatch; zero evento fuori scope | G2, G7, M1 |
| HZ-02 | unità, status, abnormal flag o codice lab cambiano significato | mapping ambiguo, default silenzioso, charset/profile errato | original value preserved; mapping version/digest; UCUM/code binding; unknown→quarantine | golden corpus include unit conversion boundary, unknown code, locale decimal, corrected result; zero trasformazione non spiegata | G6, M2 |
| HZ-03 | risultato accettato è perso o non recuperabile | ACK prima del durability point, dual-write parziale, restore incoerente | raw-first/ledger-second ADR-026; checksum; reconciliation; RPO locale 0 | crash in ogni punto prima/dopo ACK, restore e million-event reconciliation; zero ACK senza raw+ledger | G3, M5, G8 |
| HZ-04 | retry/replay duplica notifica o risultato | ACK perso, key non scoped, outcome downstream ambiguo | ADR-027, delivery token, duplicate window, replay autorizzato | retry concorrente, lost ACK, restart, unknown outcome; zero side effect non spiegato | G4, M1 |
| HZ-05 | correzione/cancellazione ORU non sostituisce logicamente il risultato | semantic revision trattata come duplicato o linkage perso | new event + causation/supersession; original raw retained | preliminary→final→corrected→entered-in-error golden sequence | M1, M2 |
| HZ-06 | CP outage usa flow corrotto, revocato o fuori scope | cache mutabile, signature/clock failure | signed content-addressed bundle ADR-028; previous-good; safe stop | corruption, expiry, clock skew, revocation and 4h isolation | G7 |
| HZ-07 | quarantine di messaggio critico non è vista | alert assente o telemetry backend down | ageing SLO, local audit buffer, out-of-band alert path, ownership | critical ORU quarantine while backend offline; alert/loss accounting and recovery | M4, G8 |
| HZ-08 | agente modifica mapping/oracle per rendere verdi i test | assenza segregazione o golden set indipendente | class A3, clinical reviewer, blind cases, immutable evidence | diff review; oracle provenance; independent rerun | M2, G8 |
| HZ-09 | FHIR Observation è valido sintatticamente ma clinicamente incompleto | profilo/support claim eccessivo, missing interpretation/reference range | capability statement limitato; semantic invariants before FHIR projection | profile validation più clinical golden assertions; missing mandatory context fails | M2 |
| HZ-10 | proiezione OMOP influenza ACK o dato clinico sorgente | coupling sincrono o write-back | Tier 2 async boundary; no write-back; separate failure domain | OMOP DB down/slow under live ORU; zero ACK latency/integrity effect | M2, G8 |

### Safety acceptance rule

Per ogni messaggio del corpus l'oracle registra `source raw digest → parsed values → mapping version → canonical assertions → projection/receipt`. Un test verde basato solo su schema o HTTP status non chiude un hazard semantico. Qualunque evento cross-scope, ACK falso, perdita accettata, modifica clinica non spiegata o duplicazione con potenziale azione blocca immediatamente la qualification.

### Review independence e stato residuo

Il design review WP1-00 è completo; la validazione non lo è. In modalità solo maintainer, Clinical Safety Officer e clinical informatics reviewer indipendente devono essere nominati prima di G3/M2. Il maintainer o un agente può preparare mapping e test, ma non approvare il proprio oracle clinico. Il rischio residuo HZ-02/03/04/05 resta `MEDIUM — OPEN FOR EVIDENCE` fino ai gate indicati.

## Casi d’uso clinici 2026 coperti

- laboratorio ospedaliero: ORU HL7 v2 verso FHIR Observation/DiagnosticReport, incluse unità, flag anomali, correzioni e risultati critici;
- radiologia multi-facility: ordine, worklist, DICOM metadata e referto, senza spostare pixel data nel canonical event;
- Patient Summary transfrontaliero: documenti/profile europei gestiti tramite adapter e validazione della versione dichiarata;
- farmacia e prescrizione: eventi di prescrizione/dispensazione con stato, consenso e non-duplicazione;
- sanità pubblica: notifiche pseudonimizzate e dati secondari separati dal percorso assistenziale;
- OMOP: proiezione analitica riproducibile che non retroagisce sul dato clinico sorgente.

## Release authority

Il Clinical Safety Officer approva hazard log, golden corpus, mapping ad alto rischio e risultato della prova di downgrade/rollback. Product Owner o agente software non possono derogare. Gli incidenti con possibile danno clinico attivano preservazione evidenze, valutazione sanitaria, comunicazioni regolatorie applicabili e sospensione della trasformazione interessata.

Riferimenti riconfermati il 10 settembre 2026: [IEC 62304 overview](https://www.iso.org/standard/38421.html), [ISO 14971 overview](https://www.iso.org/standard/72704.html) e [MDCG guidance](https://health.ec.europa.eu/medical-devices-sector/new-regulations/guidance-mdcg-endorsed-documents-and-other-guidance_en). L’applicabilità come dispositivo medico dipende dall’intended purpose e deve essere determinata da Regulatory Affairs.
