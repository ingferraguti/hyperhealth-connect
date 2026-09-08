# Requisiti di prodotto

Stato: baseline di prodotto 1.0  
Data di riferimento: 1 settembre 2026  
Target: release enterprise di produzione

## 1. Uso del documento

Questa baseline definisce requisiti verificabili per HyperHealth Connect. Le parole **DEVE**, **NON DEVE**, **DOVREBBE** e **PUÒ** esprimono rispettivamente obbligo, divieto, raccomandazione e opzione. Le soglie numeriche sono target di prodotto sulla topologia di riferimento; SLA contrattuali, retention e obblighi normativi devono essere configurati per cliente e Paese.

Priorità:

- **P0**: necessaria per sicurezza, correttezza o produzione enterprise;
- **P1**: necessaria per completezza della release enterprise;
- **P2**: estensione pianificabile senza violare l'architettura.

## 2. Requisiti funzionali

### 2.1 Organizzazione, tenancy e governance

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| FR-TEN-001 | P0 | Il sistema DEVE rappresentare Tenant, Organization, Facility, Application, Endpoint e Runtime Cell con identificativi immutabili. | Creazione, modifica e dismissione sono auditabili; ogni asset appartiene a uno scope valido. |
| FR-TEN-002 | P0 | Il sistema DEVE supportare isolamento logico e fisico per tenant, organizzazione, facility, storage, runtime e chiavi. | Test negativi dimostrano assenza di accesso cross-scope; deployment dedicato è possibile senza fork del codice. |
| FR-TEN-003 | P0 | Le policy DEVONO poter imporre data residency e vietare il transito dei payload nel Control Plane centrale. | Un runtime locale continua a processare e archiviare payload senza trasferirli al Control Plane. |
| FR-TEN-004 | P1 | Il sistema DEVE offrire amministrazione delegata e policy inheritance con override espliciti e tracciati. | Un admin di facility non può alterare asset di altra facility né abbassare policy corporate non derogabili. |
| FR-GOV-001 | P0 | Flow, mapping, contratti, connector e terminologie DEVONO avere owner, stato e versione. | Nessun asset privo di owner/stato può essere promosso in produzione. |

### 2.2 Connector e protocolli

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| FR-CON-001 | P0 | Il prodotto DEVE supportare HL7 v2/MLLP, FHIR REST, REST generico, file/SFTP, JDBC e messaging tramite connector ufficiali. | Ogni connector supera contract, security, failure e performance test pubblicati. |
| FR-CON-002 | P1 | Il prodotto DEVE supportare CDA/IHE, SOAP, DICOM e DICOMweb mediante moduli isolati. | I profili dichiarati hanno test di conformance; quelli non supportati non sono accettati silenziosamente. |
| FR-CON-003 | P0 | Il Connector SDK DEVE astrarre lifecycle, configurazione tipizzata, secret reference, health, retry, metriche e capability. | Un connector di esempio viene sviluppato e validato senza dipendere da API interne non pubbliche. |
| FR-CON-004 | P0 | Ogni endpoint DEVE dichiarare autenticazione, timeout, rate limit, dimensione massima, retry e comportamento di ACK/response. | La configurazione incompleta non supera la validazione pre-deploy. |
| FR-CON-005 | P1 | Il prodotto DEVE consentire connector firmati e trust policy per publisher/versione. | Un artefatto non fidato o manomesso viene rifiutato prima del caricamento. |

### 2.3 Flow, mapping e contratti

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| FR-FLW-001 | P0 | I flow DEVONO essere dichiarativi, esportabili, versionabili, validabili e firmabili. | Export/import conserva semantica; diff e firma sono verificabili; non esiste stato logico solo nella UI. |
| FR-FLW-002 | P0 | La pipeline DEVE supportare parse, validate, filter, enrich, map, semantic-map, route e project. | Ogni step espone input/output metadata, durata, versione ed errore senza loggare PHI per default. |
| FR-FLW-003 | P0 | Il runtime DEVE supportare percorso sincrono e asincrono per flow. | Test dimostrano fast path senza broker e event path persistente con retry/DLQ. |
| FR-FLW-004 | P0 | Il deploy DEVE supportare validate, dry-run, test, approve, promote, canary, rollback e revoca. | Un health gate fallito arresta il rollout e consente ritorno all'ultima versione valida. |
| FR-MAP-001 | P0 | Mapping tecnici e semantici DEVONO essere separati, versionati e soggetti a review. | Un evento registra entrambe le versioni; una modifica semantica non altera retroattivamente dataset pubblicati. |
| FR-MAP-002 | P0 | Il sistema NON DEVE sostituire valori sconosciuti con concetti standard senza regola approvata. | Un codice non mappato genera stato esplicito e metrica di coverage. |
| FR-CTR-001 | P0 | OpenAPI, AsyncAPI, JSON Schema, XSD/WSDL e altri contratti usati DEVONO essere registrati e collegati ai deployment. | È possibile ricostruire il contratto esatto applicato a ogni evento. |

