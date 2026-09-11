# Esecuzione della Fase 1 — Foundation / Technical MVP

Stato: in esecuzione — WP1-00 completato, G1 superato
Baseline: 10 settembre 2026
Release target: R0.2 Technical MVP
Owner: Program Engineering
Gate owner: Architecture, Product Security, SRE, Quality Engineering e Clinical Informatics

## 1. Obiettivo e definizione di completamento

La Fase 1 porta la Engineering Foundation R0.1 a un Technical MVP installabile e ripetibile, costruito sugli stessi confini del prodotto enterprise finale. Il risultato non è autorizzato a elaborare dati clinici reali né a essere usato in produzione. La fase è completata soltanto quando due vertical slice sanitari sono configurabili senza modificare il core, le invarianti di durabilità e isolamento sono provate sotto guasto e il pacchetto di evidenze R0.2 è riproducibile da un commit.

I vertical slice vincolanti sono:

1. HL7 v2 ADT/ORU → validazione → mapping tecnico → destinazione HL7/REST;
2. HL7 v2 ORU → Canonical Observation → FHIR Observation, con proiezione OMOP dimostrativa separata dal percorso clinico.

Il completamento richiede contemporaneamente:

- HHC Integration Envelope v1 e raw immutabile con checksum;
- gerarchia Tenant → Organization → Facility → Application → Endpoint → Runtime Cell;
- Connector SDK v0.2 e connector MLLP, REST e FHIR client;
- flow dichiarativi versionati, validabili, distribuibili e reversibili;
- processing e delivery ledger, retry, DLQ, delivery replay e reconciliation;
- OIDC, autorizzazione scoped, TLS, secret reference, audit append-only e redazione PHI;
- OpenTelemetry, dashboard, alert e runbook minimi;
- ambienti Compose e Kubernetes development ricostruibili;
- prova su almeno un milione di eventi sintetici senza perdita o duplicazione non spiegata;
- RPO locale 0 dopo ACK_ON_DURABLE dimostrato con crash test;
- autonomia di quattro ore dal Control Plane per flow già distribuiti;
- backup e restore del Platform DB e del raw store;
- benchmark pubblicato, security review e prova di upgrade/rollback.

## 2. Principi non negoziabili

1. Il Control Plane non entra nel percorso sincrono dei payload ed è arrestabile senza fermare i flow già validi.
2. Un ACK positivo non precede mai il durability point dichiarato.
3. Il raw accettato è immutabile; correzioni e derivati creano nuove versioni collegate.
4. La piattaforma dichiara at-least-once transport e idempotent processing, non exactly-once end-to-end generico.
5. Tenant e facility scope sono obbligatori dalla persistenza alla telemetria; un contesto mancante o ambiguo causa deny/quarantine.
6. FHIR è una rappresentazione di scambio e OMOP una proiezione analitica: nessuno dei due sostituisce il canonical model interno.
7. Mapping tecnico e mapping semantico sono distinti, versionati e ricostruibili.
8. Un valore sconosciuto o ambiguo non viene normalizzato silenziosamente.
9. Replay, export e accesso al raw sono autorizzati, motivati, rate-limited e auditati.
10. Log, metriche e trace non contengono payload o identificativi sanitari diretti per default.
11. Ogni artifact eseguibile ha SBOM, provenance, firma, vulnerability disposition e compatibility metadata.
12. Gli shortcut ammessi riducono soltanto feature, combinazioni o automazione; non riducono durabilità, sicurezza, audit o tracciabilità.

Una violazione di questi principi arresta il work package interessato e apre una decisione ADR o un risk treatment prima di proseguire.

## 3. Perimetro

### 3.1 Incluso

- modello dati e API minime di tenancy, inventory, asset, flow e deployment;
- runtime worker autonomo e bundle/configurazione locale verificabile;
- raw store S3-compatible o equivalente, metadata nel Platform DB e checksum end-to-end;
- ledger transazionale, idempotency key, ordering scope, retry bounded, circuit breaker, DLQ e reconciliation;
- MLLP inbound/outbound per messaggi HL7 v2 ADT e ORU sintetici;
- REST outbound e FHIR REST client con autenticazione e timeout configurabili;
- parser/validator HL7, mapping deterministico e Canonical Observation minimo;
- proiezione FHIR Observation e OMOP dimostrativa asincrona;
- UI essenziale per inventory, stato, trace metadata, DLQ e replay autorizzato;
- OIDC per identità umane, identità workload distinta, RBAC minimo e secret reference;
- audit append-only iniziale, correlation end-to-end e monitoraggio continuo dell'ambiente di test;
- backup, restore, fault injection, performance baseline e pacchetto di qualification.

### 3.2 Escluso e vietato anticipare senza change decision

- dati sanitari reali, pilot clinico e integrazione con sistemi production;
- SLA contrattuale, supporto 24×7 e dichiarazione di conformità/certificazione;
- HA multi-zone qualificata, DR cross-site completo e tenancy fisica dedicata;
- CDA, IHE, DICOM, DICOMweb, dispositivi e country pack nazionali;
- designer visuale completo e marketplace connector;
- semantic registry enterprise completo, servizio terminologico distribuito e analytics operativo;
- pseudonimizzazione per secondary use reale e dataset OMOP utilizzabile per ricerca;
- migrazione dati o cutover di sistemi legacy.

