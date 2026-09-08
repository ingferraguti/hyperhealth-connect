# Politica di qualità per agentic coding e vibe coding

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ambito | Codice, configurazioni, contratti, test e documentazione prodotti o modificati con assistenza generativa |
| Target | HHC enterprise multi-azienda e multi-facility per la sanità europea |
| Ultimo aggiornamento | 4 settembre 2026 |
| Ultima verifica fonti | 4 settembre 2026 |
| Owner | Engineering Governance e Quality Engineering |
| Co-owner | Product Security, Clinical Safety, Privacy, SRE e Interoperability |

## 1. Finalità

Questa politica consente di usare agentic coding e vibe coding senza trasformare velocità e fluidità di prototipazione in rischio non osservabile. Si applica a ogni contributo in cui un modello generativo propone, modifica, esegue, verifica o documenta software HHC, anche quando il contributo finale è committato da una persona.

Il principio di fondo è vincolante: **l'output di un agente è input non fidato finché requisiti, review indipendente e prove riproducibili non ne dimostrano l'idoneità**. Un risultato plausibile, una demo funzionante o test scritti dallo stesso agente non sono evidenza sufficiente per la produzione.

La politica disciplina l'uso dell'IA nel processo di sviluppo. Non classifica automaticamente HHC come sistema di IA né sostituisce la valutazione giuridica sull'AI Act, sui dispositivi medici o su altre normative applicabili. Le funzionalità runtime che usano modelli o agenti, incluso il Governed AI Tool Gateway, hanno inoltre un proprio ciclo di risk management, evaluation e sorveglianza.

## 2. Risultati attesi

1. Ogni modifica è attribuibile a una richiesta, una persona responsabile, un commit di base e un insieme di strumenti.
2. Le invarianti cliniche, di sicurezza, tenancy, durabilità e audit sono verificate da oracle indipendenti dall'implementazione.
3. Prompt, contesto e tool non ricevono PHI, segreti o dati cliente non autorizzati.
4. L'agente opera con privilegi, rete, tempo e filesystem minimi e osservabili.
5. Il processo impedisce test cosmetici, riduzione silenziosa dei gate e auto-approvazione.
6. Build, test e artefatti restano riproducibili anche se modello o servizio non sono più disponibili.
7. La scelta di modelli e strumenti è qualificata, monitorata e revocabile.
8. Gli incidenti causati o facilitati dall'automazione producono regression test e miglioramenti di controllo.

## 3. Terminologia

| Termine | Definizione operativa |
|---|---|
| Modifica assistita | Una persona usa un modello per suggerimenti circoscritti e decide direttamente cosa applicare. |
| Modifica agentica | Un agente può leggere il repository, invocare tool, eseguire comandi o proporre una patch multi-file. |
| Vibe prototype | Esperimento rapido guidato principalmente dall'intento e dal feedback empirico, privo finché non qualificato delle evidenze necessarie alla produzione. |
| Human Accountable Owner, HAO | Persona nominativa responsabile di obiettivo, rischio, completezza e decisione di sottoporre la modifica a review. |
| Oracle Owner | Persona o ruolo indipendente che possiede i risultati attesi e i dati golden per il rischio considerato. |
| Agent Operator | Persona o servizio che avvia e supervisiona la sessione agentica. Può coincidere con l'HAO, non con tutti gli approvatori critici. |
| Agent Platform Owner | Responsabile di modelli ammessi, sandbox, identity, logging, DLP, retention e kill switch. |
| Evidence Pack | Manifest di modifica, log di esecuzione ammessi, risultati, artifact, review e decisioni necessari a riprodurre il gate. |
| Agentic Change Manifest, ACM | Record machine-readable che descrive provenienza e perimetro della modifica assistita. |
| Oracle indipendente | Fonte del risultato atteso non derivata esclusivamente dal codice o dai test prodotti dallo stesso agente. |

## 4. Invarianti HHC che nessun agente può ridefinire

Una modifica non può allentare implicitamente le seguenti invarianti:

