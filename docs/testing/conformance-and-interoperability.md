# Conformità e interoperabilità

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ambito | Prodotto finale HHC, installazioni multiazienda e multifacility |
| Ultimo aggiornamento | 4 settembre 2026 |
| Responsabili | Architecture, Interoperability, Clinical Safety, Security, QA |
| Revisione minima | A ogni release, variazione normativa, nuova versione di standard o nuovo profilo di integrazione |

## 1. Finalità

Questo documento definisce come HHC dimostra che un'interfaccia è conforme al contratto dichiarato e interoperabile nel contesto clinico reale. La conformità sintattica è necessaria ma non sufficiente: una risorsa FHIR valida, un messaggio HL7 v2 accettato o un oggetto DICOM formalmente corretto possono produrre esiti clinici errati se profilo, semantica, identità, workflow, gestione degli errori o assunzioni del partner non coincidono.

Il processo deve produrre evidenze ripetibili e auditabili per:

- standard, versione, profilo, attore, transazione e opzioni effettivamente supportati;
- comportamento positivo, negativo, degradato e di recupero;
- correttezza semantica e preservazione del significato clinico;
- compatibilità con sistemi partner, dispositivi e infrastrutture nazionali;
- sicurezza, isolamento tra tenant e protezione dei dati sanitari;
- prestazioni e resilienza entro i limiti qualificati;
- limiti, deviazioni, dipendenze e funzionalità non supportate.

Le prescrizioni della [strategia di test](./test-strategy.md), della [politica agentic coding](./agentic-coding-quality-policy.md) e della [toolchain qualificata](./test-toolchain-and-environments.md) sono vincolanti anche per questo documento.

## 2. Principi non negoziabili

1. **Nessuna equivalenza impropria.** Conformità a uno schema, superamento di un validator, partecipazione a un Connectathon o compatibilità con un singolo partner non equivalgono a certificazione complessiva del prodotto.
2. **Versioni esplicite e immutabili.** Ogni prova identifica versione dello standard, Implementation Guide, package, profilo, vocabolario, tool e digest degli artefatti.
3. **Comportamento oltre la struttura.** La qualificazione comprende sequenze, stati, errori, retry, timeout, idempotenza, autorizzazione e recupero, non soltanto file statici.
4. **Semantica preservata.** Nessuna trasformazione può inventare, perdere o rendere più certa un'informazione clinica senza una regola approvata e tracciata.
5. **Partner-specifico senza contaminare il core.** Eccezioni locali e segmenti proprietari sono profili configurati e versionati, non modifiche implicite al modello canonico.
6. **Claim verificabili.** Una capability può essere pubblicizzata soltanto se esiste un pacchetto di evidenze valido per la release distribuita.
7. **Regressione automatica.** Ogni difetto di interoperabilità risolto genera un caso di regressione anonimizzato o sintetico.
8. **Oracle indipendente dall'agente.** Un agente può generare adapter, fixture e test, ma non può essere l'unica fonte di profilo, aspettativa clinica o claim di conformità.
8. **Fail closed quando la sicurezza è incerta; fail safe per il workflow clinico.** Il comportamento concreto è definito per caso d'uso, sottoposto a valutazione di rischio clinico e mai lasciato all'implementazione casuale.

## 3. Tassonomia delle dichiarazioni

Ogni matrice di compatibilità usa esclusivamente i seguenti stati:

| Stato | Significato | Uso esterno consentito |
|---|---|---|
| `qualified` | Tutti i gate applicabili sono superati sulla release e sulla configurazione dichiarate | Sì, riportando perimetro e limiti |
| `qualified-with-limitations` | I gate sono superati entro limiti documentati, con rischio residuo accettato | Sì, rendendo i limiti visibili |
| `experimental` | Implementazione disponibile ma evidenza incompleta o standard non stabile | Solo ambienti non produttivi o pilot controllati |
| `deprecated` | Supporto ancora presente durante una finestra di migrazione | Sì, con data di ritiro e percorso di sostituzione |
| `unsupported` | Capability non implementata o non qualificata | No |

Sono vietate espressioni generiche quali “FHIR compliant”, “DICOM compatible” o “IHE certified” senza versione, profilo, ruolo, opzioni e prova associata.

