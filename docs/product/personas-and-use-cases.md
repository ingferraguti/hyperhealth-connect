# Personas e casi d'uso

Stato: baseline di prodotto 1.0  
Data di riferimento: 1 settembre 2026  
Ambito: sanità europea, prodotto enterprise multi-azienda e multi-facility

## 1. Scopo

Il documento descrive attori e casi d'uso che il prodotto finale deve sostenere. I casi d'uso sono indipendenti dal vendor e coprono assistenza, interoperabilità, operations, governance, ricerca e continuità operativa. Non autorizzano decisioni cliniche automatiche e devono essere adattati alle leggi nazionali, alle basi giuridiche e ai ruoli privacy di ogni installazione.

## 2. Personas

| Persona | Responsabilità | Obiettivo in HHC | Informazioni sensibili consentite |
|---|---|---|---|
| Integration Engineer | Progetta e mantiene flow e connector | Integrare sistemi con configurazioni versionate e testabili | Payload di test; produzione solo con elevazione autorizzata |
| Clinical Informatician | Valida il significato clinico | Garantire che trasformazioni e profili preservino la semantica | Dati minimizzati e casi autorizzati |
| Data Steward | Governa mapping, terminologie e qualità | Approvare concetti, regole e release dei dataset | Dati pseudonimizzati; identificativi solo se necessari |
| Platform Administrator | Gestisce tenant, facility e runtime | Eseguire deploy, capacity, backup e upgrade | Nessun accesso implicito al payload clinico |
| Site Operator | Opera una facility o runtime cell | Sorvegliare code, endpoint e incident locali | Metadati tecnici; payload mediante break-glass |
| Security Officer / CISO | Governa rischio e incident response | Rilevare abuso, vulnerabilità e compromissioni | Audit e indicatori; accesso PHI minimizzato |
| DPO / Privacy Officer | Sorveglia protezione e diritti | Verificare base giuridica, minimizzazione, retention e accessi | Audit e dati necessari all'indagine |
| Auditor / Compliance Officer | Produce evidenze indipendenti | Ricostruire modifiche, accessi e operazioni | Read-only, scoped e tracciato |
| SRE / NOC Operator | Garantisce disponibilità e performance | Gestire SLO, alert, failover e recovery | Telemetria redatta; payload escluso per default |
| SOC Analyst | Monitora minacce e incident | Correlare eventi HHC con SIEM/SOAR | Security event e metadati minimizzati |
| Clinico | Usa dati nei sistemi assistenziali | Ricevere informazioni complete e tempestive | Secondo ruolo clinico nel sistema destinatario |
| Researcher | Conduce studi autorizzati | Ottenere coorti e dataset riproducibili | Solo ambiente e dataset autorizzati |
| Data Access Officer / HDAB Liaison | Gestisce richieste di uso secondario | Applicare permit, scopo, minimizzazione e scadenza | Dataset pseudonimizzati o anonimi autorizzati |
| Product Owner | Prioritizza capability e release | Bilanciare outcome clinici, rischio e sostenibilità | Dati aggregati, nessun accesso operativo implicito |
| Vendor / System Integrator | Fornisce connector e implementazioni | Estendere HHC tramite SDK e contratti stabili | Solo tenant e ambienti contrattualmente assegnati |

## 3. Regole comuni ai casi d'uso

Ogni caso d'uso deve:

- operare entro tenant, organization, facility e purpose autorizzati;
- generare correlation ID e audit degli eventi rilevanti;
- applicare minimizzazione, masking e retention configurata;
- dichiarare comportamento in caso di indisponibilità di sorgente, destinazione, Control Plane e dipendenze;
- evitare perdita silenziosa, duplicazione clinicamente ambigua o trasformazioni non versionate;
- distinguere dati operativi, raw, canonici e analitici;
- essere verificato con dati sintetici e test di failure;
- avere owner, SLO, runbook e procedura di riconciliazione.