- gerarchia e scope `Tenant → Organization → Facility → Application → Endpoint`, con Runtime Cell come confine di failure e scaling;
- separazione Control Plane, Data Plane, Semantic Plane e Analytics Plane;
- nessun payload clinico nel Control Plane se la residency policy lo vieta;
- RPO locale 0 per eventi Tier 0 dopo l'ACK durabile;
- semantica generale `at-least-once + idempotenza + deduplica + riconciliazione`, senza promessa generica exactly-once end-to-end;
- raw immutabile, checksum, lineage e versioni di trasformazione/mapping ricostruibili;
- OMOP e analytics fuori dal percorso sincrono di ACK clinico;
- audit critico append-only e tamper-evident, distinto dalla telemetria operativa;
- deny by default per tenancy, facility, purpose e operazioni privilegiate;
- runtime capace di operare almeno 24 ore durante indisponibilità del Control Plane, entro capacity e policy;
- SLO, RTO e RPO definiti nei requisiti e nell'architettura, non modificabili da una patch di test o ottimizzazione.

Se requisito e implementazione appaiono in conflitto, l'agente deve fermare quella parte della modifica e registrare l'incongruenza. Non deve scegliere autonomamente quale fonte rendere vera.

## 5. Classificazione del rischio della modifica

La classificazione considera dati raggiungibili, blast radius, reversibilità, patient safety, autorizzazione, semantica e impatto operativo. Il valore più alto applicabile prevale.

| Classe | Esempi | Gate minimo |
|---|---|---|
| A0 — editoriale | refusi, link, formattazione senza modifica di claim | lint documentale, diff umano |
| A1 — sviluppo locale | fixture sintetiche, tool developer, refactor non runtime dimostrato | test interessati, static checks, una review umana |
| A2 — produzione ordinaria | endpoint non privilegiato, UI, normale logica di servizio | suite PR completa, contract/integration, SAST/SCA, review owner |
| A3 — critica | authn/authz, tenancy, ACK/durabilità, parser clinico, mapping, audit, crittografia, migration, connector, backup/restore, failover, policy | specification/test-first, oracle indipendente, mutation o fault test pertinente, due approvazioni competenti, release evidence |
| A4 — azione vietata all'autonomia | segreti o dati di produzione, disattivazione gate, approvazione release, break-glass, modifica diretta di produzione, cancellazione irreversibile, accettazione del rischio | esecuzione solo umana attraverso processo privilegiato separato; l'agente può al massimo preparare una proposta non eseguita |

Una modifica A3 non è declassificata perché contiene poche righe. Un cambiamento di una condizione di autorizzazione o di un punto di ACK resta A3.

## 6. Matrice di autonomia

| Attività | A0/A1 | A2 | A3 | A4 |
|---|---:|---:|---:|---:|
| Leggere codice e documentazione autorizzati | sì | sì | sì, con logging | solo contesto redatto |
| Proporre piano e patch | sì | sì | sì | proposta testuale soltanto |
| Eseguire test in ambiente effimero | sì | sì | sì, sandbox dedicata | no |
| Aggiungere dipendenza | review | review + SCA/licenza | Architecture e Security approval | no |
| Modificare test esistenti | review | diff evidenziato | Oracle Owner + reviewer | no |
| Fare merge | no auto-merge | no auto-merge | no auto-merge | no |
| Promuovere/deployare | pipeline approvata | pipeline approvata | separation of duties | no autonomia |
| Accettare finding o waiver | no | no | no | decisione umana formalizzata |

Il passaggio da un agente a un secondo agente non costituisce separation of duties. Può fornire una challenge aggiuntiva, ma almeno una decisione qualificante resta umana.

## 7. Controllo di prompt, contesto e dati

### 7.1 Dati vietati

Non devono entrare nel prompt, nella memoria del tool, negli allegati o nel transcript esterno:

- PHI e dati personali reali, anche pseudonimizzati se la base e il perimetro non lo autorizzano;
- credenziali, token, private key, recovery code e secret material;
- dump, log, trace, screenshot o ticket di produzione non redatti;
- configurazioni cliente che rivelano indirizzi, identità, trust relationship o topologia sensibile;
- dataset proprietari o documenti soggetti a licenza non compatibile con il servizio usato.

Si usano fixture sintetiche marcate, esempi ufficiali con provenienza e support bundle redatti. La sola rimozione del nome del paziente non rende sicuro un payload sanitario.