### 2.4 Eventi, affidabilità e replay

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| FR-EVT-001 | P0 | Ogni evento accettato DEVE ricevere HHC Integration Envelope, message ID, correlation ID, scope, checksum e timestamp affidabile. | Eventi senza campi obbligatori sono rifiutati o quarantinati prima dell'elaborazione. |
| FR-EVT-002 | P0 | Il payload raw DEVE essere immutabile, cifrato e referenziato; i derivati DEVONO essere versionati. | Hash e object lock rilevano alterazioni; update in-place del raw è impedito. |
| FR-EVT-003 | P0 | Il sistema DEVE offrire at-least-once transport, idempotent processing e deduplica configurabile; NON DEVE promettere exactly-once end-to-end generico. | Test di crash e redelivery non producono side effect duplicati quando la destinazione supporta la strategia definita. |
| FR-EVT-004 | P0 | Ordering DEVE essere configurabile per chiave e preservato entro il partition scope dichiarato. | Test concorrenti verificano ordine per paziente/ordine quando richiesto. |
| FR-EVT-005 | P0 | Retry, backoff, jitter, circuit breaker, timeout, DLQ e riconciliazione DEVONO essere policy esplicite. | Guasto prolungato della destinazione non satura worker o sorgente e genera alert prima dello SLO breach. |
| FR-RPL-001 | P0 | Il sistema DEVE distinguere delivery replay, transformation replay, semantic reprocessing e OMOP rebuild. | La UI/API richiede tipo, scope, motivo, autorizzazione e preview degli effetti. |
| FR-RPL-002 | P0 | Replay e reprocessing DEVONO essere idempotenti, autorizzati, rate-limited e completamente auditati. | Un replay massivo richiede doppia approvazione e non può eludere retention o purpose limitation. |

### 2.5 Semantica e terminologie

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| FR-SEM-001 | P0 | Il Canonical Semantic Event Model DEVE essere versionato e indipendente dalla rappresentazione fisica FHIR/OMOP. | HL7→HL7 e DICOM→DICOM possono operare senza conversione FHIR; OMOP resta una proiezione. |
| FR-SEM-002 | P0 | Il Semantic Mapping Registry DEVE registrare fonte, concetto locale, terminologia, target, regola, owner, approvazione e validità. | Una query di lineage restituisce l'intera catena e le versioni. |
| FR-SEM-003 | P0 | Il Vocabulary Service DEVE supportare search, get, ancestors, descendants, relationships, source mapping e domain validation. | API contract test usa uno snapshot dichiarato e risultati ripetibili. |
| FR-SEM-004 | P1 | Il sistema DEVE gestire licenze, release, snapshot e custom concepts per terminologie locali. | Nessun package terminologico è distribuito o usato fuori dalle condizioni registrate. |
| FR-SEM-005 | P0 | Coverage, ambiguità e qualità dei mapping DEVONO essere misurate per dominio, sorgente e facility. | Dashboard e release gate mostrano valori e trend, non una media globale fuorviante. |

