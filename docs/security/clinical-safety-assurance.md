# Clinical Safety Assurance — fondazione

Stato: baseline approvata per Fase 0  
Ultimo aggiornamento: 7 settembre 2026  
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

## Casi d’uso clinici 2026 coperti

- laboratorio ospedaliero: ORU HL7 v2 verso FHIR Observation/DiagnosticReport, incluse unità, flag anomali, correzioni e risultati critici;
- radiologia multi-facility: ordine, worklist, DICOM metadata e referto, senza spostare pixel data nel canonical event;
- Patient Summary transfrontaliero: documenti/profile europei gestiti tramite adapter e validazione della versione dichiarata;
- farmacia e prescrizione: eventi di prescrizione/dispensazione con stato, consenso e non-duplicazione;
- sanità pubblica: notifiche pseudonimizzate e dati secondari separati dal percorso assistenziale;
- OMOP: proiezione analitica riproducibile che non retroagisce sul dato clinico sorgente.

## Release authority

Il Clinical Safety Officer approva hazard log, golden corpus, mapping ad alto rischio e risultato della prova di downgrade/rollback. Product Owner o agente software non possono derogare. Gli incidenti con possibile danno clinico attivano preservazione evidenze, valutazione sanitaria, comunicazioni regolatorie applicabili e sospensione della trasformazione interessata.

Riferimenti verificati il 7 settembre 2026: [IEC 62304 overview](https://www.iso.org/standard/38421.html), [ISO 14971 overview](https://www.iso.org/standard/72704.html) e [MDCG guidance](https://health.ec.europa.eu/medical-devices-sector/new-regulations/guidance-mdcg-endorsed-documents-and-other-guidance_en). L’applicabilità come dispositivo medico dipende dall’intended purpose e deve essere determinata da Regulatory Affairs.

