# Visione e perimetro del prodotto

Stato: baseline di prodotto 1.0  
Data di riferimento: 1 settembre 2026  
Ambito: prodotto enterprise finale per il mercato sanitario europeo

## 1. Scopo del documento

Questo documento definisce identità, obiettivi, valore, perimetro e principi non negoziabili di HyperHealth Connect (HHC). È la fonte di orientamento per requisiti, architettura, roadmap, posizionamento e decisioni di build-versus-buy. I dettagli implementativi sono contenuti nei documenti di architettura e negli ADR; i requisiti verificabili sono in `requirements.md`.

## 2. Visione

HyperHealth Connect è una piattaforma enterprise di interoperabilità sanitaria che trasforma gli scambi tra applicazioni in un patrimonio di dati comprensibile, governato, tracciabile e riutilizzabile.

HHC combina tre funzioni:

1. **Healthcare Integration Fabric**: connette, valida, trasforma, instrada e sorveglia flussi sanitari sincroni e asincroni.
2. **Healthcare Semantic Event Hub**: preserva l'evento originale, ne esplicita il significato e registra mapping, terminologie, qualità, lineage e versioni.
3. **OMOP-native Health Data Platform**: proietta dati governati verso OMOP CDM e l'ecosistema OHDSI per ricerca, population health, analytics e impieghi AI controllati.

La promessa sintetica è: **Connect once. Understand once. Reuse everywhere.**

## 3. Problema affrontato

Le organizzazioni sanitarie europee operano ecosistemi eterogenei, stratificati e critici: EHR/HIS, LIS, RIS/PACS, sistemi di pronto soccorso, prescrizione, farmacia, dispositivi, registri, sistemi regionali e nazionali, piattaforme di ricerca e servizi transfrontalieri. Le integrazioni punto-punto duplicano trasformazioni e conoscenza, rendono opache le dipendenze, aumentano il rischio operativo e ostacolano il riuso sicuro del dato.

HHC riduce questa frammentazione mediante:

- connettori e pattern di integrazione riutilizzabili;
- contratti e configurazioni versionati;
- un envelope tecnico uniforme che non altera il payload clinico;
- raw event immutabili per audit, replay e reprocessing;
- un modello semantico canonico indipendente dalla destinazione;
- mapping tecnici e terminologici governati;
- osservabilità end-to-end e audit append-only;
- proiezioni operative e analitiche multiple;
- runtime distribuiti vicino ai sistemi e separati dal Control Plane.

## 4. Clienti e contesti di installazione

Il prodotto finale è destinato a:

- aziende sanitarie pubbliche e private;
- gruppi ospedalieri multi-azienda e multi-facility;
- regioni, reti territoriali e centrali di interoperabilità;
- laboratori e reti diagnostiche;
- istituti di ricerca, IRCCS e reti cliniche europee;
- fornitori di servizi gestiti e system integrator autorizzati;
- infrastrutture che preparano interoperabilità e riuso dei dati nel quadro EHDS.

Sono supportati deployment on-premises, cloud privato, cloud conforme alle policy del cliente, ibrido e multi-site. Kubernetes è la baseline enterprise, mentre la logica del prodotto resta portabile e può essere distribuita anche tramite container senza Kubernetes per contesti controllati.

## 5. Modello organizzativo

HHC usa la seguente gerarchia condivisa da prodotto, sicurezza e architettura:

```text
Tenant
└── Organization
    ├── Facility
    │   ├── Application
    │   └── Endpoint
    └── Facility
        ├── Application
        └── Endpoint
```

- **Tenant**: massimo confine contrattuale, amministrativo e di isolamento.
- **Organization**: soggetto giuridico o organizzativo che governa strutture e dati.
- **Facility**: ospedale, presidio, laboratorio, ambulatorio, farmacia o altro luogo operativo.
- **Application**: sistema informativo o servizio identificato.
- **Endpoint**: istanza tecnica raggiungibile tramite protocollo e credenziali specifici.
- **Runtime Cell**: unità tecnica isolata di esecuzione assegnata a uno o più ambiti autorizzati; non coincide necessariamente con una facility.

Ogni asset, evento, autorizzazione, deployment, mapping e record di audit deve essere attribuibile a questa gerarchia senza affidarsi a convenzioni implicite.

## 6. Proposta di valore

### 6.1 Per il management sanitario

- riduzione del costo marginale delle integrazioni successive;
- maggiore continuità dei processi clinici e amministrativi;
- visibilità misurabile su qualità, capacità, rischi e dipendenze;
- base dati riutilizzabile per governo clinico, ricerca e programmazione;
- percorso graduale verso requisiti europei senza sostituire in blocco i sistemi esistenti.

### 6.2 Per i team di integrazione

- connector SDK stabile e catalogo di connettori;
- flow dichiarativi, versionati, testabili e promuovibili fra ambienti;
- mapping riusabili e sottoposti a review;
- timeline completa dell'evento e strumenti di replay controllato;
- riduzione di script opachi e configurazioni non esportabili.

### 6.3 Per data governance e ricerca