## 4. Unità di qualificazione: Conformance Pack

Ogni capability è descritta da un `Conformance Pack` versionato. Il manifest, in formato elaborabile automaticamente, contiene almeno:

```yaml
id: hhc-fhir-r4-patient-read-1
product_release: 1.0.0
status: qualified
standard:
  family: HL7 FHIR
  version: 4.0.1
profile_or_ig:
  canonical: https://example.eu/fhir/ImplementationGuide/example
  package: example.fhir.ig
  package_version: 1.2.0
  package_digest: sha256:...
role: server
capabilities:
  - Patient.read
  - Patient.search
options: []
terminology_snapshot: hhc-eu-2026-09
test_suite_version: 3.4.0
validator_versions:
  - name: HL7 FHIR Validator
    version: pinned-in-build
environment: qualification-eu-01
partner_matrix: []
limitations: []
deviations: []
evidence_uri: evidence://qualification/...
approved_by:
  - interoperability-owner
  - clinical-safety-owner
  - security-owner
executed_at: 2026-09-01T00:00:00Z
expires_at: 2027-03-01T00:00:00Z
```

Il digest deve coprire manifest, profili, schema, value set, mapping, fixture, configurazione del test runner e risultati. La firma della release e la provenienza degli artefatti seguono i controlli della supply chain definiti nella strategia di test.

## 5. Livelli obbligatori di qualificazione

| Livello | Obiettivo | Evidenza minima |
|---|---|---|
| L1 — artefatto | Sintassi, schema, cardinalità, invarianti, terminologia computabile | report validator e fixture positive/negative |
| L2 — contratto | Operazioni, sequenze, codici di errore, retry, timeout, idempotenza | test automatici di protocollo e contract diff |
| L3 — semantica | Significato clinico, unità, identità, temporalità, negazione, provenienza | golden dataset e revisione clinico-semantica |
| L4 — partner | Compatibilità con prodotti e configurazioni partner rappresentative | sessione bilaterale o laboratorio neutrale, log correlati |
| L5 — operativo | Sicurezza, prestazioni, resilienza, osservabilità e recupero | test non funzionali e runbook esercitato |
| L6 — produzione controllata | Verifica post-deploy senza dati o azioni cliniche rischiose | synthetic probe, canary, monitoraggio e rollback verificato |

Una capability è `qualified` solo se supera tutti i livelli applicabili. Gli elementi non computabili di una Implementation Guide richiedono revisione umana formalizzata: il validator non è l'arbitro finale della conformità d'uso.

## 6. Flusso di qualificazione

1. Registrare il bisogno, il workflow clinico e i sistemi coinvolti.
2. Selezionare standard, versione e profilo con stato ufficiale adeguato.
3. Identificare attori, transazioni, opzioni, binding terminologici e requisiti nazionali.
4. Modellare contratto, threat model, rischio clinico e failure semantics.
5. Creare fixture sintetiche positive, di confine, negative e malevole.
6. Eseguire L1–L3 nella pipeline e L4–L5 nell'ambiente di qualificazione.
7. Registrare limitazioni, deviazioni motivate e rischio residuo.
8. Approvare e firmare il Conformance Pack.
9. Eseguire canary e verifica post-deploy.
10. Sorvegliare drift, scadenze, nuove versioni e regressioni del partner.

Una modifica a mapping, terminologia, profilo, libreria di parsing, configurazione TLS, identity cross-reference o comportamento di ACK invalida automaticamente le prove impattate.

### 6.1 Modifiche generate o proposte da agenti

Per una modifica A3 a parser, profilo, mapping, terminologia, canonical projection o ACK:

- l'Agentic Change Manifest identifica fonti ufficiali, package/digest, perimetro e strumenti;
- lo standard o profilo pinnato prevale su esempi generati e memoria del modello;
- fixture dello stesso agente sono integrate con golden corpus posseduto da Interoperability/Clinical Safety;
- validator ufficiale o reference implementation qualificata produce output raw conservato;
- mutation, differential o metamorphic test dimostra che l'oracle rileva la classe di errore;
- l'agente non può promuovere un mapping da `PROPOSED` ad `APPROVED` né ampliare un claim;
- ogni correzione automatica proposta dal validator è sottoposta a semantic diff e review;
- testo ostile dentro payload, narrative, extension o documento è trattato come dato e non come istruzione all'agente.