### 2.6 OMOP, analytics e riuso

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| FR-OMOP-001 | P0 | Il prodotto DEVE produrre OMOP CDM mediante projection pipeline separata dal percorso clinico critico. | Un guasto OMOP non ritarda ACK o consegna operativa. |
| FR-OMOP-002 | P0 | Ogni dataset DEVE avere versione e stato `BUILDING`, `VALIDATING`, `FAILED`, `READY`, `SUPERSEDED` o `ARCHIVED`. | Solo `READY` è esposto alle API analitiche standard. |
| FR-OMOP-003 | P0 | La release DEVE includere validazione strutturale, semantica, DataQualityDashboard e characterization. | Soglie approvate determinano automaticamente pass/fail e producono evidence package. |
| FR-OMOP-004 | P1 | Il sistema DEVE supportare più CDM e separare CDM, vocabulary, results e work schema. | Due organization eseguono analytics senza accesso reciproco e con vocabulary configurabile. |
| FR-ANL-001 | P1 | API governate DEVONO coprire concept set, coorti, feature, quality, characterization e lineage. | Autorizzazione verifica tenant, dataset, purpose e permit prima dell'esecuzione. |
| FR-ANL-002 | P1 | Definizioni di coorte e feature DEVONO essere versionate e compatibili con l'ecosistema OHDSI dove dichiarato. | La stessa definizione su stesso snapshot produce risultato equivalente entro tolleranze dichiarate. |
| FR-SECUS-001 | P0 | L'uso secondario DEVE essere separato dall'uso primario e vincolato a dataset, permit/purpose, scadenza e output policy. | Revoca/scadenza impedisce nuove elaborazioni e viene propagata agli ambienti interessati. |

### 2.7 UI, audit e osservabilità

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| FR-UI-001 | P0 | La UI DEVE offrire inventario, designer, deployment, dashboard, Message Explorer, DLQ, replay e audit secondo ruolo. | Test di autorizzazione coprono tutte le azioni e gli accessi a payload. |
| FR-OBS-001 | P0 | Metriche, trace e log DEVONO usare convenzioni comuni e correlation ID end-to-end. | Un evento campione è ricostruibile fra connector, mapping, broker e destinazione. |
| FR-OBS-002 | P0 | Il monitoraggio DEVE coprire golden signals, queue age/depth, consumer lag, endpoint health, mapping failure e semantic coverage. | Alert sintetici e reali sono consegnati e correlati con runbook. |
| FR-AUD-001 | P0 | L'audit DEVE registrare login, accessi, modifiche, approvazioni, deploy, secret operations, replay, export e break-glass. | Gli eventi includono chi/cosa/quando/dove/perché/esito e non sono modificabili dagli admin ordinari. |
| FR-AUD-002 | P0 | Il prodotto DEVE esportare audit e security event verso SIEM in formato documentato. | Perdita del SIEM non perde l'audit locale e genera backlog/alert. |
| FR-AUD-003 | P0 | Consultazione ed esportazione dell'audit DEVONO essere auditati. | Evidence package include firma/hash, filtri, richiedente e timestamp. |

## 3. Requisiti non funzionali

### 3.1 Disponibilità, business continuity e disaster recovery

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| NFR-AVL-001 | P0 | I flow Tier 0 DEVONO avere architettura senza single point of failure nella topologia enterprise. | Failure di un worker, nodo o zona non interrompe il servizio oltre il budget SLO. |
| NFR-AVL-002 | P0 | Il Data Plane DEVE continuare con l'ultima configurazione firmata durante indisponibilità del Control Plane. | Test di 24 ore offline mantiene flow, queue e audit locali; modifiche amministrative sono bloccate. |
| NFR-AVL-003 | P0 | I target SLO mensili di piattaforma sono: Tier 0 99,99%; Tier 1 99,95%; Tier 2 99,9%, misurati per servizio e facility. | Report mensile esclude solo finestre contrattualmente definite e mostra error budget. |
| NFR-BC-001 | P0 | Per eventi Tier 0 confermati al sorgente, RPO locale DEVE essere 0 nella topologia di riferimento. | Crash immediato dopo ACK non perde evento accettato; la durabilità è provata da fault injection. |
| NFR-DR-001 | P0 | Target DR di default: Tier 0 RTO ≤30 min e RPO cross-site ≤5 min; Tier 1 RTO ≤2 h e RPO ≤15 min; Control Plane RTO ≤4 h e RPO ≤15 min. | Drill almeno semestrale produce tempi reali, gap e remediation; il contratto può imporre valori più stringenti. |
| NFR-DR-002 | P0 | Backup e repliche NON DEVONO condividere tutte le stesse credenziali, failure domain o privilegi del primario. | Simulazione ransomware dimostra disponibilità di copie immutabili/offline e restore in clean environment. |
| NFR-DR-003 | P0 | Failover e failback DEVONO includere fencing, integrità, riconciliazione e prevenzione split-brain. | Test controllato dimostra singola autorità di scrittura e rileva divergenze. |