Le interfacce create in questa fase devono comunque permettere le estensioni successive senza fork del prodotto o riscrittura dei contratti fondamentali.

## 4. Assunzioni, capacità e stima

La stima è ROM con confidenza media e tolleranza ±25%. Un mese/persona equivale a 18 giorni produttivi, esclusi ferie e attività non pianificate.

| Workstream | Giorni/persona base |
|---|---:|
| preparazione esecutiva, backlog e decisioni | 20–30 |
| Control Plane, tenancy, registry e deploy API | 55–75 |
| runtime, raw store, ledger e durabilità | 75–105 |
| Connector SDK e connector MLLP/REST/FHIR | 65–90 |
| mapping, canonical, FHIR e demo OMOP | 55–75 |
| sicurezza, privacy e audit | 45–65 |
| UI operativa minima | 25–40 |
| osservabilità, ambienti, backup e runbook | 55–75 |
| test, performance, fault e reconciliation | 80–110 |
| qualification, documentazione e release | 30–45 |
| **Totale base** | **505–710** |
| **Impegno pianificabile con riserva** | **36–44 mesi/persona; limite prudenziale 47** |

### 4.1 Scenario di calendario raccomandato

Con 7–8 FTE medi e avvio entro settembre 2026, il percorso critico richiede 24–28 settimane e può chiudersi entro il 31 marzo 2027. Il team tipo è composto da technical lead, tre backend/integration engineer, un SRE/platform engineer, un quality/performance engineer, frontend part-time, security part-time e clinical informatics part-time.

### 4.2 Modalità corrente: solo maintainer

Con un solo maintainer full-time e supporto agentico, la previsione realistica per tutti i gate è 24–36 mesi; una demo tecnica incompleta può arrivare in 9–12 mesi. Il codice assistito può ridurre il tempo di implementazione, ma non sostituisce benchmark, fault test, qualification né review indipendenti di sicurezza e correttezza clinica.

Per ridurre il calendario senza creare un team completo, il minimo efficace è:

- maintainer principale full-time;
- integration/backend engineer per SDK, HL7 e runtime;
- SRE/QE condiviso per ambienti, failure testing e performance;
- security e clinical reviewer esterni a milestone.

Con questo nucleo il calendario atteso è 10–14 mesi. La data Q1 2027 resta quindi un target condizionato alla capacità raccomandata, non un impegno credibile in modalità solo maintainer.

## 5. Modello di esecuzione

Il lavoro procede per incrementi verticali. Ogni work package usa gli stati PLANNED, READY, IN_PROGRESS, BLOCKED, VERIFYING e DONE. DONE richiede codice, test, documentazione, evidence e assenza di finding bloccanti; una pull request unita non equivale automaticamente a completamento.

### 5.1 Definition of Ready

Un'attività è READY quando possiede:

- outcome e criterio di accettazione misurabile;
- requirement, use case, rischio e owner collegati;
- tenant/data scope e classificazione dell'informazione;
- contratto o schema di input/output;
- failure behaviour, rollback e osservabilità previsti;
- strategia test e dati sintetici;
- classe di rischio agentico e revisore indipendente quando richiesto;
- dipendenze approvate e licenze compatibili.

### 5.2 Definition of Done

Un'attività è DONE quando:

- implementazione e migration sono versionate e riproducibili;
- unit, component, contract e test negativi pertinenti sono verdi;
- errori, timeout, retry, metriche, audit e redazione sono verificati;
- threat/hazard/risk register e tracciabilità sono aggiornati;
- artifact e dipendenze superano SAST, SCA, secret e license gate;
- runbook, rollback e compatibility note sono provati;
- evidence machine-readable è archiviata e collegata al commit;
- nessun finding Critical/High aperto è privo di disposition approvata e scadenza.

## 6. Work package e sequenza vincolante

### WP1-00 — Mobilitazione della Fase 1

Dipendenze: qualification R0.1 verde. Effort: 20–30 giorni/persona.

**Stato:** DONE — completato l'11 settembre 2026. La qualification R0.1 di ingresso è verde nel [run 34449013221](https://github.com/ingferraguti/hyperhealth-connect/actions/runs/34449013221). Il record di esecuzione e il verdetto G1 sono in [phase-1-wp1-00-qualification.md](./phase-1-wp1-00-qualification.md).

Attività:

- P1-0001: trasformare lo scope R0.2 in backlog requirement–use case–test–evidence;
- P1-0002: assegnare owner e RACI, inclusi reviewer security e clinical;
- P1-0003: definire Performance Test Manifest e hardware di riferimento;
- P1-0004: scegliere Platform DB, object store, migration tool, OIDC provider di test e telemetry backend;
- P1-0005: chiudere decisioni su transazione raw/ledger, idempotency key e bundle locale;
- P1-0006: aggiornare risk register, hazard log e privacy review per i due vertical slice;
- P1-0007: creare board, milestone, label e dashboard di avanzamento;
- P1-0008: fissare versioni, digest, licenze, owner e policy di aggiornamento delle nuove dipendenze.

Output:

- backlog baselined R0.2;
- ADR nuovi o aggiornati;
- architecture decision record per durability point;
- test manifest e environment manifest;
- matrice di tracciabilità Fase 1.

Gate G1 — Ready to build:

- nessuna decisione critica aperta su persistenza, ACK, tenancy o identity;
- tutti gli epic P0 hanno owner e acceptance test;
- nessun componente fondamentale privo di security/license disposition.

**Verdetto G1:** PASS. Le decisioni critiche sono chiuse dagli ADR-025–028; i 13 epic P0 successivi hanno owner e acceptance oracle; le dipendenze core selezionate sono versionate, digest-pinned ove containerizzate e hanno security/license disposition. I reviewer indipendenti sono staffing checkpoint vincolanti dei gate successivi e non sono sostituiti dal solo maintainer.

### WP1-01 — Tenancy, identità e Platform DB

Dipendenze: G1. Effort: 55–75 giorni/persona, parzialmente sovrapponibile a WP1-02.

Attività:

- P1-0101: implementare ID immutabili e gerarchia Tenant/Organization/Facility/Application/Endpoint/Runtime Cell;
- P1-0102: definire schema SQL, migration expand/contract, constraint e indici;
- P1-0103: applicare tenant/facility scope in repository, service e API;
- P1-0104: introdurre OIDC, ruoli minimi e workload identity separata;
- P1-0105: implementare secret reference senza valore segreto nel database o negli export;
- P1-0106: esporre API inventory con pagination, filtering, optimistic concurrency e idempotency;
- P1-0107: auditare creazione, modifica, dismissione e accesso amministrativo;
- P1-0108: creare test negativi cross-tenant e cross-facility su ogni layer;
- P1-0109: verificare Unicode, timezone, locale e conservazione degli identificativi originali;
- P1-0110: produrre seed sintetico per almeno due tenant, quattro facility e più endpoint.

Output: Platform DB v0.2, API inventory v0.2, migration, RBAC e tenant isolation suite.

Gate G2 — Scoped platform:

- nessuna query o cache opera senza scope esplicito;
- accesso cross-scope negato prima del payload access;
- migration avanti/indietro e backup logico del DB verificati;
- API indicizzate e prive di secret/PHI negli errori.

### WP1-02 — Envelope, raw store e durability point

Dipendenze: G1; usa gli scope definiti da WP1-01. Effort: 45–65 giorni/persona.

Attività:

- P1-0201: completare Envelope v1 con message, correlation, causation, tenant, facility, flow e schema version;
- P1-0202: calcolare checksum su byte originali e conservarne algoritmo/versione;
- P1-0203: implementare raw object store immutabile con metadata, retention class e content type;
- P1-0204: separare raw reference dal metadata operativo nel Platform DB/ledger;
- P1-0205: implementare ACK_ON_RECEIVE, ACK_ON_DURABLE e ACK_ON_DELIVERY come policy esplicite;
- P1-0206: garantire che ACK_ON_DURABLE avvenga solo dopo commit verificabile di raw e record di ingest;
- P1-0207: gestire object collision, upload parziale, checksum mismatch e storage timeout;
- P1-0208: impedire update in-place e accesso raw non autorizzato;
- P1-0209: creare crash point deterministici immediatamente prima e dopo il durability point;
- P1-0210: documentare retention, deletion futura, legal hold e limiti del Technical MVP.

Output: raw store adapter, envelope schema, ingest state machine e crash-test harness.

Gate G3 — Durable ingest:

- crash prima dell'ACK produce retry sicuro;
- crash dopo ACK_ON_DURABLE conserva raw ed evento recuperabile;
- checksum raw è verificabile dopo restart e restore;
- nessun ACK positivo è emesso su stato ambiguo.

### WP1-03 — Ledger, idempotenza, retry, DLQ e reconciliation

Dipendenze: G3 e scope G2. Effort: 45–60 giorni/persona.

Attività:

- P1-0301: implementare processing ledger e delivery ledger con transizioni valide;
- P1-0302: definire idempotency key e duplicate window configurabile per flow/partner;
- P1-0303: preservare ordering entro la partition key dichiarata;
- P1-0304: implementare timeout, exponential backoff, jitter, retry budget e circuit breaker;
- P1-0305: introdurre DLQ/quarantine con reason taxonomy e payload reference;
- P1-0306: implementare delivery replay separato da transformation e semantic replay;
- P1-0307: applicare autorizzazione, motivo, rate limit e audit al replay;
- P1-0308: riconciliare ingress, raw, ledger, delivery receipt e DLQ;
- P1-0309: emettere alert su gap, mismatch, queue age e retry exhaustion;
- P1-0310: testare destinazione lenta, ACK perso, duplicati, reorder, crash e restart.

Output: reliability kernel v0.2, reconciliation job e report di integrità.

Gate G4 — Explainable delivery:

- ogni evento ha uno stato terminale o una causa operativa esplicita;
- duplicate delivery e side effect sono prevenuti entro la strategia dichiarata;
- un mismatch di reconciliation fallisce il gate e genera evidence;
- replay non elude scope, retention o policy.

### WP1-04 — Connector SDK v0.2 e Connector Test Kit

Dipendenze: G2–G4. Effort: 30–40 giorni/persona.

Attività:

- P1-0401: stabilizzare lifecycle initialize/start/drain/stop e ownership delle risorse;
- P1-0402: implementare configurazione tipizzata, schema, default sicuri e secret reference;
- P1-0403: completare ingress/egress contract, receipt, error taxonomy e health model;
- P1-0404: integrare backpressure, deadline, cancellation e bounded memory;
- P1-0405: standardizzare metriche, trace, audit e correlation propagation;
- P1-0406: definire manifest di capability e permission;
- P1-0407: creare compatibility matrix SDK/runtime N/N-1 iniziale;
- P1-0408: sviluppare Connector Test Kit con fault stub, slow partner e deterministic clock;
- P1-0409: verificare che un connector di esempio non usi API interne;
- P1-0410: produrre template e guida per nuovi connector.

Output: SDK v0.2, test kit, example connector, schema manifest e compatibility contract.

Gate G5 — Stable connector boundary:

- contract suite verde per ingress ed egress;
- nessun connector può anticipare ACK o bypassare envelope/ledger;
- risorse e thread vengono rilasciati dopo drain/stop;
- configurazioni invalide o permission eccessive vengono rifiutate prima dell'avvio.

### WP1-05 — Connector HL7 v2/MLLP, REST e FHIR

Dipendenze: G5. Effort: 35–50 giorni/persona.

Attività:

- P1-0501: implementare listener MLLP con frame limit, timeout, connessioni bounded e TLS opzionale;
- P1-0502: supportare ACK HL7 coerente con validation e durability policy;
- P1-0503: implementare MLLP outbound con connection lifecycle e duplicate handling;
- P1-0504: supportare strutture ADT e ORU dichiarate, version/profile detection e charset esplicito;
- P1-0505: quarantinare messaggi malformati, oversized, con encoding non valido o profilo sconosciuto;
- P1-0506: implementare REST outbound con OAuth/OIDC client credentials o mTLS reference;
- P1-0507: implementare FHIR client con content negotiation, OperationOutcome, pagination non applicabile al singolo invio e token rotation;
- P1-0508: normalizzare receipt HTTP/FHIR senza nascondere errori clinici o applicativi;
- P1-0509: produrre synthetic corpus multilingue con correzioni, cancellazioni, duplicati e clock skew;
- P1-0510: eseguire contract, negative, fuzz-smoke e performance test per connector.

Output: connector pack R0.2 e conformance statement limitato ai profili effettivamente testati.

Gate G6 — Protocol-ready:

- MLLP ACK e HTTP/FHIR response sono correlati al delivery ledger;
- charset, profile e versione non vengono inferiti in modo distruttivo;
- input ostile non causa crash, memory growth non bounded o leakage;
- nessuna dichiarazione di conformità supera l'evidenza disponibile.

### WP1-06 — Flow dichiarativi, mapping e deployment

Dipendenze: G2, G4–G6. Effort: 45–60 giorni/persona.

Attività:

- P1-0601: definire flow schema v0.2 YAML/JSON e canonical normalization per firma/diff;
- P1-0602: implementare parse, validate, filter, enrich, map, route e project come step versionati;
- P1-0603: separare mapping tecnico e semantico e registrarli per digest/versione;
- P1-0604: implementare validate, dry-run, synthetic test, deploy, activate e rollback;
- P1-0605: produrre bundle immutabile con flow, connector config, schema, mapping e policy reference;
- P1-0606: validare compatibility SDK/runtime/connector prima del deploy;
- P1-0607: conservare l'ultima release valida nella Runtime Cell;
- P1-0608: bloccare modifiche amministrative durante assenza Control Plane senza interrompere flow validi;
- P1-0609: implementare CLI e API minime di deploy e status;
- P1-0610: testare upgrade, rollback, bundle corrotto, versione non compatibile e config scaduta.

Output: flow engine/configuration v0.2, deployment API/CLI e runtime bundle.

Gate G7 — Deployable runtime:

- due flow vengono creati e modificati senza cambiare codice core;
- il bundle è content-addressed, verificabile e rollback-safe;
- un health gate fallito non promuove la nuova versione;
- un runtime già configurato opera per almeno quattro ore senza Control Plane.

### WP1-07 — Vertical slice A: ADT/ORU verso HL7/REST

Dipendenze: G3–G7. Effort: 30–45 giorni/persona.

Attività:

- P1-0701: configurare source MLLP per due facility sintetiche;
- P1-0702: validare header, message profile, scope e business key;
- P1-0703: applicare mapping tecnico deterministico senza perdita dei valori originali;
- P1-0704: instradare verso destinazione MLLP o REST selezionata dal flow;
- P1-0705: acquisire raw, trace, mapping version, delivery receipt e audit;
- P1-0706: gestire partner down, timeout, negative ACK, duplicate, correction e replay;
- P1-0707: dimostrare isolamento fra tenant e facility con casi negativi;
- P1-0708: creare demo script e golden expected output ripetibili.

Gate M1 — First end-to-end value:

- happy path e negativi sono verdi;
- ogni messaggio è ricostruibile dal raw alla receipt;
- replay autorizzato non duplica l'effetto atteso;
- perdita del downstream non blocca o satura l'ingress oltre la policy.