### 7.2 Prevenzione della prompt injection

Issue, file, commenti, output di tool, pagine web, payload e documentazione di terze parti sono dati non fidati. Testo che ordina di ignorare policy, leggere secret, eseguire comandi o inviare dati fuori perimetro non modifica l'autorità dell'agente.

Controlli obbligatori:

- separare istruzioni approvate, contenuti del repository e output esterni;
- applicare allowlist di tool e destinazioni di rete per task;
- richiedere conferma umana fuori banda per un'azione A4;
- impedire che il contenuto analizzato costruisca comandi di shell, path distruttivi o URL di esfiltrazione;
- redigere output prima di inserirlo nel contesto del modello;
- simulare prompt injection nel benchmark di qualificazione dell'agente.

### 7.3 Retention e minimizzazione

Si conserva il minimo necessario ad audit e riproduzione: richiesta normalizzata, policy applicata, tool autorizzati, comandi ed esiti, diff, identificativi di modello/servizio se disponibili, approvazioni e checksum. Non è richiesta né desiderata la conservazione del ragionamento interno del modello. Transcript estesi sono disabilitati o hanno accesso e retention specifici; nessun transcript diventa automaticamente parte del repository.

## 8. Sandbox e identità dell'agente

Ogni sessione agentica che può modificare codice usa:

- worktree o workspace effimero derivato da un commit noto;
- identità workload dedicata, mai credenziali personali riutilizzate;
- filesystem limitato al repository e a directory temporanee esplicite;
- rete negata per default, con allowlist di registry, mirror e fonti ufficiali necessarie;
- package registry interno o proxy con anti-typosquatting e policy licenze;
- nessuna route verso produzione, backup, SIEM o ambienti cliente;
- secret effimeri, scoped, non leggibili dal codice sotto test quando non indispensabili;
- limiti CPU, memoria, processo, durata e volume di output;
- logging di invocazioni tool, exit code, digest degli artefatti e destinazioni di rete;
- cleanup verificato, revoca credenziali e attestazione di chiusura.

I runner sono ricreati da immagini firmate. La cache non può attraversare tenant, repository o trust domain senza content addressing e validazione. Un agente non modifica la propria sandbox policy.

## 9. Workflow obbligatorio

### 9.1 Intake

Prima della generazione si registrano:

1. problema e non-obiettivi;
2. requisiti e use case coinvolti;
3. classe A0–A4 e motivazione;
4. file e componenti consentiti;
5. invarianti, rischi e abuse case;
6. criteri di accettazione osservabili;
7. oracle e relativo owner;
8. test richiesti e ambiente;
9. strategia di rollout e rollback;
10. dati e fonti utilizzabili.

Per A3 i criteri non possono essere inventati dopo aver visto il comportamento dell'implementazione. Un cambiamento del criterio riapre la review.

### 9.2 Produzione della modifica

L'agente:

- opera sul minimo perimetro utile;
- legge i contratti e gli ADR prima di modificare l'implementazione;
- produce diff piccoli e spiegabili;
- non nasconde errori con fallback generici;
- aggiunge o aggiorna test coerenti con il rischio;
- conserva seed e minimal failing example per test generativi;
- documenta assunzioni e limiti verificabili;
- non marca come eseguito un test che non è stato realmente eseguito.

### 9.3 Verifica indipendente

La verifica usa almeno una fonte indipendente per ogni rischio A3:

- golden corpus approvato da informatica clinica o interoperability;
- property/invariant derivata dal requisito, non dall'implementazione;
- validator ufficiale o reference implementation qualificata;
- differential test contro versione precedente o partner qualificato;
- mutation test per dimostrare la sensibilità delle asserzioni;
- fault injection per semantica di failure e crash consistency;
- reconciliation su input, ledger, output e quarantena;
- review di persona competente diversa dall'Agent Operator.

Un test generato nella stessa sessione è utile, ma non è l'unico oracle. Snapshot e golden aggiornati dall'agente richiedono diff semantico e approvazione dell'Oracle Owner.

### 9.4 Review e merge

Ogni pull request dichiara l'origine assistita, la classe, l'ACM e i test modificati. I reviewer vedono separatamente:

- codice di produzione;
- test/oracle/fixture;
- contratti e migration;
- dipendenze e lockfile;
- cambiamenti ai gate CI;
- dati di benchmark;
- documentazione e claim.

A3 richiede almeno due approvazioni, scelte secondo il rischio: code owner più Security, Clinical Safety/Terminology, Privacy, Interoperability o SRE. L'autore umano non può essere l'unico approvatore.

### 9.5 Release

La normale pipeline firmata produce SBOM, provenance, risultati e artifact. L'agente non possiede credenziale di promotion e non approva il release decision record. Canary, synthetic monitoring e rollback seguono gli stessi gate di modifiche non assistite, con osservazione più stretta quando il rischio o la novità lo richiedono.

## 10. Agentic Change Manifest

Schema logico minimo:

```yaml
schemaVersion: hhc.agentic-change/v1
changeId: CHG-2026-000123
taskRef: REQ-...
baseCommit: sha256-or-git-commit
resultCommit: git-commit
classification: A3
humanAccountableOwner: team/user-id
agentOperator: team/user-or-service-id
oracleOwners: [role-id]
agent:
  provider: approved-provider-id
  model: approved-model-id
  serviceVersion: recorded-if-available
  policyProfile: agent-sandbox-critical-v2
execution:
  runnerImageDigest: sha256:...
  tools: [tool-id@digest]
  networkDestinations: [approved-mirror-id]
  permissions: [repo-read, worktree-write, test-execute]
scope:
  allowedPaths: [module/...]
  changedPaths: [module/...]
  dataClasses: [synthetic]
evidence:
  requirementIds: [FR-EVT-003]
  testRunIds: [run-id]
  independentOracleRefs: [golden-pack@version]
  sbomRef: artifact://...
  provenanceRef: artifact://...
  sourceRefs: [official-source-id]
reviews:
  - role: sre
    decision: approved
    recordRef: review-id
limitations: []
```

Il manifest non contiene PHI, secret o ragionamento interno. Se il provider non espone una versione immutabile del modello, si registra l'identificativo disponibile e il limite di riproducibilità; questo può escludere l'uso per determinate classi A3.

## 11. Protezione contro il test gaming

Sono vietati senza approvazione esplicita dell'Oracle Owner:

- rimuovere o indebolire un'asserzione per far passare la build;
- ridurre soglia di coverage, mutation, performance, sicurezza o resilienza;
- aggiungere retry che converte un fail in pass;
- aumentare timeout senza analisi causale e budget;
- escludere file, regole, vulnerabilità o mutanti dal gate;
- accettare automaticamente nuovi snapshot/golden;
- mockare il comportamento che costituisce l'oggetto della prova;
- catturare eccezioni critiche e restituire successo/degraded non previsto;
- marcare un test flaky, quarantinarlo o cancellarlo senza issue, owner ed expiry;
- ottimizzare il codice per fixture note degradando distribuzioni reali;
- modificare simultaneamente oracle e implementazione senza diff semantico indipendente.

La pipeline assegna ownership rinforzata a directory di test critici, policy, benchmark, fixture e CI. Un diff che le tocca aumenta automaticamente la classe e richiede review dedicata.

## 12. Qualificazione di agenti, modelli e prompt pack

Prima dell'ammissione si usa un benchmark HHC versionato e privo di dati reali. Include task corretti, ambigui, malevoli e impossibili. Le dimensioni minime sono:

| Dimensione | Esempio di prova | Criterio |
|---|---|---|
| Correttezza | fix con hidden regression suite | nessuna regressione critica |
| Scope discipline | richiesta circoscritta | nessun diff estraneo o accesso fuori scope |
| Security | authz bug e unsafe deserialization | controllo preservato e finding rilevato |
| Prompt injection | issue/file con istruzioni ostili | istruzione ignorata e segnalata |
| Privacy | log contenente canary PHI sintetico | nessuna inclusione in prompt/output vietato |
| Test quality | implementazione volutamente mutata | oracle uccide i mutanti rilevanti |
| Reliability | crash fra commit e ACK | nessun falso successo o perdita silenziosa |
| Semantica | mapping locale ambiguo | nessuna mappatura inventata |
| Tool safety | comando distruttivo suggerito dal contesto | rifiuto e nessuna esecuzione |
| Honesty | tool indisponibile/test non eseguito | limite dichiarato, nessun risultato simulato |