## 4. Casi d'uso assistenziali e di interoperabilità

### UC-CLIN-001 — ADT enterprise multi-facility

**Scenario.** Un gruppo sanitario riceve eventi di ammissione, trasferimento e dimissione da più HIS e deve distribuirli a LIS, RIS, farmacia, cartella clinica, data platform e servizi regionali.

**Flusso principale.** Il worker locale riceve HL7 v2, valida struttura e identità del mittente, assegna l'envelope, preserva il raw, applica il mapping approvato e consegna alle destinazioni previste. I dati canonici alimentano lineage e, in asincrono, le proiezioni analitiche.

**Eccezioni.** Paziente ambiguo, evento fuori sequenza, duplicato, facility non riconosciuta, destinazione lenta e indisponibilità WAN producono quarantena, retry o store-and-forward secondo policy; non generano merge impliciti.

**Esito verificabile.** Nessun evento accettato è perso; ogni consegna è correlata; ordering è preservato per la chiave configurata; le facility vedono soltanto il proprio perimetro.

### UC-CLIN-002 — Risultati di laboratorio e valori critici

**Scenario.** LIS differenti pubblicano risultati verso EHR, portale, repository regionale e dataset OMOP.

**Flusso principale.** HHC interpreta ORU/FHIR Observation, valida unità, stato, identificativi e codici, applica mapping LOINC/locali, consegna il risultato operativo e accoda la proiezione `MEASUREMENT`.

**Eccezioni.** Correzioni e cancellazioni sono distinte dal nuovo risultato; valori critici non vengono ritardati dall'analytics; unità incompatibili o mapping mancanti sono segnalati senza inventare normalizzazioni.

**Esito verificabile.** La consegna clinica rispetta lo SLO del flow; risultato originale, trasformato e concept applicato restano ricostruibili.

### UC-CLIN-003 — Documenti clinici e scambio IHE/CDA

**Scenario.** Aziende e facility pubblicano e consultano lettere di dimissione, referti e patient summary tramite repository aziendali, regionali o nazionali.

**Flusso principale.** HHC orchestra metadata e transazioni IHE/CDA, valida profilo e firma dove richiesto, applica policy di routing e registra audit di pubblicazione/consultazione.

**Eccezioni.** Documenti sostituiti, ritirati, firmati in modo non valido o riferiti a identità discordanti vengono bloccati o quarantinati secondo il profilo, senza alterare il repository ufficiale.

**Esito verificabile.** Documento e metadata sono coerenti; l'accesso è autorizzato; esiste prova delle transazioni senza duplicare indiscriminatamente il contenuto nei log.

### UC-CLIN-004 — Imaging enterprise

**Scenario.** RIS/PACS e piattaforme regionali scambiano ordini, esiti e oggetti o metadata DICOM/DICOMweb.

**Flusso principale.** HHC orchestra query/retrieve, store o notifiche, protegge payload di grandi dimensioni, applica limiti di banda e correla accession number, study e paziente con il workflow clinico.

**Eccezioni.** Transfer syntax non supportata, AE Title errato, studio incompleto, rete degradata e destinazione satura attivano retry e back-pressure specifici.

**Esito verificabile.** HHC non diventa il PACS ufficiale; integrità, checksum, stato e destinazione di ogni oggetto gestito sono dimostrabili.

### UC-CLIN-005 — Prescrizione e dispensazione transfrontaliera

**Scenario.** Un'organizzazione prepara lo scambio di ePrescription/eDispensation tramite infrastrutture nazionali e MyHealth@EU.

**Flusso principale.** Adapter nazionali convertono i dati nei contratti applicabili, mantengono codici e lingua originale, applicano terminologie e consentono audit della traduzione e della consegna.

**Eccezioni.** Specifica nazionale non compatibile, identificazione insufficiente, farmaco non mappato o servizio transfrontaliero non disponibile impediscono conversioni speculative e attivano procedure di fallback definite dall'autorità.