### WP1-08 — Vertical slice B: ORU, Canonical Observation, FHIR e OMOP demo

Dipendenze: M1, G6 e ADR-011/013. Effort: 55–75 giorni/persona.

Attività:

- P1-0801: definire subset Canonical Observation v0.1 indipendente da FHIR e OMOP;
- P1-0802: preservare valore, unità, reference range, status, source code e provenance;
- P1-0803: implementare mapping locale → canonical con stato unmapped/ambiguous esplicito;
- P1-0804: proiettare FHIR Observation con profilo dichiarato e validazione automatica;
- P1-0805: gestire risultato preliminare, finale, corretto e cancellato senza update distruttivo;
- P1-0806: implementare proiezione OMOP asincrona dimostrativa separata dal Tier 0;
- P1-0807: registrare CDM/vocabulary/mapping version e lineage della proiezione;
- P1-0808: dimostrare che un guasto FHIR/OMOP non ritarda ACK o consegna primaria;
- P1-0809: misurare mapping coverage e impedire sostituzione silenziosa di codici ignoti;
- P1-0810: sottoporre golden corpus e trasformazioni a review clinical informatics indipendente.

Gate M2 — Semantic vertical slice:

- canonical, FHIR e OMOP sono proiezioni versionate e ricostruibili;
- correzioni/cancellazioni conservano lineage e stato precedente;
- valori ambigui non diventano dati validi;
- guasto del ramo analitico non influenza il percorso operativo.

### WP1-09 — UI operativa minima

Dipendenze: G2, G4, G7 e M1. Effort: 25–40 giorni/persona.

Attività:

- P1-0901: inventory tenant/facility/application/endpoint/runtime;
- P1-0902: vista flow/version/deployment e stato runtime;
- P1-0903: Message Explorer solo metadata con ricerca scoped e pagination;
- P1-0904: vista trace/ledger/receipt e motivazione degli errori;
- P1-0905: DLQ e delivery replay con confirmation, reason e audit;
- P1-0906: autorizzazione per route e action, incluse negative UI test;
- P1-0907: Unicode, timezone e localizzazione minima italiana/inglese;
- P1-0908: keyboard navigation e smoke accessibilità senza dichiarare ancora WCAG AA completa.

Gate M3 — Operable MVP:

- un operatore ricostruisce lo stato senza accedere direttamente ai database;
- la UI non espone raw/PHI per default;
- azioni privilegiate sono negate, motivate e auditabili;
- gli stessi asset sono esportabili e non esistono soltanto nella UI.

### WP1-10 — Audit, osservabilità e sicurezza operativa

Dipendenze: tutti i componenti devono adottare le convenzioni prima di M4. Effort: 45–65 giorni/persona.

Attività:

- P1-1001: propagare correlation/trace ID fra connector, flow, ledger e destinazione;
- P1-1002: esporre RED/USE, queue age/depth, delivery error e mapping coverage;
- P1-1003: implementare audit append-only con sequence/hash o controllo tamper-evident equivalente;
- P1-1004: coprire login, config, deploy, secret reference, replay, export e audit access;
- P1-1005: bufferizzare audit/telemetry durante collector outage entro capacity dichiarata;
- P1-1006: creare dashboard per runtime, facility, flow e vertical slice;
- P1-1007: creare alert symptom-based per ingest gap, queue age, endpoint down, raw failure e reconciliation mismatch;
- P1-1008: collegare ogni alert a un runbook testato;
- P1-1009: automatizzare PHI leakage scan su log, trace, metriche ed error response;
- P1-1010: riesaminare threat model e chiudere i finding Critical/High.

Gate M4 — Observable and auditable:

- un evento sintetico è ricostruibile end-to-end senza payload nei log;
- alterazione o gap dell'audit è rilevato;
- un collector indisponibile non elimina silenziosamente l'evidenza;
- ogni failure in scope genera stato, metrica e azione operativa coerenti.

### WP1-11 — Ambienti, backup, restore e runbook

Dipendenze: G7, M1–M4. Effort: 55–75 giorni/persona.

Attività:

- P1-1101: estendere Compose per DB, object store, OIDC, telemetry e partner simulator;
- P1-1102: completare profilo Kubernetes development con resource limit e security context;
- P1-1103: configurare network policy, non-root, read-only filesystem e secret mount reference;
- P1-1104: automatizzare backup consistente di Platform DB e raw metadata/object;
- P1-1105: eseguire restore in ambiente pulito e verificare checksum/reconciliation;
- P1-1106: documentare endpoint down, DLQ, certificate expiry, raw store failure e Control Plane outage;
- P1-1107: verificare startup, readiness, graceful shutdown, drain e restart;
- P1-1108: produrre capacity envelope per spool, raw, DB e telemetry buffer;
- P1-1109: rendere ambienti ricostruibili da manifest e versioni pin-nate;
- P1-1110: provare upgrade e rollback applicativo con schema backward-compatible.

Gate M5 — Recoverable environment:

- installazione pulita e restore sono ripetibili da runbook;
- raw checksum, ledger e receipt riconciliano dopo restore;
- un certificato scaduto o secret assente impedisce avvio insicuro;
- rollback non richiede ripristino distruttivo del database.

### WP1-12 — Performance, affidabilità e fault qualification

