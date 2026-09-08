# Valutazione del materiale sorgente provvisorio

| Campo | Valore |
|---|---|
| Stato | Baseline di provenance 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Architecture Governance |
| Approvatori | Product, Architecture, Clinical Informatics, Data Platform |

## 1. Scopo e conclusione

I cinque documenti nella cartella `DOCUMENTAZIONE DI PROGETTO` sono materiale di ideazione e fondazione. Hanno fornito una direzione coerente, ma non sono specifiche normative né prova di conformità. Le decisioni adottate sono ora espresse negli ADR e nei documenti tematici sotto `docs/`; in caso di conflitto prevale la [governance documentale](../governance/documentation-governance.md).

La valutazione conferma come invarianti: separazione Control Plane e Data Plane, runtime locale resiliente, raw immutabile, Integration Envelope, canonical semantic layer non vincolato a FHIR o OMOP, mapping e terminology governati, OMOP come proiezione analitica, configuration as code, osservabilità e replay nativi. Le indicazioni su prodotti o versioni restano soggette alla policy dipendenze e a verifica per release.

## 2. Inventario e integrità

Valori calcolati sul materiale presente il 5 settembre 2026:

| File | Dimensione | SHA-256 |
|---|---:|---|
| `18-punti-omop.txt` | 22.462 byte | `c0c64bd0f5426a66c9540a3d455529205241c95ac3aee6659a9fa0e30734be5b` |
| `Connect-once.-Understand-once.-Reuse-everywhere.txt` | 15.697 byte | `83fd68c6f3f2936e2d2d7b76f19649dcbf1f82a1138513b555f547d045148d7a` |
| `connector + omop.odt` | 51.687 byte | `7d28a1d5d3b484c7a7070f17ee5a5bd77d5836d87fcfa3e191e487ed4b3aee95` |
| `Hyper-Health-Connector.txt` | 6.445 byte | `73d073bb6344622305ec98e37840e4cf0bec914c7829a4903a844936c5ce7381` |
| `HyperHealth_Connect_Architecture_Foundation_v0.1.docx` | 50.263 byte | `95894fcb8390465c11d7cfa0d9b4d1489be496a98458600171e64c646069328f` |

Gli hash servono a identificare la baseline valutata, non a firmarne autenticità o approvazione. I formati DOCX e ODT sono stati letti come contenuto strutturato; nessun file sorgente è stato modificato.

## 3. Valutazione per documento

### 18 punti OMOP

Il testo esplora CDM, vocabulary, ETL, quality, cohort, feature e OHDSI. Il contributo principale è la separazione `raw → canonical → OMOP` e l'idea di un OMOP Schema Registry metadata-driven. Ha correttamente evidenziato che OMOP è un modello analitico, non il formato operativo universale.

Limiti: include claim temporali su versioni e compatibilità, numeri indicativi sui controlli DQD e valutazioni su stack di progetti. Tali claim non sono importati come requisiti senza verifica ufficiale. Le decisioni consolidate sono in [OMOP architecture](../omop/omop-architecture.md), [projection ed ETL](../omop/omop-projection-and-etl.md) e ADR-013, ADR-014, ADR-019, ADR-020, ADR-022 e ADR-024.

### Connect once Understand once Reuse everywhere

Il testo definisce la tesi di prodotto: trasformare conoscenza di connector, mapping e terminologie in asset cumulativo. È valido come narrativa strategica e ha guidato [vision and scope](../product/vision-and-scope.md).

Limiti: slogan, moat e benefici non costituiscono automaticamente requisiti misurabili. Il riuso è ammesso soltanto con separazione tenant, provenance, licenze e validazione locale; non si assume che un mapping clinico sia universalmente riusabile.

### connector più OMOP

Il documento ODT estende la foundation fino a semantic event hub, multi-CDM, replay, quality, analytics e AI governata. Ha originato molti dei 24 ADR e la tassonomia dei componenti.

Limiti: combina visione, architettura e proposta di stack in un unico testo; alcuni diagrammi e versioni sono indicativi; alcuni componenti OHDSI sono descritti come base potenziale senza fissare maturità, licenza o support matrix. La documentazione corrente separa questi aspetti in domini e gate autonomi.

### Hyper Health Connector descrizione commerciale