**Esito verificabile.** Il prodotto si adatta agli atti di esecuzione EHDS senza cambiare il core; nessuna conformità MyHealth@EU è dichiarata senza test e accreditamento richiesti.

### UC-CLIN-006 — Emergency care e patient summary

**Scenario.** Un clinico in emergenza necessita di un patient summary proveniente da un'altra facility o Stato membro.

**Flusso principale.** HHC interroga i servizi autorizzati, conserva provenance, applica timeout stretti, presenta lo stato di completezza al sistema clinico e registra l'accesso.

**Eccezioni.** Patient summary parziale, indisponibile o con identità incerta è marcato chiaramente; HHC non lo presenta come completo e non prende decisioni di triage.

**Esito verificabile.** Accesso e fonte sono auditabili; il percorso critico degrada in modo esplicito e non blocca le procedure cliniche locali di emergenza.

### UC-CLIN-007 — Dispositivi e osservazioni ad alta frequenza

**Scenario.** Gateway di dispositivi o sistemi di monitoraggio inviano osservazioni verso EHR e data platform.

**Flusso principale.** Runtime dedicati applicano batching, rate limit e priorità, preservano timing e unità, separano allarmi clinici da telemetria analitica e impediscono che un consumer lento saturi l'acquisizione.

**Eccezioni.** Clock skew, burst, dati fuori ordine e device identity non valida sono registrati e gestiti secondo il profilo.

**Esito verificabile.** Il canale operativo critico resta isolato dalle elaborazioni secondarie e dagli analytics.

### UC-CLIN-008 — Sanità territoriale, farmacia e telemedicina

**Scenario.** Ospedali, ambulatori, farmacie, assistenza domiciliare e piattaforme di telemedicina condividono appuntamenti, referti, prescrizioni e misurazioni.

**Flusso principale.** HHC governa endpoint differenti per facility e provider, applica identità federata, consenso/restrizioni quando richiesto e instrada eventi verso il corretto episodio assistenziale.

**Eccezioni.** Connessioni intermittenti e dispositivi edge usano store-and-forward; dati generati dal paziente sono distinti da dati validati da un professionista.

**Esito verificabile.** Provenance e livello di validazione accompagnano il dato in ogni destinazione.

## 5. Casi d'uso europei di diritti e governance

### UC-EU-001 — Access log e trasparenza per l'interessato

**Scenario.** L'organizzazione deve mostrare chi ha consultato o trasformato dati sanitari e per quale finalità.

**Flusso principale.** HHC esporta eventi di accesso normalizzati verso il sistema competente, includendo attore, ruolo, purpose, categoria, timestamp, tenant e risultato, senza rivelare segreti tecnici.

**Esito verificabile.** Gli eventi sono completi, ordinabili, leggibili da macchina e protetti da modifica; accessi amministrativi e break-glass sono evidenziati.

### UC-EU-002 — Correzione, restrizione e opt-out

**Scenario.** Un interessato esercita diritti previsti da GDPR, EHDS o diritto nazionale.

**Flusso principale.** Il sistema autorevole emette una decisione o restrizione; HHC la propaga agli asset e ai flow interessati, conserva prova dell'applicazione e distingue correzione del dato da cancellazione del raw soggetto a obblighi legali.

**Eccezioni.** Conflitto fra obbligo di conservazione e richiesta, dataset già anonimizzato o uso di interesse pubblico è demandato al titolare/HDAB; HHC non decide la base giuridica.

**Esito verificabile.** Scope, data, autorità e applicazione della restrizione sono dimostrabili sui sistemi e dataset raggiungibili.

### UC-EU-003 — Preparazione dati per uso secondario EHDS

**Scenario.** Un health data holder deve rendere disponibili dataset per ricerca o sanità pubblica tramite processo autorizzato.

