# Contesto di sistema

Stato: baseline architetturale 1.0  
Data di riferimento: 1 settembre 2026  
Target: HyperHealth Connect enterprise, produzione multi-azienda e multi-facility

## 1. Scopo

Questo documento descrive HyperHealth Connect (HHC) nel suo ecosistema, gli attori, i sistemi esterni, i confini di responsabilità e le trust boundary. Usa il livello **System Context** del modello C4; i deployable sono definiti in `containers.md` e i moduli in `components.md`.

## 2. Sistema di interesse

HHC è l'infrastruttura che governa e rende osservabili gli scambi tra sistemi sanitari. Acquisisce eventi, preserva il payload originale quando previsto, applica contratti e trasformazioni versionate, instrada verso una o più destinazioni, produce rappresentazioni semantiche e proiezioni analitiche e mantiene lineage e audit.

HHC è un intermediario tecnico e semantico. Il sistema clinico sorgente resta autorevole per il contenuto che emette; il sistema destinatario resta responsabile dell'uso clinico; repository, PACS, EHR, MPI e registri mantengono i rispettivi ruoli di system of record.

## 3. Contesto organizzativo

```text
Enterprise / Tenant
├── Organization A
│   ├── Facility A1
│   │   ├── HIS/EHR, LIS, RIS/PACS, Devices
│   │   └── Runtime Cell A1
│   └── Facility A2
│       └── Runtime Cell A2
├── Organization B
│   └── Facility B1
│       └── Runtime Cell B1
└── Shared Enterprise Services
    ├── HHC Control Plane
    ├── Identity / PKI / Secrets
    ├── Monitoring / SIEM / ITSM
    └── Optional shared semantic and analytics services
```

Il tenant è il confine massimo contrattuale e di policy. Organization e Facility definiscono ownership, delega e data locality. Le Runtime Cell costituiscono failure e scaling domain tecnici: una facility può avere più celle e una cella può servire più facility soltanto se policy, rischio e capacità lo consentono.

## 4. Diagramma di contesto

```text
                     ┌──────────────────────────────┐
                     │  Utenti enterprise HHC       │
                     │  Admin · Engineer · Steward  │
                     │  SRE · SOC · DPO · Auditor   │
                     └──────────────┬───────────────┘
                                    │ OIDC/SAML + MFA
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    HYPERHEALTH CONNECT                          │
│ Control Plane · Runtime Cells · Semantic Hub · OMOP/Analytics   │
└───┬─────────────┬──────────────┬──────────────┬──────────────┬──┘
    │             │              │              │              │
    ▼             ▼              ▼              ▼              ▼
 Clinical      National/      Enterprise     Security &     Secondary-use
 systems       EU services    platforms      operations     ecosystem
 HIS/EHR       Regional       IdP/PKI         SIEM/SOAR      HDAB/SPE
 LIS/RIS       MyHealth@EU    Vault/KMS       NOC/ITSM       Research
 PACS/Devices  Registries     Git/CI/CD       Backup/DR      HealthData@EU
```

## 5. Attori umani

| Attore | Interazione | Confine |
|---|---|---|
| Integration Engineer | Disegna, testa e promuove flow e mapping | Nessun accesso implicito ai payload di produzione |
| Clinical Informatician / Data Steward | Approva semantica, terminologie e data-quality gate | Opera solo sui domini assegnati |
| Platform e Site Administrator | Gestisce tenant, facility, runtime e capacity | Non eredita privilegi clinici |
| SRE / NOC | Monitora SLO, code, failover e recovery | Usa telemetria redatta per default |
| CISO / SOC | Monitora minacce, revoca e contenimento | Riceve security event minimizzati |
| DPO / Privacy Officer | Verifica accessi, retention e diritti | Ruolo indipendente e auditato |
| Auditor | Consulta evidenze read-only e firmate | Ogni consultazione è auditata |
| Researcher / Data Analyst | Usa dataset e API autorizzate | Nessun accesso ai raw operativi |
| Clinico | Riceve dati nei sistemi assistenziali | Non usa la console HHC come cartella clinica |
| System Integrator / Vendor | Distribuisce connector o configurazioni | Limitato a tenant, ambienti e contratti assegnati |