Se implementation guide, validator e partner divergono, il caso resta non qualificato finché un owner umano non documenta profilo locale, rischio e decisione. Non si addestra il test sul comportamento errato del partner senza rendere l'eccezione esplicita e isolata.

## 7. HL7 v2

La baseline di progetto supporta integrazioni HL7 v2 da 2.3 a 2.9, ma la singola interfaccia dichiara una versione esatta. La disponibilità di strutture predefinite in una libreria non prova il supporto dell'intero standard: per esempio, la documentazione HAPI pubblicata alla data di aggiornamento elenca strutture fino a HL7 v2.8.1. Versioni successive o profili locali richiedono modelli e prove dedicati.

### 7.1 Profilo di messaggistica

Per ogni flusso devono essere fissati:

- versione, message type, trigger event e message structure;
- segmenti, gruppi, cardinalità, datatype, usage e lunghezze;
- delimitatori, escape, charset, timezone e precisione temporale;
- codifiche, tabelle, identifier namespace e assigning authority;
- segmenti `Z`, estensioni locali e regole di compatibilità;
- MLLP framing, connessione, timeout, keepalive e dimensione massima;
- modalità di ACK, `MSH-15`, `MSH-16`, codici `AA/AE/AR` o `CA/CE/CR` e correlazione tramite `MSH-10`/`MSA-2`;
- ordine degli eventi, duplicati, messaggi tardivi, retry e riconciliazione;
- comportamento in assenza o indisponibilità del sistema destinatario.

### 7.2 Suite minima

- messaggio nominale per ciascun trigger supportato;
- campi opzionali assenti, ripetizioni al limite e valori massimi;
- delimitatori non predefiniti, escape e caratteri Unicode ammessi;
- versione, struttura, evento o profilo non supportati;
- campo richiesto assente, datatype errato, codice fuori value set;
- identifier ambiguo, conflitto di assigning authority, merge/unmerge;
- ACK positivo, errore applicativo, rifiuto, ACK tardivo, perso o duplicato;
- disconnessione prima e dopo l'ACK durevole;
- replay identico, stesso controllo con payload diverso e riordino;
- protezione da payload sovradimensionato, nesting patologico e injection;
- throughput, burst, backpressure e recovery senza perdita silenziosa.

Per ogni messaggio si verificano raw immutabile, envelope, hash, tenant/facility, stato del lifecycle, provenienza e risultato della trasformazione. L'ACK positivo può essere emesso solo al punto di durabilità definito dal contratto; non può dipendere dall'ETL OMOP.

## 8. HL7 FHIR

HHC qualifica separatamente R4, R4B e R5. Non si assume compatibilità automatica tra versioni. FHIR R6, se ancora non pubblicato come release normativa stabile per il profilo richiesto, resta sperimentale e non può essere baseline produttiva.

### 8.1 Dipendenze e validazione

Ogni test pinna:

- versione core e package canonico della Implementation Guide;
- dipendenze transitive e relativo digest;
- snapshot dei `StructureDefinition`, `ValueSet`, `CodeSystem`, `ConceptMap` e `SearchParameter`;
- validator e terminology server;
- regole nazionali o europee applicabili.

La suite verifica almeno:

- validità JSON/XML e FHIR core;
- profili dichiarati in `meta.profile` e profili richiesti dal contesto;
- cardinalità, slicing, invarianti FHIRPath e reference target;
- binding terminologico, display, versioni e sistemi canonici;
- reference interne/esterne, contenute, condizionali e non risolte;
- narrative obbligatoria e coerenza tra parte testuale e strutturata;
- estensioni note, modifier extension e rifiuto sicuro delle semantiche ignote;
- bundle links, `fullUrl`, order independence, atomicità di transaction e partial failure di batch.

Il superamento del validator copre solo requisiti computabili. Ogni IG deve avere anche una checklist umana degli obblighi testuali, degli esempi normativi e del workflow.

### 8.2 Comportamento server e client

Il `CapabilityStatement` pubblicato deve essere generato o verificato contro il comportamento reale. Si testano, ove dichiarati:

- read, vread, history, search, create, update, patch, delete e conditional interaction;
- parametri di ricerca, modifier, chaining, reverse chaining, include/revinclude e paginazione;
- ETag, `If-Match`, `If-None-Exist`, concorrenza e conflitti;
- transaction, batch, document, message e collection bundle;
- `OperationOutcome` per errore sintattico, semantico, autorizzativo e temporaneo;
- compartment, consent, scope SMART e autorizzazione a livello di risorsa;
- export/import bulk e subscription soltanto quando dichiarati;
- rate limit, retry sicuro, timeout, backpressure e idempotency policy.

La suite di sicurezza prova accesso cross-tenant, IDOR/BOLA, mass assignment, query costose, enumeration, SSRF tramite reference/URL, upload malevoli e disclosure negli errori.

## 9. IHE e CDA

### 9.1 Profili IHE

Il catalogo distingue sempre profili **Final Text** da **Trial Implementation**. I profili trial possono essere usati in pilot controllati, con rischio e strategia di migrazione espliciti; non sono presentati come equivalenti ai profili finali.

Per ogni profilo IHE si registra:

- dominio, revisione del Technical Framework e data;
- attore interpretato da HHC e attori partner;
- transazioni, opzioni e grouped actors;
- trasporto, audit, identificazione, terminologia e dipendenze;
- casi positivi, eccezioni e fault previsti dal profilo.

La matrice può includere, se richiesti dal caso d'uso, XDS.b, XCA, XCPD, PIX/PIXm, PDQ/PDQm, MHD, ATNA e altri profili, ma nessuno è implicitamente supportato. Gli strumenti IHE Gazelle e i Connectathon forniscono evidenze aggiuntive; non sostituiscono la validazione della configurazione installata né autorizzano dichiarazioni più ampie del test eseguito.

### 9.2 CDA

La qualificazione CDA comprende:

- XML ben formato e schema della versione dichiarata;
- templateId, cardinalità, vocabolari e regole Schematron dell'IG;
- identificatori, patient/author/custodian/legalAuthenticator e service event;
- coerenza tra header, body strutturato e narrative leggibile;
- nullFlavor, negazione, status, effectiveTime, unità e reference;
- document replacement, addendum, trasformazione, firma e integrità quando applicabili;
- metadata XDS/MHD coerenti con il documento;
- rendering sicuro senza script, external entity o contenuti attivi.

La trasformazione CDA↔FHIR o CDA↔modello canonico usa golden record bidirezionali. Se la reversibilità non è possibile, la perdita informativa deve essere classificata, visibile e approvata.

## 10. DICOM e DICOMweb

La baseline normativa è DICOM PS3.2 edizione corrente qualificata, identificata nel Conformance Pack; alla data di questo documento la pubblicazione ufficiale consultata è **2026c**. La Conformance Statement di HHC segue la struttura PS3.2 e specifica almeno:

- application entity, ruoli SCU/SCP e modello funzionale;
- SOP Class, transfer syntax, association negotiation e presentation context;
- DIMSE service, status, timeout, retry e limiti di concorrenza;
- character set, UID generation, coercion e attributi privati;
- storage commitment, query/retrieve e media handling, se supportati;
- DICOMweb endpoint e operazioni QIDO-RS, WADO-RS, STOW-RS, se supportate;
- content negotiation, multipart, range, bulk data e limite degli oggetti;
- TLS, autenticazione, autorizzazione, audit e gestione delle credenziali;
- configurazione, dipendenze, limiti e comportamento in errore.

PS3.2 chiarisce che una Conformance Statement è solo il primo livello di confronto e non garantisce da sola l'interoperabilità. HHC deve quindi provare la combinazione reale con modalità, PACS/VNA, viewer e gateway rappresentativi della facility.

### 10.1 Casi obbligatori DICOM