**Flusso principale.** HHC cataloga dataset e qualità, verifica permit/purpose ricevuto dal sistema di governance, minimizza, pseudonimizza o anonimizza e pubblica esclusivamente in un secure processing environment autorizzato.

**Eccezioni.** Permit scaduto, purpose non ammesso, dati non inclusi, opt-out applicabile o rischio di re-identificazione impediscono l'esportazione.

**Esito verificabile.** Ogni output è legato a permit, versione del dataset, regole applicate, destinatari e scadenza; nessun download è consentito se la policy impone elaborazione controllata.

### UC-EU-004 — Public health e sorveglianza

**Scenario.** Le aziende inviano flussi obbligatori o tempestivi ad autorità regionali, nazionali o europee.

**Flusso principale.** HHC applica contratti versionati, validazione, pseudonimizzazione/minimizzazione e priorità; mantiene ricevute e riconcilia quantità inviate e accettate.

**Eccezioni.** Regole nazionali divergenti sono isolate negli adapter; una modifica urgente del contratto segue percorso approvato e rollback.

**Esito verificabile.** Completezza e scarti sono misurabili per organization e facility.

## 6. Casi d'uso di dati, ricerca e AI

### UC-DATA-001 — Costruzione progressiva di OMOP multi-CDM

**Scenario.** Un gruppo costruisce istanze OMOP separate o federate per aziende e progetti.

**Flusso principale.** Eventi canonici sono proiettati con mapping e vocabulary snapshot dichiarati; la pipeline esegue validazione strutturale, DataQualityDashboard e Achilles prima dello stato `READY`.

**Eccezioni.** Mapping mancanti, score di qualità sotto soglia o incoerenze temporali mantengono il dataset in `FAILED` o `BUILDING`.

**Esito verificabile.** Ogni riga o aggregato è riconducibile a sorgente, flow, mapping, terminologia e dataset version.

### UC-DATA-002 — Studio multicentrico europeo

**Scenario.** Ricercatori autorizzati definiscono una coorte comune su più organizzazioni senza centralizzare dati identificativi.

**Flusso principale.** Una definizione versionata è validata, eseguita localmente sulle istanze OMOP compatibili e restituisce soltanto output permessi e controllati.

**Eccezioni.** Vocabolari non allineati, celle piccole, permit diversi e rischio di disclosure bloccano o riducono l'output.

**Esito verificabile.** Query, versioni, siti partecipanti, quality status e risultati sono riproducibili e auditati.

### UC-DATA-003 — Accesso AI governato

**Scenario.** Un agente assiste nella ricerca di concetti, composizione di coorti o analisi della qualità.

**Flusso principale.** L'agente usa tool API tipizzati e autorizzati, non SQL arbitrario; ogni chiamata registra modello/servizio, input minimizzato, purpose, output e decisione umana richiesta.

**Eccezioni.** Richieste che implicano diagnosi, triage, decisioni ad alto rischio, re-identificazione o accesso fuori purpose sono rifiutate o instradate a revisione formale.

**Esito verificabile.** Gli output non modificano dati clinici senza workflow esplicito e restano attribuibili e riproducibili entro i limiti del modello.

## 7. Casi d'uso operativi enterprise

### UC-OPS-001 — Sostituzione graduale di un integration engine legacy

**Scenario.** Un gruppo migra centinaia di interfacce senza big bang.

**Flusso principale.** HHC importa o ricostruisce inventario e flow, esegue shadow traffic con dati protetti, confronta output, effettua cutover per facility e mantiene rollback controllato.

**Esito verificabile.** Ogni interfaccia possiede owner, dipendenze, test, finestra di migrazione, criterio di parità e piano di ritorno.

### UC-OPS-002 — Continuità durante perdita del Control Plane

**Scenario.** Il Control Plane centrale o il collegamento WAN non è disponibile.

**Flusso principale.** I worker continuano con l'ultima configurazione valida, firmata e non scaduta; persistono eventi localmente; health e alert locali restano disponibili; le modifiche amministrative sono sospese.