- lineage dal payload originale alla proiezione analitica;
- terminologie e mapping con snapshot e provenance;
- dataset OMOP versionati con quality gate;
- coorti e feature riproducibili;
- accesso secondario governato, minimizzato e separato dai sistemi clinici operativi.

### 6.4 Per sicurezza e operations

- identity federation, least privilege, mTLS e gestione del ciclo di vita dei segreti;
- audit append-only resistente alle manomissioni;
- metriche, log e trace correlati con minimizzazione della PHI;
- architettura fault-tolerant, store-and-forward e procedure di disaster recovery verificabili;
- SBOM, gestione vulnerabilità e tracciabilità della supply chain software.

## 7. Principi di prodotto

1. **Patient safety prima della convenienza tecnica.** Un errore non deve diventare silenziosamente un dato clinico valido.
2. **Nessun evento accettato senza identità e tracciabilità.** Correlation ID, tenant, sorgente, versione del flow e stato sono obbligatori.
3. **Raw immutabile, derivati versionati.** Ogni reinterpretazione deve poter essere spiegata e, quando consentito, ripetuta.
4. **FHIR e OMOP hanno ruoli distinti.** FHIR è centrale per interoperabilità; OMOP è una proiezione analitica; nessuno dei due è il modello interno universale.
5. **Control Plane e Data Plane sono separati.** La perdita temporanea della console non deve arrestare i flow già distribuiti.
6. **Configurazione come codice.** La UI modifica definizioni dichiarative esportabili, confrontabili, validabili e firmabili.
7. **Security, privacy e observability by design.** Sono requisiti del primo incremento, non hardening tardivo.
8. **Località e sovranità del dato.** I payload possono restare nella facility o nel Paese richiesto mentre la governance è federata.
9. **Scalabilità per celle indipendenti.** La crescita avviene aggiungendo worker e celle senza allargare inutilmente il failure domain.
10. **Degrado controllato.** Back-pressure, code persistenti e circuit breaker proteggono i sistemi clinici durante i guasti.
11. **Standard aperti e sostituibilità.** Le dipendenze sono isolate dietro contratti HHC e sottoposte a policy di licenza e aggiornamento.
12. **Nessuna pretesa normativa implicita.** La conformità dipende da configurazione, intended purpose, deployment, contratti e diritto nazionale.

## 8. Capability del prodotto finale

### 8.1 Integrazione operativa

- HL7 v2/MLLP, FHIR REST e Bulk Data, REST, SOAP, file/SFTP, JDBC e messaging/eventi;
- CDA e profili IHE documentali e di patient identity;
- DICOM e DICOMweb per orchestrazione e metadati, senza trasformare HHC in PACS;
- routing, filtering, orchestration, enrichment e trasformazioni sincrone/asincrone;
- ACK/response, retry, DLQ, deduplica, ordering configurabile e riconciliazione.

### 8.2 Governance e operatività

- registry di connector, flow, mapping, contratti, schemi e terminologie;
- designer visuale sopra definizioni dichiarative;
- promozione, canary, rollback e configurazioni firmate;
- Message Explorer con accesso granulare e masking;
- replay di consegna, trasformazione, semantica e rebuild OMOP;
- catalogo tecnico e semantico con owner e lineage.

### 8.3 Semantic data platform

- Canonical Semantic Event Model versionato;
- Semantic Mapping Registry e workflow di stewardship;
- vocabulary service e concetti locali governati;
- regole di qualità strutturale e semantica;
- proiezioni FHIR, OMOP, data lake/warehouse, API e flussi regolatori.

### 8.4 Analytics e riuso

- OMOP CDM e compatibility layer OHDSI;
- dataset versionati e separati per organizzazione/uso;
- data quality e characterization;
- concept set, coorti, feature e analytics riproducibili;
- API governate per BI, ricerca e agenti AI.

### 8.5 Enterprise platform

- multi-tenancy, multi-organization, multi-facility e multi-CDM;
- worker pool distribuiti, isolamento per runtime cell e policy di data locality;
- HA multi-zone, DR multi-site e continuità anche con Control Plane indisponibile;
- audit completo, monitoraggio continuo e integrazione SIEM/SOC;
- gestione lifecycle, upgrade a basso downtime e supportabilità di lungo periodo.

## 9. Perimetro negativo

HHC non è:

- un EHR/HIS o la cartella clinica ufficiale;
- il master anagrafico universale o un MPI obbligatorio;
- un PACS, VNA diagnostico o viewer certificato;
- un motore autonomo di decisione clinica;
- un sostituto automatico di un terminology server, di un consent service o di un data access body;
- un archivio legale di documenti salvo modulo, configurazione e validazione dedicate;
- una garanzia automatica di conformità GDPR, EHDS, NIS2, MDR/IVDR, AI Act o alle leggi nazionali;
- una piattaforma che consente accesso indiscriminato ai dati clinici per analytics o AI.

Funzioni diagnostiche, terapeutiche, di triage o decision support non rientrano nella baseline. Se introdotte, richiedono valutazione formale dell'intended purpose e dell'eventuale qualificazione come medical device software ai sensi del MDR/IVDR, oltre all'analisi dell'AI Act se pertinente.