### 3.2 Prestazioni e scalabilità

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| NFR-PERF-001 | P0 | Il runtime DEVE scalare orizzontalmente per worker pool e partition key senza modifica dei flow. | Raddoppiare i worker aumenta throughput utile almeno del 70% fino al collo di bottiglia dichiarato. |
| NFR-PERF-002 | P0 | Per payload HL7 v2 ≤64 KiB, mapping in-memory e dipendenze sane, l'overhead HHC sul fast path DEVE avere p95 ≤50 ms e p99 ≤100 ms sulla topologia di riferimento. | Benchmark ripetibile separa tempo HHC da rete e destinazione e pubblica hardware/configurazione. |
| NFR-PERF-003 | P0 | Una runtime cell di riferimento DEVE sostenere almeno 2.000 eventi/s asincroni da 8 KiB per 60 minuti con CPU media ≤70%, nessuna perdita e p99 di ingest ≤250 ms. | Test ufficiale produce report, queue lag, resource saturation e checksum di riconciliazione. |
| NFR-PERF-004 | P0 | Il sistema DEVE applicare back-pressure e quote per tenant/flow, evitando noisy-neighbour. | Un carico 5× su una facility non viola lo SLO Tier 0 di una cella isolata. |
| NFR-PERF-005 | P1 | Il sistema DEVE gestire payload grandi tramite streaming e object storage, senza caricarli integralmente nella heap del worker. | Test DICOM/file multi-GB rispetta limite memoria e checksum end-to-end. |
| NFR-SCL-001 | P0 | Registri e API Control Plane DEVONO supportare almeno 100 organization, 1.000 facility, 10.000 endpoint e 20.000 flow per tenant enterprise di riferimento. | Test di listing, ricerca, deploy e audit rispetta p95 API ≤500 ms per query indicizzate. |

### 3.3 Affidabilità e integrità

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| NFR-REL-001 | P0 | Nessun errore di parse, validation, mapping o delivery DEVE essere trattato come successo senza policy esplicita. | Fault test dimostra stato terminale coerente e allarme per perdita di semantic fidelity. |
| NFR-REL-002 | P0 | Tutti i passaggi persistenti DEVONO avere checksum e reconciliation count. | Job periodici rilevano eventi mancanti, duplicati o corrotti e producono ticket/alert. |
| NFR-REL-003 | P0 | Clock e timestamp DEVONO usare UTC, mantenere offset originale quando disponibile e rilevare clock skew. | Eventi da facility con clock errato sono marcati senza riscrivere silenziosamente il tempo sorgente. |
| NFR-REL-004 | P0 | I worker DEVONO essere stateless salvo cache e durable local spool esplicitamente gestiti. | Sostituzione del pod/nodo non perde configurazione o eventi confermati. |

### 3.4 Sicurezza e privacy

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| NFR-SEC-001 | P0 | Tutte le identità umane DEVONO essere federabili via OIDC/SAML con MFA secondo policy; i workload DEVONO avere identità distinte. | Nessuna credenziale condivisa fra utenti o servizi nella baseline production. |
| NFR-SEC-002 | P0 | Comunicazioni e dati persistenti DEVONO essere cifrati con algoritmi e chiavi gestiti secondo policy aggiornata. | Scansione configurazione non rileva endpoint plaintext o secrets hard-coded. |
| NFR-SEC-003 | P0 | Segreti DEVONO essere referenziati da vault/KMS, ruotabili e mai inclusi in export, log o audit payload. | Rotazione senza downtime significativo e secret scanning superato. |
| NFR-SEC-004 | P0 | Accesso a raw/PHI, replay, export e break-glass DEVE richiedere scope, motivo e controllo rafforzato. | Sessione privilegiata è time-bound, alertata e registrata; opzionale doppia approvazione. |
| NFR-SEC-005 | P0 | Il prodotto DEVE disporre di threat model, secure SDLC, SAST/DAST/SCA, SBOM, firma artefatti e vulnerability disclosure. | Release gate blocca vulnerabilità oltre soglia salvo risk acceptance firmata e scadenza. |
| NFR-SEC-006 | P0 | La gestione vulnerabilità DEVE supportare monitoraggio, triage, remediation e comunicazioni coerenti con CRA e NIS2 applicabili. | Tempi e decisioni sono tracciati; il processo viene esercitato con tabletop annuale. |
| NFR-PRV-001 | P0 | Log, metriche e trace NON DEVONO contenere payload o identificativi diretti per default. | Test automatici di leakage su dataset sintetici falliscono la build in caso di esposizione. |
| NFR-PRV-002 | P0 | Retention, legal hold, deletion e crypto-shredding DEVONO essere configurabili per categoria e giurisdizione. | Scadenza elimina o rende inaccessibile il dato previsto e produce prova senza cancellare audit dovuto. |