- associazione accettata/rifiutata e negotiation parzialmente compatibile;
- SOP/transfer syntax supportata e non supportata;
- C-STORE, commitment e retry dopo esito ambiguo;
- C-FIND/C-MOVE/C-GET ove dichiarati, inclusi risultati parziali;
- STOW multipart, QIDO pagination/filtering e WADO content negotiation;
- oggetti grandi e multi-frame, compressione, streaming e interruzione a metà trasferimento;
- duplicate SOP Instance UID con stesso hash e con contenuto diverso;
- mismatch Patient/Study/Series, late binding e riconciliazione;
- attributi privati, burned-in annotation e pixel data potenzialmente identificante;
- indisponibilità di storage, PACS, DNS, KMS o audit sink;
- isolamento tenant/facility e impossibilità di enumerare studi altrui.

## 11. Terminologie e semantica

La suite terminologica usa snapshot immutabili e licenze compatibili con l'ambiente. Le versioni operative sono mantenute nel [catalogo della terminologia](../semantic/terminology-architecture.md), non duplicate come configurazione in questo documento.

Per ogni codice o binding si verificano:

- sistema canonico, versione, code, display e stato attivo/inattivo;
- membership nel value set e modalità di espansione;
- gerarchie e proprietà soltanto se supportate dalla release sorgente;
- traduzioni tramite `ConceptMap`, equivalence e direzione;
- unità UCUM e normalizzazione senza perdita del valore originale;
- codice locale non mappato, ambiguo, deprecato o sostituito;
- drift tra snapshot, impatto dei delta e riproducibilità storica;
- coerenza tra payload sorgente, evento canonico e proiezioni.

LOINC, SNOMED CT, ICD-11 e ATC richiedono test per la specifica edizione e giurisdizione licenziata. Un mapping automatico non può essere promosso in produzione senza soglia di confidenza, revisione richiesta dal rischio, provenienza e rollback come definito nella [governance dei mapping](../mapping/mapping-governance.md).

## 12. Modello canonico e mapping

La validazione del [Canonical Semantic Event](../canonical-model/canonical-semantic-event.md) e dell'[Integration Envelope](../canonical-model/integration-envelope.md) include:

- schema, invariant e compatibilità forward/backward;
- chiavi tenant, organization, facility, application ed endpoint;
- identità clinica, encounter, order, specimen e imaging context;
- event time, ingestion time, timezone, precisione e ordine causale;
- payload raw, digest, lineage e regola di trasformazione;
- duplicati, correzioni, cancellazioni, merge/unmerge e late event;
- quarantena deterministica e replay con la stessa versione;
- round-trip per campi preservabili e report esplicito delle perdite.

Ogni mapping ha golden dataset, mutation test delle condizioni, property test degli invarianti e differenziale tra versione precedente e nuova. Una modifica che cambia l'esito clinico richiede approvazione clinico-semantica e migrazione controllata.

## 13. OMOP CDM e strumenti OHDSI

La proiezione analitica adotta OMOP CDM **5.5** come baseline attuale, secondo la documentazione OHDSI consultata il 1 settembre 2026. Il supporto degli strumenti all'intero set di funzionalità 5.5 non è assunto: la pagina ufficiale segnala supporto completo del package `CommonDataModel`, mentre per diversi strumenti il supporto di nuove feature non risulta completamente testato e rilasciato.

La qualificazione comprende:

- schema e vincoli della versione CDM dichiarata;
- convenzioni di dominio, standard concept, source value e source concept;
- person, visit, provider, care site e gerarchie organizzative;
- unità, date, era, observation period e provenienza;
- completeness e plausibility rispetto alla fonte riconciliata;
- Data Quality Dashboard con check applicabili e soglie motivate;
- caratterizzazione e trend per individuare drift o rotture dell'ETL;
- compatibilità verificata, non presunta, di ATLAS, Achilles, ARES e tool impiegati;
- de-identificazione/pseudonimizzazione e controllo degli accessi analitici.

La prova confronta conteggi, chiavi, distribuzioni e record campionati attraverso raw → canonico → mapping → OMOP. L'ETL resta asincrono e non partecipa mai al percorso di ACK clinico.

## 14. Contratti API ed eventi non clinici

OpenAPI, AsyncAPI, JSON Schema, XML Schema e WSDL sono versionati come codice. I gate comprendono:

- lint e validazione con parser indipendenti;
- breaking-change detection rispetto all'ultima release supportata;
- consumer-driven contract test per consumer critici;
- compatibilità di enum, required, default, nullable e additional properties;
- status/error model, correlation ID, pagination e rate-limit headers;
- autenticazione/autorizzazione per operazione e oggetto;
- compatibilità di eventi, ordering key, schema evolution e dead-letter behavior;
- fuzzing strutturato e limiti di complessità.