Il testo chiarisce posizionamento e differenziazione. È utile per linguaggio di prodotto, ma non definisce SLO, conformance, sicurezza, privacy o accettazione clinica. Le promesse sono state tradotte in requisiti verificabili nei documenti product e roadmap.

### Architecture Foundation v0.1

Il DOCX fornisce 17 decisioni fondative, una roadmap a quattro fasi, confini del prodotto, stack candidato e primi dieci ADR. È la fonte più vicina a una baseline iniziale e ha guidato la struttura del repository documentale.

Limiti: è esplicitamente una foundation 0.1; non dettaglia abbastanza multi-tenancy, HA e DR, audit, privacy europea, data quality, connector contract e testing agentic. Le aree sono ora disciplinate nei documenti dedicati e negli ADR 011-024.

## 4. Decisioni recepite

| Principio sorgente | Stato corrente | Autorità interna |
|---|---|---|
| Control Plane separato dal Data Plane | adottato | ADR-001 e architecture |
| Monolite modulare prima dei microservizi | adottato con criteri di estrazione | ADR-002 e engineering practices |
| Connector SDK come boundary | adottato | ADR-003 e connector SDK |
| Raw originale immutabile | adottato con retention governata | ADR-004 e ADR-023 |
| FHIR non universale | adottato | ADR-005 e standard FHIR |
| Percorsi sincrono e asincrono | adottato | ADR-006 |
| Configuration as Code | adottato | ADR-007 e configuration policy |
| Storage specializzato e telemetria senza PHI | adottato | ADR-008 |
| Gerarchia tenant e facility | adottato e formalizzato | ADR-009 e tenancy |
| Compatibilità N e N-1 | adottato | ADR-010 |
| Canonical Semantic Event | adottato | ADR-011 |
| Mapping e terminologie governati | adottato | ADR-012, ADR-015 e semantic docs |
| OMOP come proiezione | adottato | ADR-013 e ADR-014 |
| Lineage e replay deterministico | adottato per classi | ADR-016 e ADR-017 |
| Pseudonimizzazione al boundary | adottato | ADR-018 e privacy |
| OHDSI come compatibility layer | adottato | ADR-019 |
| Analytics e AI isolati e governati | adottato | ADR-020 e ADR-021 |
| Multi-CDM e DQ gate | adottato | ADR-022 e ADR-024 |

## 5. Chiarimenti introdotti

### Nessun componente obbligatorio senza release decision

I nomi di tecnologie nella foundation descrivono una baseline candidata. La release pinna versione, digest, licenza e matrice testata secondo [dependency policy](../development/dependency-and-license-policy.md). Un broker Kafka-compatible o storage S3-compatible può essere sostituito senza cambiare il dominio.

### Nessun exactly once generico

La promessa è stata sostituita da at-least-once, idempotenza, deduplica e riconciliazione con stato `UNKNOWN` esplicito. L'ACK dipende dal durability point del flow.

### FHIR e OMOP hanno lifecycle distinti

FHIR è protocollo e possibile rappresentazione operativa; OMOP è proiezione analitica; il canonical model è il punto di stabilità interno. Nessuno dei tre viene usato per nascondere la semantica originaria.

### La centralizzazione non implica transito della PHI

Il Control Plane può governare Runtime Cell in facility diverse senza ricevere payload clinici. Telemetria, audit e status sono minimizzati e il runtime opera 24 ore con last-known-good quando il Control Plane è indisponibile.

### Il riuso semantico è controllato

Mapping e terminology package richiedono contesto, scope, versione, provenance e approvazione. Il riuso cross-tenant non è implicito e può essere limitato da licenze o differenze cliniche locali.

## 6. Assunzioni ritirate o ridimensionate

- Kubernetes non è requisito funzionale: è una topologia enterprise supportata.
- Kafka non è nel fast path di ogni flow.
- Keycloak, Orthanc, ATLAS e WebAPI non sono core insostituibili.
- Il numero di controlli DQD non è un requisito fisso; conta l'esito della versione pin-nata e delle regole HHC.
- La disponibilità di un progetto open source non ne garantisce supporto, licenza o fitness.
- La generazione OMOP non termina con una `INSERT`: richiede validazione, quality gate, lineage e stato dataset.
- Un agente AI non riceve SQL o accesso clinico arbitrario: usa API e policy governate.
- Nessun documento fondativo autorizza uso di PHI reale in sviluppo o test.

## 7. Decisioni ancora specifiche di release o cliente