### 3.5 Auditabilità e monitoraggio continuo

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| NFR-AUD-001 | P0 | L'audit store DEVE essere append-only, cifrato, time-synchronised e protetto da tamper evidence. | Verifica periodica della catena hash/firma rileva alterazione o buco nella sequenza. |
| NFR-AUD-002 | P0 | Audit e telemetry DEVONO avere buffer durabile in caso di indisponibilità dei collector. | Disconnessione di 24 ore non perde eventi entro capacity dichiarata e genera early warning. |
| NFR-MON-001 | P0 | Ogni componente DEVE esporre liveness, readiness, health dependency, metriche RED/USE e version/build identity. | Service discovery e dashboard mostrano stato per runtime cell e facility. |
| NFR-MON-002 | P0 | Il monitoraggio DEVE essere continuo 24×7 per installazioni Tier 0 e integrabile con NOC/SOC. | Alert route, escalation e acknowledge sono testati trimestralmente. |
| NFR-MON-003 | P0 | Gli alert DEVONO essere symptom-based, deduplicati e collegati a runbook; non devono dipendere soltanto da log testuali. | Game day dimostra rilevazione di perdita di throughput, queue age e data-quality regression. |

### 3.6 Manutenibilità, portabilità e lifecycle

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| NFR-MNT-001 | P0 | API, SDK, schema, flow e mapping DEVONO seguire una compatibility policy pubblicata. | Ogni breaking change ha migration tool, periodo di deprecazione e rollback documentato. |
| NFR-MNT-002 | P0 | Upgrade di componenti stateless DEVE essere rolling/canary; migrazioni dati DEVONO essere backward-compatible per la finestra di rollback. | Upgrade su reference environment rispetta error budget e rollback verificato. |
| NFR-PRT-001 | P0 | Il prodotto DEVE essere containerizzato e non dipendere da servizi cloud proprietari nel dominio. | La stessa release supera test su Kubernetes conformante e profilo Compose supportato. |
| NFR-OPS-001 | P0 | Ogni servizio e connector DEVE avere owner, dashboard, SLO, alert, runbook, backup class e support matrix. | Release gate verifica presenza e validità degli artefatti operativi. |
| NFR-I18N-001 | P1 | UI, messaggi operativi e metadata clinici DEVONO supportare Unicode, locale, timezone e traduzioni europee senza alterare codici originali. | Test con lingue e alfabeti diversi preserva contenuto, ordinamento e audit. |
| NFR-ACC-001 | P1 | La UI amministrativa DOVREBBE raggiungere WCAG 2.2 AA. | Audit accessibilità e test keyboard/screen reader prima della release enterprise. |

### 3.7 Assurance e standard enterprise