Dipendenze: M1–M5; ambiente performance isolato. Effort: 80–110 giorni/persona.

Attività:

- P1-1201: fissare hardware, dataset, payload distribution, warm-up e misure del benchmark;
- P1-1202: eseguire almeno un milione di eventi sintetici con checksum e conteggi end-to-end;
- P1-1203: misurare fast path HL7 ≤64 KiB rispetto a p95 ≤50 ms e p99 ≤100 ms;
- P1-1204: misurare percorso asincrono rispetto al riferimento 2.000 eventi/s da 8 KiB per 60 minuti;
- P1-1205: verificare scaling orizzontale e identificare il primo collo di bottiglia;
- P1-1206: iniettare crash prima/dopo durability point, durante delivery e durante replay;
- P1-1207: arrestare il Control Plane per almeno quattro ore mantenendo i flow autorizzati;
- P1-1208: simulare destination down, raw store timeout, DB restart, collector outage e clock skew;
- P1-1209: verificare zero perdita dopo ACK_ON_DURABLE e zero mismatch non spiegato;
- P1-1210: creare regression budget CI per le misure ripetibili e trend dashboard per quelle ambientali;
- P1-1211: eseguire security review, DAST/API negative test e retest dei finding;
- P1-1212: rieseguire restore, deployment rollback e reconciliation sotto carico.

I target enterprise vengono misurati già in Fase 1 perché il go/no-go deve scoprire ora un modello non scalabile. Un mancato target non viene nascosto: blocca la promotion oppure richiede decisione formale con remediation prima del pilot, senza dichiarazioni prestazionali non supportate.

Gate G8 — Technical qualification:

- almeno 1.000.000 eventi, conteggi e checksum riconciliati;
- RPO locale 0 dopo ACK_ON_DURABLE;
- nessuna perdita o duplicazione non spiegata;
- benchmark e saturazione pubblicati con ambiente riproducibile;
- quattro ore senza Control Plane completate;
- restore e rollback riusciti;
- zero finding security Critical aperti e nessun High senza disposition a scadenza.

### WP1-13 — Release R0.2 e chiusura della fase

Dipendenze: G8 e tutti i gate M1–M5. Effort: 30–45 giorni/persona.

Attività:

- P1-1301: congelare scope e release candidate;
- P1-1302: generare artifact, immagini, SBOM, provenance, firme e checksum;
- P1-1303: produrre compatibility matrix e conformance statement limitato;
- P1-1304: consolidare test, benchmark, fault, restore, security e clinical review evidence;
- P1-1305: aggiornare requirements, ADR, controls, risks, hazards e traceability;
- P1-1306: verificare installazione da zero e demo dei due vertical slice;
- P1-1307: rendere visibili in artifact, UI e release note i limiti non-production;
- P1-1308: svolgere go/no-go con Architecture, Security, SRE, Quality e Clinical Informatics;
- P1-1309: pubblicare qualification report R0.2 e backlog delle remediation non bloccanti;
- P1-1310: effettuare retrospettiva e rebaseline della Fase 2.

Gate G9 — Phase 1 complete:

- tutti gli exit criteria della sezione 9 sono PASS;
- evidence package è immutabile e collegato al commit/release;
- demo è ripetibile senza accesso manuale ai database;
- limitazioni e rischi residui sono approvati;
- nessun pilot o dato reale è abilitato dalla release.

## 7. Milestone e calendario integrato

Le settimane sono relative all'avvio con capacità raccomandata. Work package indipendenti possono sovrapporsi soltanto dopo il rispettivo gate di ingresso.

| Milestone | Settimana target | Risultato | Gate |
|---|---:|---|---|
| M0 | 2 | backlog, decisioni e ambienti baselined | G1 |
| M1 | 6 | tenancy e identity scope applicati | G2 |
| M2 | 10 | durable ingest e reliability kernel | G3–G4 |
| M3 | 14 | SDK e connector pack | G5–G6 |
| M4 | 17 | vertical slice ADT/REST | M1 |
| M5 | 20 | ORU/FHIR/OMOP demo | M2 |
| M6 | 22 | UI, audit, telemetry e restore | M3–M5 |
| M7 | 26 | performance e fault qualification | G8 |
| M8 | 28 | R0.2 e report di qualification | G9 |

### 7.1 Percorso critico

G1 → G2/G3 → G4 → G5 → G6 → G7 → M1 → M2 → M4/M5 → G8 → G9.

Il lavoro UI può iniziare dopo G2 e G4; osservabilità parte con il primo componente, non alla fine; test e synthetic corpus sono sviluppati insieme ai contratti. Spostare durability, tenant isolation, fault testing o reconciliation a fine progetto non è consentito.

## 8. Piano per incrementi dimostrabili

Ogni incremento deve essere eseguibile in CI e nel reference environment.

| Incremento | Demo | Criterio di uscita |
|---|---|---|
| I1 Scoped ingest | messaggio sintetico scoped persiste raw ed envelope | G2–G3 |
| I2 Reliable delivery | raw → ledger → REST con retry e receipt | G4 |
| I3 Connector boundary | connector esempio supera test kit | G5 |
| I4 ADT vertical | due facility MLLP → REST/HL7 | M1 |
| I5 Semantic vertical | ORU → canonical → FHIR + OMOP demo | M2 |
| I6 Operability | UI, trace, audit, DLQ e replay | M3–M4 |
| I7 Recovery | backup/restore, rollback e Control Plane offline | M5 |
| I8 Qualification | milione di eventi, fault e security review | G8–G9 |