## 6. Sistemi sanitari esterni

### 6.1 Sistemi di assistenza

- **HIS/EHR/EMR e cartelle cliniche**: ADT, documenti, ordini, risultati, episodi, problemi, terapie.
- **LIS e laboratori**: ordini, campioni, risultati preliminari/finali/corretti, valori critici e codici locali.
- **RIS/PACS/VNA**: ordini, worklist, referti, DICOM/DICOMweb e metadati imaging.
- **Farmacia e prescrizione**: prescrizioni, dispensazioni, terapie e cataloghi farmaco.
- **Pronto soccorso e 112/118**: eventi urgenti con latency budget rigoroso; HHC non effettua triage.
- **Dispositivi e gateway**: osservazioni ad alta frequenza, identità del dispositivo, timing e qualità del segnale.
- **MPI/MDM e anagrafiche**: identità paziente/professionista/struttura; HHC non esegue merge clinico implicito.
- **Repository documentali e firme**: conservazione, firma e valore legale restano responsabilità dei servizi dedicati.
- **Telemedicina, territorio e patient-generated data**: provenance e livello di validazione accompagnano il dato.

### 6.2 Infrastrutture regionali, nazionali ed europee

- fascicoli sanitari, registri, prescrizione elettronica e flussi amministrativi nazionali;
- nodi e servizi MyHealth@EU per uso primario, tramite adapter conformi alla giurisdizione;
- Health Data Access Bodies e secure processing environments per uso secondario;
- HealthData@EU e cataloghi nazionali/europei quando specifiche e autorizzazioni lo consentono;
- sorveglianza epidemiologica, registri di patologia e autorità di sanità pubblica.

Le differenze nazionali sono isolate in adapter e profili configurabili. Il core non incorpora assunzioni su identificativi, basi giuridiche o formati di un singolo Stato membro.

### 6.3 Piattaforme dati e scientifiche

- data lake, data warehouse e BI;
- istanze OMOP CDM per organization, network o progetto;
- strumenti OHDSI per qualità, characterization, coorti, feature e studi;
- piattaforme AI/ML che accedono tramite API governate e non mediante SQL arbitrario sui dati clinici;
- ambienti di ricerca e secure processing environment soggetti a permit e output control.

## 7. Sistemi enterprise di supporto

| Sistema | Responsabilità esterna | Integrazione HHC |
|---|---|---|
| Identity Provider | Identità, MFA, federation e lifecycle account | OIDC/SAML; mapping di gruppi e claim a ruoli HHC |
| PKI / Certificate Authority | Emissione e revoca certificati | mTLS, firma e trust store per endpoint/workload |
| Vault / KMS / HSM | Custodia e rotazione segreti e chiavi | Secret reference, envelope encryption e key rotation |
| Git / CI/CD | Versione, review e promotion delle definizioni | Configuration as Code e artefatti firmati |
| SIEM / SOAR | Correlazione security, detection e response | Audit e security event; contenimento via API controllate |
| NOC / APM | Monitoraggio disponibilità e performance | OpenTelemetry, metriche, alert e SLO |
| ITSM / CMDB | Incident, change, asset e ownership | Ticket, change ID, inventory e runbook link |
| Backup / Cyber vault | Copie immutabili e recovery | Backup policy, restore evidence e clean-room recovery |
| DNS/NTP | Naming e tempo affidabili | UTC, clock-skew detection, service discovery |

## 8. Trust boundary

### TB-1 — Utente verso Control Plane

Il traffico passa attraverso WAF/API gateway, autenticazione federata e authorization policy. Le sessioni privilegiate richiedono MFA, scadenza breve e, per operazioni ad alto impatto, approvazione o step-up authentication. La UI non accede direttamente ai database.

### TB-2 — Control Plane verso Runtime Cell

Il Control Plane distribuisce bundle immutabili, versionati e firmati. Il runtime verifica firma, scope, compatibilità, freshness e policy prima dell'attivazione. I worker non accettano comandi non autenticati e continuano con l'ultima configurazione valida se il canale è interrotto.