| ID | Pri. | Requisito | Criterio di accettazione |
|---|---:|---|---|
| NFR-STD-001 | P0 | Controlli e artefatti DEVONO essere mappabili a ISO/IEC 27001:2022, ISO/IEC 27701:2025 e ISO 27799:2025 entro lo scope dichiarato. | Control matrix versionata collega requisito, implementazione, test, evidenza, owner ed eccezioni. |
| NFR-STD-002 | P0 | Business Impact Analysis, strategie, piani, esercitazioni e miglioramento continuo DEVONO essere compatibili con un BCMS basato su ISO 22301:2019/Amd 1:2024. | Audit interno verifica BIA, RTO/RPO, drill, lesson learned e remediation. |
| NFR-STD-003 | P0 | Audit trail sanitario DEVE essere mappato a ISO 27789:2021 o successore adottato dalla release. | Schema mapping e conformance test coprono trigger, actor, subject, action, time e protezione del trail. |
| NFR-STD-004 | P1 | Integrazioni con dispositivi/health IT network DEVONO supportare risk management coerente con IEC 80001-1:2021 o successore. | Ogni integrazione in scope possiede risk owner, hazards, controls, residual risk e change review. |
| NFR-STD-005 | P0 | Il secure development lifecycle DOVREBBE essere mappabile a IEC 81001-5-1:2021 o successore per le componenti health software. | Product security file contiene threat model, security requirements, verification, vulnerability e maintenance evidence. |

Le versioni sono riesaminate a ogni major release. La mappatura non deve essere presentata come certificazione senza assessment indipendente e scope esplicito.

## 4. Service tier di riferimento

| Tier | Esempi | Disponibilità target | RTO | RPO cross-site | Priorità di recovery |
|---|---|---:|---:|---:|---:|
| Tier 0 | ADT, risultati critici, patient identity, ordini urgenti | 99,99% | 30 min | 5 min | 1 |
| Tier 1 | Documenti, prescrizioni non urgenti, feed operativi | 99,95% | 2 h | 15 min | 2 |
| Tier 2 | Proiezioni OMOP, analytics, batch e catalogazione | 99,9% | 8 h | 1 h | 3 |
| Control Plane | Configurazione e governance; worker autonomi | 99,9% | 4 h | 15 min | 2 |

Il service tier è assegnato al flow, non soltanto al componente. Il rispetto end-to-end dipende anche dai sistemi esterni; HHC deve distinguere chiaramente disponibilità propria, indisponibilità upstream/downstream e stato degradato.

## 5. Quality gate della release enterprise

Una release non è promuovibile se manca uno dei seguenti elementi:

1. test unitari, contract, integration, conformance, performance, failover e security superati;
2. SBOM, firme e vulnerability assessment;
3. compatibility report e migrazioni reversibili;
4. benchmark ripetibile sulla topologia di riferimento;
5. backup/restore e DR drill della release candidate;
6. dashboard, alert e runbook aggiornati;
7. threat model e privacy review per le modifiche rilevanti;
8. audit coverage delle nuove azioni privilegiate;
9. documentazione di amministrazione, deploy e supporto;
10. risk acceptance esplicita per ogni eccezione residua.

## 6. Tracciabilità principale

- Requisiti di dominio: `vision-and-scope.md` e `personas-and-use-cases.md`.
- Struttura e runtime: `../architecture/containers.md` e `components.md`.
- Eventi e correttezza: `../architecture/data-flow.md`.
- Prestazioni, SLO e DR: `../architecture/scalability-and-resilience.md`.
- Persistenza: `../architecture/data-architecture.md`.
- Decisioni vincolanti: `../adr/`.

## 7. Nota normativa

HHC deve facilitare controlli tecnici e produzione di evidenze, ma il titolare del trattamento, il responsabile, l'health data holder, l'HDAB e gli altri ruoli dipendono dal deployment. La conformità deve essere valutata con consulenza competente rispetto a GDPR, EHDS, NIS2 e diritto nazionale. Funzioni che cambiano l'intended purpose possono attivare MDR/IVDR o AI Act e richiedono un processo separato di classificazione e conformity assessment.

## 8. Fonti normative e tecniche

Consultate o riconfermate il 5 settembre 2026:

- [Regolamento UE 2016/679 GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [Regolamento UE 2025/327 European Health Data Space](https://eur-lex.europa.eu/eli/reg/2025/327/oj)
- [Direttiva UE 2022/2555 NIS2](https://eur-lex.europa.eu/eli/dir/2022/2555/oj)
- [Regolamento UE 2024/1689 AI Act](https://eur-lex.europa.eu/eli/reg/2024/1689/oj)
- [Regolamento UE 2017/745 dispositivi medici](https://eur-lex.europa.eu/eli/reg/2017/745/oj)
- [Regolamento UE 2017/746 dispositivi medico-diagnostici in vitro](https://eur-lex.europa.eu/eli/reg/2017/746/oj)