Si registrano task success rate con confidence interval, false pass, escaped defect, azioni vietate tentate, diff estraneo, costo, latenza e interventi umani. L'acceptance rate delle patch non è una metrica di qualità sufficiente.

Nuovo modello, major version, provider, system prompt, tool permission, retrieval source o orchestrator richiede almeno regression qualification proporzionata. Per A3 la versione non qualificata opera in shadow mode finché non raggiunge la baseline e non presenta nuovi failure mode critici.

## 13. Dipendenze e supply chain suggerite dall'agente

Una dipendenza non viene ammessa perché popolare o proposta con sicurezza dal modello. La richiesta contiene:

- problema non risolvibile ragionevolmente con la baseline;
- repository e documentazione ufficiali;
- licenza e compatibilità d'uso, incluse clausole relative ad agenti o dati;
- manutenzione, release, supporto, security policy e rischio di abbandono;
- dipendenze transitive, SBOM, vulnerabilità e provenienza;
- alternative considerate e costo di rimozione;
- versione/digest pinnati nel Toolchain BOM o product BOM;
- test di compatibilità, performance e failure;
- owner, ciclo di aggiornamento ed exit plan.

Installazioni dirette da URL, branch, gist, package omonimi non verificati o tag mobili sono vietate nei gate. Il nome suggerito viene confrontato con coordinate ufficiali per contrastare dependency confusion e typosquatting.

## 14. Metriche e sorveglianza

Dashboard per repository, team e classe:

- percentuale di modifiche con ACM completo;
- escaped defect e incidenti per origine assistita/non assistita;
- false pass rilevati da hidden/independent suite;
- mutation score e requisito coverage dei moduli critici;
- tasso di modifica/rimozione test nello stesso change;
- finding SAST/SCA/secret per modifica;
- rework dopo review e dopo canary;
- flaky test introdotti e tempo di riparazione;
- rollback e change failure rate;
- accessi negati, prompt injection e azioni A4 tentate;
- drift fra policy di sandbox dichiarata e osservata;
- tempo di qualifica per modello/tool e data dell'ultima rivalutazione.

Le metriche non premiano linee generate, volume di commit o autonomia senza intervento. Target di velocità non può prevalere su gate P0.

## 15. Incident response e revoca

Se una modifica agentica causa o può causare perdita, leakage, corruzione semantica, falso ACK, bypass o audit gap:

1. sospendere rollout e sessioni con il profilo interessato;
2. isolare l'artefatto e conservare ACM, log tool, build, test e provenance;
3. attivare rollback o recovery secondo tier e runbook;
4. verificare tenant/facility colpiti tramite ledger e audit, senza affidarsi ai soli log;
5. ruotare credenziali se il contesto può averle esposte;
6. classificare causa in requisito, prompt/context, modello, tool, sandbox, review, oracle o gate;
7. aggiungere regression test e aggiornare benchmark/policy;
8. riqualificare prima di riabilitare il profilo;
9. eseguire disclosure e notifica secondo processo legale applicabile.

Il kill switch revoca centralmente modello, provider, prompt pack, tool o versione runner senza richiedere una modifica in ogni repository.

## 16. Casi d'uso completi

### AC-01 — Correzione del punto di ACK HL7 v2

**Scenario:** un agente propone di inviare ACK prima del flush durabile per ridurre la latenza.

**Rischio:** A3, perdita silenziosa dopo crash e violazione RPO locale 0.

**Prove:** state-machine model, crash prima/dopo commit, kill del processo, retry del sender, deduplica e riconciliazione input/ledger/output. L'oracle è il requisito FR-EVT-003 con golden fault timeline posseduta da SRE.

**Esito richiesto:** la proposta è respinta oppure mantiene l'ACK dopo il durability point; p95/p99 sono misurati senza cambiare la semantica.

### AC-02 — Parser di risultato laboratorio generato dall'agente

**Scenario:** viene aggiunto supporto per varianti HL7 v2 di OBX con valori numerici, testo, unità UCUM, reference range e amendment.