Una modifica incompatibile richiede nuova major version, finestra di coesistenza, telemetria d'uso, avviso ai consumer e prova di rollback.

## 15. Connector SDK e plug-in

Ogni connettore è qualificato separatamente dal runtime core. La suite verifica:

- manifest, API/ABI e versione SDK compatibili;
- firma, provenienza e allowlist dell'artefatto;
- permessi minimi a rete, filesystem, secret e API;
- isolamento di processo o sandbox previsto dall'architettura;
- timeout, cancellazione, retry, circuit breaker e backpressure;
- crash, leak, deadlock e consumo anomalo di CPU/memoria;
- input malevoli e dipendenze compromesse;
- upgrade, downgrade, rollback e configurazione incompatibile;
- telemetria senza PHI e audit delle operazioni privilegiate.

Il fallimento di un connettore non deve propagarsi a tenant, facility o Runtime Cell non correlati.

## 16. Qualificazione europea e country pack

HHC separa il core europeo dai country pack. Ogni pack registra giurisdizione, lingua, profili nazionali, identificativi, terminologie, retention, consenso e dipendenze infrastrutturali. Le prove includono:

- dati con alfabeti, accenti, nomi multipli e convenzioni locali;
- timezone, ora legale, date incomplete e calendari ammessi;
- identificatori nazionali validi, invalidi, sostituiti e assenti;
- ePrescription/eDispensation, Patient Summary, lab, imaging e discharge soltanto per i profili effettivamente adottati;
- scambio transfrontaliero e traduzione controllata, se nel perimetro;
- indisponibilità dei servizi nazionali e continuità locale;
- segregazione dei dati tra aziende sanitarie e facility;
- export, accesso, rettifica, conservazione e legal hold secondo la policy applicabile.

“EHDS-ready” indica requisiti architetturali e di interoperabilità tracciati; non costituisce da solo dichiarazione di conformità legale. Ogni go-live richiede una valutazione aggiornata per paese, ruolo del prodotto e caso d'uso.

## 17. Matrice partner e laboratorio

La matrice di qualificazione registra almeno produttore, prodotto, versione, configurazione, standard/profilo, ruolo, facility type, data, esito e limitazioni. Le combinazioni sono selezionate per rischio e quota installata, non solo per disponibilità.

Il laboratorio riproduce:

- almeno due organization e due facility per organization;
- namespace e identity domain sovrapposti per dimostrare l'isolamento;
- latenze WAN, perdita, riordino e interruzioni rappresentative;
- certificati, trust store e rotazione prossimi alla produzione;
- sistemi partner reali o simulatori con comportamento negativo verificato;
- orologi sincronizzati e scenari di clock skew controllato;
- raccolta integrale delle evidenze senza dati personali reali.

## 18. Casi d'uso di qualificazione end-to-end

### CI-01 — ADT multiazienda con merge e replay

Un ospedale invia ADT HL7 v2, corregge l'identità e produce un merge mentre una seconda azienda usa lo stesso identificativo locale.

**Verifiche:** profilo e ACK; isolamento dei namespace; idempotenza; merge/unmerge; lineage; riconciliazione; nessuna contaminazione cross-tenant; replay deterministico; audit completo.

### CI-02 — Ordine e risultato di laboratorio transfrontaliero

Ordine e risultato attraversano HL7 v2, modello canonico e FHIR con codici locali, LOINC, UCUM, valore critico e testo multilingue.

**Verifiche:** conservazione di valore/unità/range/status; mapping versionato; timezone; amendment; notifica del valore critico; quarantena di codice ambiguo; perdita informativa esplicita.

### CI-03 — Patient Summary e documento CDA/FHIR

Una struttura pubblica produce un Patient Summary per scambio europeo e riceve un documento aggiornato.

**Verifiche:** IG e country pack; narrative/structured consistency; autore/custode/firma; versioning/replacement; terminologia; consenso; rendering sicuro; evidenza degli obblighi non computabili.

### CI-04 — Imaging DICOM tra facility e VNA centrale