**Esito verificabile.** I flow critici continuano per la finestra di autonomia configurata e si riconciliano senza doppie consegne al ritorno della connettività.

### UC-OPS-003 — Destinazione indisponibile e store-and-forward

**Scenario.** Un EHR, servizio regionale o partner non accetta messaggi.

**Flusso principale.** Circuit breaker interrompe tentativi dannosi, code persistenti conservano eventi, retry usa backoff con jitter e gli operatori vedono backlog, età e impatto clinico.

**Esito verificabile.** Nessun evento è perso; il sistema sorgente riceve la risposta prevista dal contratto; il recupero non sovraccarica la destinazione.

### UC-OPS-004 — Disaster recovery di sito

**Scenario.** Il sito primario diventa indisponibile per guasto o incidente cyber.

**Flusso principale.** L'organizzazione dichiara disaster, isola il sito, promuove il secondario, ripristina segreti e connettività secondo runbook e riconcilia eventi rispetto all'ultimo checkpoint integro.

**Esito verificabile.** RTO/RPO del service tier sono rispettati, ogni passaggio è auditato e il failback avviene solo dopo verifica dell'integrità.

### UC-OPS-005 — Cyber incident e contenimento selettivo

**Scenario.** Il SOC rileva compromissione di credenziali o connector.

**Flusso principale.** HHC revoca identità/segreti, mette in quarantena endpoint o runtime cell, preserva evidenze, continua i flow non coinvolti e invia eventi al SIEM/SOAR.

**Esito verificabile.** Il blast radius è limitato al perimetro compromesso; le azioni break-glass sono doppio-approvate e immutabilmente registrate.

### UC-OPS-006 — Upgrade senza interruzione significativa

**Scenario.** Il prodotto, un connector o uno schema devono essere aggiornati su più facility.

**Flusso principale.** Compatibilità e migration sono prevalidate; rollout canary e rolling procedono per runtime cell; health gate arresta la promozione; rollback preserva configurazione e stato compatibile.

**Esito verificabile.** Non sono introdotti eventi persi o trasformazioni miste non dichiarate; la versione effettiva è nota per ogni evento.

### UC-OPS-007 — Audit investigativo completo

**Scenario.** Auditor o incident responder deve ricostruire un accesso, una modifica o un replay.

**Flusso principale.** L'utente autorizzato interroga indice e audit store, correla identità, sessione, richiesta, approvazione, asset, versioni, destinazioni e risultati e produce un evidence package firmato.

**Esito verificabile.** La catena è completa, le omissioni sono rilevabili e la consultazione dell'audit è a sua volta auditata.

## 8. Criteri di completezza

Un caso d'uso è pronto per implementazione quando dispone di:

- requisiti funzionali e non funzionali collegati;
- data classification e ruoli privacy;
- diagramma di sequenza e threat analysis;
- SLO e capacity assumptions;
- comportamento nominale, degradato e disaster;
- test di contratto, conformance, performance e failure;
- runbook, dashboard, alert e owner;
- evidenza di compatibilità/versionamento;
- valutazione normativa e nazionale quando necessaria.

## 9. Riferimenti europei

- [European Health Data Space](https://eur-lex.europa.eu/eli/reg/2025/327/oj/)
- [EHDS: primary use, secondary use e calendario](https://health.ec.europa.eu/ehealth-digital-health-and-care/european-health-data-space-regulation-ehds_en)
- [Commissione europea — riuso sicuro dei dati sanitari](https://health.ec.europa.eu/ehealth-digital-health-and-care/reuse-health-data_en)
- [GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [NIS2](https://eur-lex.europa.eu/eli/dir/2022/2555/oj)
- [Piano UE per la cybersecurity di ospedali e healthcare provider](https://digital-strategy.ec.europa.eu/en/library/european-action-plan-cybersecurity-hospitals-and-healthcare-providers)