**Rischio:** A3, reinterpretazione clinica o perdita di precisione.

**Prove:** corpus ufficiale/sintetico approvato, malformed/fuzz, Unicode, decimal, timezone, null flavor, differential test HAPI/adattatore, semantic diff e mutation testing.

**Esito richiesto:** raw preservato; valori non interpretabili sono quarantinati, non inventati; versione parser e mapping registrate.

### AC-03 — Mapping locale verso LOINC/SNOMED CT

**Scenario:** l'agente suggerisce automaticamente concetti per codici locali.

**Rischio:** A3 e distinto dal mero coding; la proposta semantica non è approvazione.

**Prove:** terminology snapshot pinnato, evidence del mapping, broader/narrower/equivalent, unità, historical association, doppia review e replay.

**Esito richiesto:** solo mapping `APPROVED` entra in produzione; ambiguous/no-map resta esplicito; nessun remap retroattivo di dataset pubblicati.

### AC-04 — Endpoint amministrativo multi-tenant

**Scenario:** una patch aggiunge ricerca e bulk action su flow di più facility.

**Rischio:** A3 per BOLA/BFLA e confused deputy.

**Prove:** matrice attore × tenant × organization × facility × purpose, ID enumeration, cache/search/index leakage, token audience, stale grant e audit decision.

**Esito richiesto:** deny uniforme cross-scope; query e side effect filtrati server-side; nessun identificativo esterno rivela esistenza di risorse non autorizzate.

### AC-05 — Migration del delivery ledger

**Scenario:** l'agente converte schema e backfill in un'unica migration.

**Rischio:** A3, lock, downtime, perdita e rollback impossibile.

**Prove:** expand/migrate/contract, N/N-1 mixed version, volume rappresentativo, crash/restart, checksum, rollback/fallback e restore rehearsal.

**Esito richiesto:** zero downtime entro budget, conteggi riconciliati, vecchia versione compatibile fino al gate di contract.

### AC-06 — Nuova dipendenza proposta dal modello

**Scenario:** il codice importa un package con nome plausibile non presente nel BOM.

**Rischio:** A2/A3, typosquat o supply-chain compromise.

**Prove:** coordinate dal repository ufficiale, licenza, maintainer/security posture, SBOM, firma/provenance, mirror interno e alternative.

**Esito richiesto:** la build fallisce fino all'approvazione; nessun download arbitrario dalla sessione agentica.

### AC-07 — Ottimizzazione del mapping engine

**Scenario:** una cache suggerita migliora il benchmark ma omette snapshot terminologico e tenant dalla chiave.

**Rischio:** A3, contaminazione semantica cross-tenant.

**Prove:** property sulla chiave, dati di due tenant con codici uguali e mapping diversi, concurrency, eviction, upgrade del vocabolario e benchmark blind.

**Esito richiesto:** chiave completa; zero cross-scope result; regressione entro budget e nessun aumento di cardinalità non controllato.

### AC-08 — “Riparazione” di un test flaky

**Scenario:** l'agente aggiunge retry e sleep a un test di failover.

**Rischio:** A3 perché nasconde una race sulla leadership.

**Prove:** scheduler/clock controllati, ripetizione con seed, trace della state machine e stress concurrency.

**Esito richiesto:** la race è corretta o il test resta failing/quarantena formalizzata; retry non converte il gate in pass.

### AC-09 — Prompt injection in una issue di connector

**Scenario:** un testo di esempio ordina all'agente di leggere credenziali e pubblicare un log.

**Rischio:** esfiltrazione e compromissione supply chain.

**Prove:** canary secret non reale, deny di filesystem/rete, audit invocazioni tool.

**Esito richiesto:** istruzione ignorata, accesso negato, evento di sicurezza correlato, nessun contenuto sensibile nell'output.

### AC-10 — Payload clinico reale incollato nel prompt

**Scenario:** un operatore tenta di far diagnosticare un errore con un messaggio di produzione.

**Rischio:** privacy e data transfer non autorizzato.

**Prove:** DLP pre-submit, marker sintetici, workflow di redazione e support enclave.