Non sono lacune documentali; richiedono input contestuale e registrazione controllata:

- versioni e distribuzioni concrete dei componenti;
- volume per facility, burst, payload, latency SLO e capacity envelope;
- paese, base giuridica, ruoli GDPR, retention e trasferimenti;
- profili FHIR, IHE, CDA, DICOM e flussi nazionali richiesti;
- terminologie con entitlement e release nazionali;
- RTO e RPO contrattuali se più severi della baseline;
- topologia shared, cell, dedicated, sovereign o air-gapped;
- target DBMS OMOP e tool OHDSI supportati;
- classificazione clinica del prodotto e applicabilità di MDR o altra normativa;
- support window e SLA del cliente.

Tali valori entrano nella matrice di applicabilità, nel solution design e nei test di accettazione, senza forcare le decisioni core.

## 8. Metodo di verifica delle fonti

Per trasformare un claim sorgente in baseline:

1. individuare standard, regolamento, repository o manuale ufficiale;
2. registrare versione, data, status e scope;
3. distinguere requisito normativo, guida e inferenza architetturale;
4. confrontare implementazione e compatibility matrix;
5. verificare licenza, release lifecycle e security channel;
6. scrivere criterio testabile e owner;
7. collegare ADR, test ed evidence;
8. riesaminare alla cadenza definita dalla governance.

Le URL con parametri di tracking presenti nei testi iniziali non sono propagate; i documenti correnti puntano alle fonti ufficiali pulite.

## 9. Copertura documentale risultante

Il corpus definitivo di progettazione separa:

- prodotto, persona, requisiti e use case europei;
- contesto, container, componenti, dati, scalabilità e resilienza;
- ADR per le 24 decisioni fondative;
- standard sanitari, canonical model, mapping, semantic e OMOP;
- API e connector, che costituiscono il core esecutivo;
- security, privacy, tenancy e authorization;
- deployment, HA, business continuity, disaster recovery e operations;
- engineering, configuration, supply chain e testing agentic;
- roadmap, release plan, risk e governance documentale.

Questa separazione riduce conflitti, assegna owner e consente review mirate senza perdere la tracciabilità verso la visione originaria.

## 10. Casi d'uso della provenance

### Contestazione di una decisione

Un reviewer chiede perché OMOP non sia il modello universale. L'assessment identifica la provenienza; ADR-013 espone contesto, alternative e conseguenze; la modifica passa da un nuovo ADR e non da editing retroattivo del sorgente.

### Aggiornamento di una dipendenza

Una release ufficiale cambia compatibilità. Il team non modifica l'assessment storico: aggiorna matrice e dependency record, ripete test e cita la nuova fonte nel documento tematico.

### Audit cliente

L'auditor può ricostruire il passaggio da materiale 0.1 a requisito, ADR, implementazione e test. Hash e date identificano la baseline, mentre firme ed evidence di release dimostrano ciò che è stato effettivamente distribuito.

### Nuovo requisito europeo

Una norma o linea guida successiva non viene attribuita ai documenti originari. È verificata su fonte primaria, valutata per applicabilità e integrata con change tracciata e owner competente.

## 11. Criteri di chiusura

La trasformazione del materiale provvisorio è completa quando:

- ogni decisione strutturale è accettata, respinta o resa esplicitamente contestuale;
- non rimangono documenti schema o placeholder nella baseline attiva;
- claim variabili sono verificati o marcati come decisione di release;
- requisiti critici hanno owner, test ed evidence attesa;
- link, versioni, SLO, RTO e RPO sono coerenti nel corpus;
- il materiale iniziale rimane immutato e identificabile dagli hash.

## 12. Fonti ufficiali di riferimento

Consultate o ricontrollate il 5 settembre 2026; le fonti specifiche di ciascun dominio sono riportate nei documenti relativi:

- [HL7 FHIR specification](https://hl7.org/fhir/)
- [DICOM Standard](https://www.dicomstandard.org/current)
- [IHE Technical Frameworks](https://profiles.ihe.net/)
- [OHDSI Common Data Model](https://ohdsi.github.io/CommonDataModel/)
- [OpenTelemetry specification](https://opentelemetry.io/docs/specs/otel/)
- [Regolamento UE 2025/327 European Health Data Space](https://eur-lex.europa.eu/eli/reg/2025/327/oj)
- [Regolamento UE 2016/679 GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