## 10. Posizionamento europeo nel 2026

Il Regolamento EHDS è entrato in vigore il 26 marzo 2025, si applica in via generale dal 26 marzo 2027 e prevede tappe operative successive: dal 2029 per patient summary ed ePrescription/eDispensation e per gran parte dell'uso secondario; dal 2031 per immagini, referti di laboratorio e lettere di dimissione. Nel 2026 HHC deve quindi essere progettato per adattarsi agli atti di esecuzione e all'European Electronic Health Record Exchange Format, evitando di congelare contratti non ancora definitivi.

Il prodotto deve supportare:

- diritti di accesso, rettifica, restrizione e trasparenza configurabili;
- logging granulare e verificabile degli accessi;
- separazione fra uso primario e secondario;
- data minimisation, pseudonimizzazione e secure processing environment per il riuso;
- interoperabilità con nodi nazionali e servizi MyHealth@EU tramite adapter certificabili;
- preparazione di dataset e cataloghi per HealthData@EU e Health Data Access Bodies;
- requisiti di cyber risk management e incident response coerenti con NIS2 e legislazioni nazionali;
- secure-by-design e vulnerability handling coerenti con il Cyber Resilience Act.

## 11. Obiettivi misurabili di successo

Il prodotto finale è considerato riuscito quando:

- un gruppo multi-azienda può governare centralmente più facility mantenendo isolamento e data locality;
- un evento clinico può essere seguito dalla ricezione a ogni consegna o proiezione, con versioni e decisioni ricostruibili;
- i flow critici continuano durante l'indisponibilità temporanea del Control Plane;
- capacity e alta disponibilità possono crescere orizzontalmente senza riprogettazione del dominio;
- restore, failover e ritorno al sito primario sono testati e producono evidenze;
- mapping e connettori vengono riusati senza duplicare conoscenza non governata;
- dataset analitici sono rilasciati solo dopo quality gate e restano separati dall'operatività clinica;
- accessi privilegiati, replay e modifiche sono attribuibili, motivati e rilevabili dal monitoraggio;
- gli aggiornamenti dichiarano compatibilità, migration path, rollback e impatto sui dati.

## 12. Allineamento a standard enterprise

L'architettura e il processo di sviluppo devono produrre evidenze mappabili agli standard seguenti, nella versione applicabile alla release e al contratto:

- **ISO/IEC 27001:2022** per il sistema di gestione della sicurezza delle informazioni;
- **ISO/IEC 27701:2025** per il privacy information management system;
- **ISO 27799:2025** per i controlli di sicurezza specifici delle informazioni sanitarie;
- **ISO 22301:2019/Amd 1:2024** per business continuity management;
- **ISO 27789:2021**, o successore pubblicato, per audit trail degli EHR;
- **IEC 80001-1:2021**, o successore, per la gestione del rischio nelle reti che connettono health software e dispositivi;
- **IEC 81001-5-1:2021**, o successore, per il secure product lifecycle del software sanitario.

L'allineamento significa che requisiti, controlli, test e artefatti possono essere mappati e auditati. Non equivale a certificazione del prodotto o dell'organizzazione, che richiede scope e assessment formali.

## 13. Fonti normative, istituzionali e standard

- [Regolamento (UE) 2025/327 — European Health Data Space](https://eur-lex.europa.eu/eli/reg/2025/327/oj/)
- [Commissione europea — EHDS e calendario di applicazione](https://health.ec.europa.eu/ehealth-digital-health-and-care/european-health-data-space-regulation-ehds_en)
- [Regolamento (UE) 2016/679 — GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
- [Direttiva (UE) 2022/2555 — NIS2](https://eur-lex.europa.eu/eli/dir/2022/2555/oj)
- [Regolamento (UE) 2024/2847 — Cyber Resilience Act](https://eur-lex.europa.eu/eli/reg/2024/2847/oj)
- [Regolamento (UE) 2017/745 — Medical Device Regulation](https://eur-lex.europa.eu/eli/reg/2017/745/oj)
- [Regolamento (UE) 2024/1689 — Artificial Intelligence Act](https://eur-lex.europa.eu/eli/reg/2024/1689/oj)
- [Commissione europea — riuso dei dati sanitari](https://health.ec.europa.eu/ehealth-digital-health-and-care/reuse-health-data_en)
- [ISO/IEC 27001:2022](https://www.iso.org/standard/27001)
- [ISO/IEC 27701:2025](https://www.iso.org/standard/27701)
- [ISO 27799:2025](https://www.iso.org/standard/84647.html)
- [ISO 22301:2019](https://www.iso.org/standard/75106.html)
- [ISO 27789:2021](https://www.iso.org/standard/75313.html)
- [IEC 80001-1:2021](https://www.iso.org/standard/72026.html)
- [IEC 81001-5-1:2021](https://www.iso.org/standard/76097.html)

Questi riferimenti definiscono il contesto, non costituiscono parere legale né attestazione di conformità.