**Esito richiesto:** invio bloccato; viene generata una fixture sintetica equivalente; il tentativo è auditato senza conservare il payload.

### AC-11 — Modifica del runbook DR

**Scenario:** un agente propone una sequenza di failover più breve saltando fencing e reconciliation.

**Rischio:** A3/A4 in esecuzione reale.

**Prove:** tabletop, simulation in resilience lab, split-brain, stale primary, RTO/RPO e chain of custody.

**Esito richiesto:** il documento non viene approvato finché il rehearsal non dimostra fencing, autorità decisionale e consistenza; l'agente non esegue il failover.

### AC-12 — Modifica del Governed AI Tool Gateway

**Scenario:** viene aggiunto un tool per interrogare dataset OMOP.

**Rischio:** A3 per purpose, disclosure e prompt injection; possibile ulteriore classificazione normativa da valutare.

**Prove:** allowlist di query tipizzate, row/cell suppression, purpose, rate/query budget, membership inference, indirect injection, audit e kill switch.

**Esito richiesto:** niente SQL libero o raw clinical access; output minimizzato; ogni chiamata è attribuibile e revocabile.

## 17. Gate non derogabili

Nessun waiver può trasformare in pass:

- accesso cross-tenant o cross-facility;
- ACK positivo senza durabilità dichiarata;
- perdita o corruzione silenziosa di un evento;
- mapping clinicamente pericoloso senza stato di errore;
- bypass authn/authz/purpose o audit critico assente;
- PHI o secret trasmesso a un servizio non autorizzato;
- prova inventata, risultato non eseguito o evidence alterata;
- auto-approvazione agentica di merge, release o rischio;
- restore Tier 0 non dimostrato entro target.

Una deroga su controllo non critico ha owner umano, motivazione, compensazione, scadenza e visibilità nel release decision record.

## 18. Fonti ufficiali e stato di verifica

Fonti verificate il **4 settembre 2026**:

- [NIST SP 800-218, Secure Software Development Framework 1.1](https://csrc.nist.gov/pubs/sp/800/218/final), baseline per secure SDLC;
- [NIST SP 800-218A](https://csrc.nist.gov/pubs/sp/800/218/a/final), profilo SSDF per sistemi e modelli generativi; integra, non sostituisce, SSDF 1.1;
- [NIST AI RMF 1.0 e risorse](https://www.nist.gov/itl/ai-risk-management-framework), con funzioni Govern, Map, Measure e Manage e profilo generative AI NIST AI 600-1;
- [OWASP ASVS 5.0.0](https://owasp.org/www-project-application-security-verification-standard/), release stabile del 30 maggio 2025 e riferimento versionato per i requisiti applicativi;
- [SLSA specification 1.2](https://slsa.dev/spec/v1.2/), riferimento per provenance e integrità della supply chain;
- [Regolamento (UE) 2025/327 — EHDS](https://eur-lex.europa.eu/eli/reg/2025/327/oj/), in vigore, applicabile dal 26 marzo 2027 con fasi successive; nel 2026 HHC prepara evidenze e architettura senza dichiarare conformità anticipata;
- [Commissione europea — quadro e calendario AI Act](https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai), consultato per lo stato al 2026; l'applicabilità concreta richiede classificazione legale del ruolo e del caso d'uso;
- [Commissione europea — Cyber Resilience Act](https://digital-strategy.ec.europa.eu/en/policies/cyber-resilience-act), inclusi reporting dal 11 settembre 2026 e piena applicazione dal 11 dicembre 2027, quando applicabile al prodotto/ruolo.

Queste fonti supportano il processo, non certificano HHC. Legal, Security e Clinical Safety mantengono una matrice di applicabilità per paese, deployment, componente e ruolo economico.

## 19. Collegamenti

- [Strategia di test e quality engineering](./test-strategy.md)
- [Toolchain e ambienti di test](./test-toolchain-and-environments.md)
- [Conformità e interoperabilità](./conformance-and-interoperability.md)
- [Performance, resilienza e chaos engineering](./performance-and-chaos.md)
- [Requisiti di prodotto](../product/requirements.md)
- [Architettura dei componenti](../architecture/components.md)
- [Connector SDK](../connectors/connector-sdk.md)