### TB-3 — Runtime verso sistemi sanitari

Ogni endpoint possiede identità, credenziali, rate limit e network policy proprie. I protocolli legacy non cifrati sono confinati in segmenti protetti o tunnel approvati; non vengono esposti su reti non fidate.

### TB-4 — Operatività verso analytics

Il passaggio avviene tramite proiezioni asincrone, dataset versionati e quality gate. I servizi analitici non scrivono nei sistemi clinici e non fanno parte dell'ACK path.

### TB-5 — Tenant e organization

Autorizzazione, routing, storage namespace, encryption context e audit includono lo scope. Dove il rischio lo richiede si usano database, bucket, chiavi, cluster o account separati, non soltanto filtri applicativi.

### TB-6 — Uso secondario

Permit, purpose, dataset e scadenza sono verificati prima dell'accesso. L'output attraversa controlli contro re-identificazione e disclosure. Il secure processing environment impedisce estrazioni non autorizzate.

### TB-7 — Monitoring e supporto

Telemetry e diagnostica sono trattate come un canale potenzialmente sensibile. Payload, identificativi diretti, token e segreti sono vietati per default; l'accesso temporaneo al raw è separato, motivato e auditato.

## 9. Flussi di alto livello

### 9.1 Uso primario sincrono

```text
Sorgente → Runtime Cell → validate/map/route → Destinazione
                 ├── durable raw + event state
                 ├── trace/audit/metrics
                 └── async projection event
```

La risposta al sorgente segue il contratto del flow. Un ACK positivo viene emesso solo dopo il durability point configurato.

### 9.2 Fan-out asincrono

```text
Sorgente → ingest durabile → topic/queue partizionata
                              ├── Destination A
                              ├── Destination B
                              ├── Canonical/Semantic
                              └── Analytics/OMOP
```

Ogni consumer ha retry, DLQ e SLO autonomi. Un consumer lento non blocca gli altri.

### 9.3 Configurazione e deployment

```text
Designer/Git → validate/test/review → signed release bundle
              → staged deployment → Runtime Cell → health gate → promote/rollback
```

### 9.4 Uso secondario

```text
Permit/Purpose → dataset selection → minimisation/pseudonymisation
               → secure processing environment → controlled output
```

## 10. Confini di responsabilità

HHC è responsabile di:

- integrità del trasporto e delle trasformazioni dichiarate;
- preservazione, versione e lineage degli artefatti HHC;
- applicazione delle policy tecniche configurate;
- evidenza di ricezione, elaborazione, consegna e accesso;
- isolamento e continuità dei propri componenti;
- rilevazione esplicita degli stati degradati.

HHC non è responsabile, da solo, di:

- correttezza clinica del dato sorgente;
- identità master o consenso se delegati a sistemi esterni;
- disponibilità e comportamento dei sistemi integrati;
- base giuridica e finalità decise dal titolare/HDAB;
- valore legale di firma, documento o conservazione non gestiti da moduli certificati;
- decisioni diagnostiche, terapeutiche o di triage.

## 11. Assunzioni e vincoli

- Le reti sanitarie possono essere intermittenti e contenere protocolli legacy.
- Alcune facility richiedono che la PHI non lasci il sito; il Control Plane deve funzionare senza payload.
- Un gruppo può usare identity provider, PKI e SIEM differenti per organization.
- Lo stesso concetto locale può avere significati diversi per facility e periodo: scope e validità sono obbligatori.
- Gli atti di esecuzione EHDS e i profili nazionali evolvono: gli adapter sono versionati e sostituibili.
- L'alta disponibilità end-to-end richiede HA anche delle dipendenze esterne; HHC distingue responsabilità e misura separatamente le cause.

## 12. Riferimenti collegati

- `../product/vision-and-scope.md`
- `../product/personas-and-use-cases.md`
- `containers.md`
- `components.md`
- `data-flow.md`
- `scalability-and-resilience.md`
- `data-architecture.md`
- `../security/security-architecture.md`
- `../deployment/deployment-topologies.md`