Una modalità invia uno studio multi-frame, richiede storage commitment e lo studio è recuperato via DICOMweb da un'altra facility autorizzata.

**Verifiche:** association negotiation; transfer syntax; UID collision; commit; streaming; metadata patient/study; autorizzazione facility; audit; interruzione e resume/retry senza duplicati incoerenti.

### CI-05 — XDS/MHD con registry indisponibile

Un documento clinico deve essere pubblicato mentre il repository è raggiungibile ma il registry non lo è.

**Verifiche:** atomicità prevista dal profilo; errore non ambiguo; nessun successo falso; retry idempotente; riconciliazione; allarme; nessuna perdita del raw.

### CI-06 — Aggiornamento terminologico

Viene promossa una nuova release SNOMED CT/LOINC con concetti inattivati e nuove mappe.

**Verifiche:** delta impact; vecchi eventi riproducibili; nuovi mapping approvati; nessun remap retroattivo silenzioso; rollback; metriche di unmapped/ambiguous code.

### CI-07 — Proiezione OMOP 5.5

Eventi di più aziende alimentano dataset OMOP segregati per finalità analitica.

**Verifiche:** convenzioni CDM; DQD; conteggi reconciliati; vocabolari; care site; pseudonimizzazione; autorizzazione; nessun impatto sul percorso clinico; qualificazione dei tool OHDSI usati.

### CI-08 — Upgrade partner incompatibile

Un vendor modifica un profilo o un comportamento nonostante la versione dichiarata resti uguale.

**Verifiche:** synthetic probe; contract drift; quarantena selettiva; mantenimento del servizio agli altri partner; alert; rollback/config override approvato; aggiornamento della matrice.

## 19. Gate, evidenze e scadenza

Il pacchetto di evidenze contiene:

- requisito, rischio, caso d'uso e Conformance Pack;
- commit, build, SBOM e ambiente;
- fixture con classificazione e provenienza;
- output raw dei validator e report normalizzato;
- log, metriche e trace correlate;
- differenze attese/effettive e difetti;
- revisione clinica, sicurezza e interoperabilità;
- deviazioni, rischio residuo, responsabile e scadenza;
- firma e timestamp affidabile.

Le evidenze scadono dopo sei mesi, al cambio di una dipendenza qualificata o prima se il rischio lo richiede. Il gate di release fallisce per difetti critici/aperti, claim senza prove, artefatti non riproducibili, deviazioni scadute o regressioni semantiche non approvate.

## 20. Fonti ufficiali e data di verifica

Fonti verificate il **1 settembre 2026**:

- HL7 FHIR, Conformance Module: <https://hl7.org/fhir/conformance-module.html>
- HL7 FHIR, Validation: <https://hl7.org/fhir/validation.html>
- HL7 FHIR, operazione normativa `$validate`: <https://hl7.org/fhir/R5/resource-operation-validate.html>
- HL7 v2, sito ufficiale e versioni pubblicate: <https://www.hl7.org/implement/standards/product_brief.cfm?product_id=185>
- HAPI HL7 v2, strutture supportate: <https://hapifhir.github.io/hapi-hl7v2/base/apidocs/ca/uhn/hl7v2/model/package-summary.html>
- IHE IT Infrastructure Technical Framework: <https://profiles.ihe.net/ITI/>
- IHE Gazelle: <https://gazelle.ihe.net/>
- DICOM PS3.2 2026c, Conformance: <https://dicom.nema.org/medical/dicom/current/output/html/part02.html>
- DICOM PS3.2, limiti delle Conformance Statement: <https://dicom.nema.org/medical/dicom/current/output/chtml/part02/sect_n.3.3.html>
- OHDSI, OMOP Common Data Model: <https://ohdsi.github.io/CommonDataModel/>
- OHDSI, Data Quality Dashboard check index: <https://ohdsi.github.io/DataQualityDashboard/articles/checkIndex.html>
- W3C, WCAG 2.2: <https://www.w3.org/TR/WCAG22/>

Le fonti ufficiali prevalgono su esempi, tool e documentazione secondaria. Prima di qualificare una release, il responsabile dell'interoperabilità deve verificare che pagina, package e stato normativo non siano cambiati, registrando data e digest nel Conformance Pack.