## 9. Exit criteria verificabili

| ID | Criterio | Metodo di prova | Evidenza attesa |
|---|---|---|---|
| EC1 | due flow configurabili senza modifica core | installazione e demo automatizzata | demo manifest e report |
| EC2 | RPO locale 0 dopo ACK_ON_DURABLE | crash immediato post-ACK | fault timeline e reconciliation |
| EC3 | nessuna perdita/duplicazione inspiegata su ≥1M eventi | load test con checksum | raw series e count matrix |
| EC4 | fast path e async baseline pubblicate | benchmark ripetibile | performance report/environment manifest |
| EC5 | lineage completo per evento | query API/UI su sample ID | raw checksum, versioni, trace, receipt |
| EC6 | runtime continua ≥4 ore senza Control Plane | network isolation controllata | chaos report e telemetry |
| EC7 | isolamento tenant/facility | negative test a ogni layer | authorization test report |
| EC8 | nessuna PHI in log/trace/metriche | seeded leakage scanner | scan report |
| EC9 | backup/restore DB e raw riusciti | clean-room restore | restore report e checksum |
| EC10 | upgrade e rollback flow riusciti | N/N-1 compatibility test | deployment timeline |
| EC11 | security review senza Critical aperti | review, scan e retest | signed security disposition |
| EC12 | canonical/FHIR/OMOP semantic fidelity | golden corpus e clinical review | mapping diff e review record |
| EC13 | audit append-only e accessi auditati | tamper/gap/access test | audit verification report |
| EC14 | artifact riproducibili e attestati | CI release workflow | SBOM, signature e provenance |

Tutti gli EC sono obbligatori. Una deroga non può trasformare la release in Technical MVP qualificato; può soltanto produrre una build sperimentale chiaramente marcata.

## 10. Strategia di test per work package

| Layer | Quando | Blocco release |
|---|---|---|
| unit/property | ogni commit | failure o coverage del rischio insufficiente |
| component | ogni PR del modulo | error path, resource leak o scope non provati |
| contract | SDK, API e connector | incompatibilità o comportamento ambiguo |
| integration | ogni incremento I1–I8 | mismatch fra persistence, ledger e output |
| end-to-end | M1, M2 e release candidate | vertical slice non ripetibile |
| security/privacy | continuo e G8 | secret/PHI, auth bypass, Critical/High non gestito |
| performance | da M1, definitivo G8 | target/gap non misurato o regressione oltre budget |
| fault/chaos | da G3, definitivo G8 | perdita post-ACK, stato ambiguo o recovery non spiegata |
| backup/restore | M5 e release candidate | restore non ripetibile o checksum divergenti |
| semantic/clinical | M2 e release candidate | valore alterato, unknown normalizzato o lineage incompleto |

I test usano esclusivamente dati sintetici chiaramente marcati. Nessun dump, identificativo o endpoint production entra nel repository, negli artifact o nei sistemi di osservabilità della fase.

## 11. Evidence package R0.2

La pipeline deve produrre e conservare almeno:

- manifest di release con commit, toolchain e environment;
- SBOM CycloneDX e vulnerability/license disposition;
- provenance e firma degli artifact;
- test summary e report unit/component/contract/E2E;
- reconciliation matrix del milione di eventi;
- raw performance series e capacity envelope;
- crash/fault timeline, autonomia Control Plane e recovery report;
- backup/restore report con checksum;
- security review, DAST/SCA/SAST/secret/PHI scan e retest;
- clinical mapping review e golden corpus version;
- audit integrity e access-control report;
- compatibility matrix SDK/runtime/connector/flow;
- requirements–ADR–control–test–evidence traceability;
- known limitations, residual risks, approvals e scadenze.

Gli output effimeri vivono sotto target/phase1-evidence e non sono versionati. Il report di qualification versionato registra digest e riferimenti agli artifact immutabili della pipeline.

## 12. Governance dell'agentic coding

Ogni modifica assistita segue la policy di testing agentico del repository:

- il prompt non sostituisce requisito, design o acceptance test;
- l'Agentic Change Manifest registra modello/tool, input, file toccati, rischio e verifiche;
- l'agente non approva la propria modifica né decide una risk acceptance;
- codice di tenancy, auth, durability, parser, mapping, replay, audit e migration è classe A3/P0;
- test oracle e implementazione non dipendono dalla stessa generazione non verificata;
- dependency upgrade e action workflow richiedono source, digest, licenza, CVE e rollback;
- output non deterministici non diventano evidence senza verifica ripetibile;
- secret e dati clinici reali non vengono forniti a modelli o tool.

Durante la modalità solo maintainer GitHub non impone auto-approvazioni prive di indipendenza. Prima di G8, tuttavia, le modifiche A3/P0 e il pacchetto di sicurezza/clinical correctness devono ricevere review umana indipendente documentata, eventualmente tramite specialisti esterni.

## 13. Rischi principali e trigger di rebaseline

| Rischio | Segnale anticipatore | Trattamento | Trigger di rebaseline |
|---|---|---|---|
| durability raw/ledger non atomica | ACK con record mancante o stato ambiguo | spike, crash point e reconciliation prima dei connector | G3 non superato entro due iterazioni |
| varianti HL7/charset maggiori del previsto | corpus cresce senza profilo stabile | support matrix esplicita e quarantine | >20% casi fuori profilo concordato |
| SDK instabile | breaking change ripetute nei connector | freeze contract dopo G5 | >2 breaking revision dopo M1 |
| mapping clinico ambiguo | coverage bassa o golden diff frequenti | review clinica e unknown esplicito | trasformazione non validabile |
| performance insufficiente | queue age/CPU aumentano non linearmente | profiling, partitioning e bounded pipeline | target distante >30% a M1 |
| solo maintainer bottleneck | WIP elevato e review assenti | limitare WIP, automazione e specialisti milestone | percorso critico slitta >4 settimane |
| dipendenza vulnerabile/licenza incompatibile | gate SCA/license fallisce | sostituzione o upgrade pin-nato | nessuna remediation entro SLA |
| scope creep verso pilot/enterprise | richiesta di HA, DICOM o dati reali | change control e rinvio a fase corretta | >10% effort non R0.2 |
| test ambientali non ripetibili | risultati senza manifest o alta varianza | ambiente isolato e calibrazione | varianza impedisce decisione per 2 run |

La roadmap viene ribaselinata quando cambia lo scope, la capacità media varia oltre il 20%, un gate critico fallisce per due iterazioni o emerge una decisione architetturale che modifica Envelope, SDK, durability o tenancy. La rebaseline aggiorna effort, dipendenze, rischio e data; non abbassa retroattivamente gli exit criteria.

## 14. Metriche di avanzamento

Le percentuali basate sul numero di ticket non sono sufficienti. Il programma pubblica settimanalmente:

- gate superati/totali e milestone forecast;
- burn-up degli acceptance criteria, non delle sole attività;
- WIP e cycle time per work package;
- test pass/fail/flaky e mutation/fault coverage pertinente;
- requisiti, rischi e controlli senza evidence;
- defect Critical/High aperti e vulnerability age;
- mapping coverage/ambiguity per facility sintetica;
- reconciliation mismatch, duplicate e unexplained loss;
- performance p50/p95/p99, throughput, saturation e queue age;
- giorni di slittamento sul percorso critico e consumo della riserva.

## 15. Cadenza operativa

- giornaliera: CI, vulnerability/secret scan e aggiornamento dei blocchi;
- settimanale: demo dell'incremento, risk review e controllo percorso critico;
- quindicinale: milestone review, performance trend e aggiornamento traceability;
- mensile: architecture/security/clinical checkpoint e dipendenze;
- a ogni gate: evidence review e decisione esplicita pass/fail;
- fine fase: qualification board, retrospettiva e autorizzazione separata alla Fase 2.

## 16. Checklist di avvio immediato

Le prime attività, in ordine, sono:

1. aprire milestone R0.2 e creare gli epic WP1-00…WP1-13;
2. aggiornare governance/traceability.yml dalla baseline phase-0 a una sezione phase-1;
3. decidere e documentare la transazione raw/ledger e il durability point;
4. scegliere DB, object store, OIDC provider e telemetry stack di riferimento;
5. creare schema Platform DB e migration iniziale;
6. estendere TenantScope a repository, API, cache e telemetry;
7. implementare raw adapter ed Envelope v1 prima dei connector reali;
8. costruire crash-test harness e primo test ACK_ON_DURABLE;
9. implementare reliability kernel e reconciliation minima;
10. stabilizzare SDK v0.2 e soltanto dopo iniziare MLLP/REST/FHIR.

Nessun lavoro sul designer visuale, su protocolli fuori scope o sull'ottimizzazione prematura precede questi dieci punti.

## 17. Collegamenti canonici

- [Roadmap MVP → enterprise](./mvp-to-enterprise.md)
- [Release plan](./release-plan.md)
- [Qualification della Fase 0](./phase-0-qualification.md)
- [Requisiti di prodotto](../product/requirements.md)
- [Flussi dati e lifecycle](../architecture/data-flow.md)
- [Scalabilità e resilienza](../architecture/scalability-and-resilience.md)
- [Connector SDK](../connectors/connector-sdk.md)
- [Control Plane API](../api/control-plane-api.md)
- [Strategia di test](../testing/test-strategy.md)
- [Performance e chaos](../testing/performance-and-chaos.md)
- [Policy agentic coding](../testing/agentic-coding-quality-policy.md)
- [Risk register](./risk-register.md)

## 18. Decisione finale

La Fase 1 è riuscita quando Envelope, durability model, Connector SDK, flow configuration e tenancy dimostrano stabilità insieme, non quando la UI mostra una demo positiva. Se il milione di eventi, i crash test, la reconciliation, il restore o l'isolamento rivelano un difetto strutturale, il programma corregge la fondazione in R0.2 prima di qualsiasi pilot. Il passaggio alla Fase 2 richiede una nuova decisione esplicita e non autorizza automaticamente l'uso clinico.
