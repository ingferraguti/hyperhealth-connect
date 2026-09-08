# Canonical Semantic Event (CSE)

Stato: specifica baseline 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Target: pivot semantico stabile e loss-aware per interoperabilità e analytics

## 1. Obiettivo e non-obiettivi

Il CSE separa significato clinico-operativo dai formati HL7 v2, FHIR, CDA, DICOM e proprietari. Riduce mapping N×M, rende provenance/versioni esplicite e consente proiezioni multiple. Non è EHR, FHIR universale, terminologia clinica, MPI né OMOP. Non sostituisce il raw e non pretende di rappresentare ogni dettaglio di ogni standard.

Il modello è event-centric, modulare e loss-aware. Se un'informazione non è rappresentabile, resta nel raw/parsed artifact e viene registrata come semantic gap; non è scartata silenziosamente.

## 2. Principi

1. Source fidelity prima della normalizzazione.
2. Concetto e codice sorgente convivono; mapping non cancella l'originale.
3. Identificatori includono namespace/assigner/period.
4. Event time, effective time, authored time e recorded time sono distinti.
5. Null, absent, unknown, not-applicable e masked sono distinti.
6. Status e lifecycle sono espliciti; correzione non è nuovo fatto indipendente.
7. Reference è scoped e typed; risoluzione non è implicita.
8. Ogni campo derivato ha lineage e confidence.
9. Schema e semantic mapping sono versionati separatamente.
10. Estensioni locali sono namespaced e hanno exit strategy.

## 3. Struttura comune

```yaml
cseVersion: hhc-cse/1.0
eventId: cse-uuid
eventType: observation.recorded
eventStatus: preliminary|final|amended|corrected|cancelled|entered-in-error
subject: {reference: subject-token, type: Patient, scope: organization-id}
encounter: {reference: encounter-token, type: Encounter, optional: true}
actors:
  author: [typed-reference]
  performer: [typed-reference]
  recorder: [typed-reference]
locations: [typed-reference]
times:
  effective: temporal-value
  authored: optional-temporal-value
  issued: optional-temporal-value
  recorded: utc-instant
content: domain-specific-object
source:
  envelopeId: urn:uuid:...
  pointers: [source-pointer]
  sourceStandard: HL7V2
  sourceProfile: exact
semantics:
  mappingRelease: exact
  terminologySnapshot: exact
  quality: [finding]
  losses: [semantic-gap]
governance:
  classification: HEALTH_DATA
  purposeContext: care-delivery
  restrictions: [policy-reference]
relationships:
  replaces: [event-id]
  derivesFrom: [event-id]
  partOf: [event-id]
  basedOn: [typed-reference]
```

## 4. Type system

| Tipo | Contenuto | Invariante |
|---|---|---|
| `Identifier` | system, value, type, assigner, period | system/value non separati |
| `Coding` | system URI, version, code, display, userSelected | display non è identità |
| `Concept` | coding[], text, mapping state | più coding con provenance |
| `Quantity` | value decimal, unit, system, code, comparator | niente float binario per valori clinici |
| `Ratio/Range` | quantity component | unità compatibili o finding |
| `TemporalValue` | lexical, normalized, precision, offset | precisione preservata |
| `Reference` | type, token, scope, identifier optional | no dereference cross-scope implicito |
| `HumanName/Address` | parti, use, period | non usati come chiave |
| `Attachment` | mediaType, bytes, checksum, ref | contenuto grande fuori evento |
| `Annotation` | text, author, time | PHI e rendering policy |
| `AbsentReason` | unknown/asked-unknown/masked/NA/etc. | distinto da null tecnico |

Decimal preserva scale quando clinicamente significativa. Unit conversion usa UCUM o tabella approvata e crea un nuovo valore derivato mantenendo l'originale. Boolean non rappresenta “sconosciuto”.

## 5. Domini CSE

### 5.1 Identity e administration

`PatientIdentityEvent`, `EncounterEvent`, `LocationEvent`, `PractitionerEvent`, `OrganizationEvent`. Merge/unmerge è relazione proveniente dall'MPI; HHC non decide identity match. Encounter conserva class, service, admission/discharge, transfer e facility timeline.

### 5.2 Order e workflow

`ServiceRequestEvent`, `MedicationOrderEvent`, `SpecimenEvent`, `TaskEvent`. Conserva placer/filler ID, intent, priority, requested timing, reason, requester e status. Order cancellation/amendment è lifecycle, non delete.

### 5.3 Clinical observations

`ObservationEvent`, `DiagnosticReportEvent`, `ConditionEvent`, `ProcedureEvent`, `AllergyIntoleranceEvent`, `MedicationAdministrationEvent`, `MedicationDispenseEvent`, `DeviceUseEvent`. Ogni domain ha required core e extension point governati.

### 5.4 Documents e imaging

`ClinicalDocumentEvent` descrive identity, type, author, attestation, confidentiality, period e content reference. `ImagingStudyEvent` descrive study/series/instance metadata e references; pixel data resta in DICOM object store.

### 5.5 Consent e governance

`ConsentDecisionEvent`, `RestrictionEvent`, `DataUseGrantEvent` rappresentano decisioni ricevute da autorità/sistemi competenti; non inventano base giuridica. Include issuer, scope, purpose, effective period, version, revocation e evidence reference.

## 6. ObservationEvent di riferimento

```yaml
content:
  code:
    codings:
      - {system: "http://loinc.org", version: "2.83", code: "...", display: "..."}
    sourceCodings:
      - {system: "urn:local:lab-a", version: "2026-08", code: "HB"}
    mappingStatus: approved
  value:
    kind: quantity
    quantity: {value: "13.2", unit: "g/dL", system: "http://unitsofmeasure.org", code: "g/dL"}
  referenceRange: []
  interpretation: []
  method: optional-concept
  specimen: optional-reference
  bodySite: optional-concept
  components: []
```

Invarianti: value e dataAbsentReason non coesistono salvo regola domain; status è obbligatorio; subject è obbligatorio salvo evento non-patient espressamente modellato; unit conversion registra formula/engine/version; abnormality non viene inferita se range/context non sufficiente.

## 7. Codifica e semantic mapping

Un `Concept` può contenere source coding, mapped coding e testo. Ogni mapping ha relationship (`equivalent`, broader, narrower, related, no-map), map rule, context dependency, confidence, reviewer e validity. Solo mapping `approved` nel release attivo può guidare delivery o analytics. `proposed`/`ambiguous` resta finding.

Il modello non forza un unico vocabolario per ogni dominio. La policy può scegliere SNOMED CT per concetti clinici, LOINC per osservazioni/documenti, UCUM per unità, ICD-10/ICD-11 per reporting e ATC per drug utilization; OMOP usa i propri standard concept secondo vocabulary snapshot.

## 8. Provenance campo-a-campo

Ogni elemento materialmente trasformato può referenziare:

```yaml
sourcePointer:
  artifactRef: parsed-artifact-id
  path: "OBX[1].5[1]"
  lexicalValueHash: hmac-or-sha256
transformation:
  mappingId: map-lab-result
  mappingVersion: 3.4.1
  ruleId: quantity-from-obx5-obx6
  terminologySnapshot: ts-2026-08
  workerBuild: sha256:...
```

Per performance, il dettaglio può essere compattato in provenance block condivisi, ma deve restare interrogabile. Gli hash di piccoli valori sensibili usano HMAC scoped per evitare dictionary attack.

## 9. Loss accounting

`semanticGap` contiene source path, reason, severity, disposition e preservation reference. Categorie: unsupported-field, ambiguous-code, precision-loss, cardinality-loss, incompatible-unit, unknown-extension, conflicting-source, target-restriction. Gate:

- `INFO`: preservato senza effetto;
- `WARN`: output consentito, quality visible;
- `ERROR`: destinazione specifica bloccata;
- `FATAL`: evento quarantinato.

Il loss budget è definito per mapping/destination. Zero warning non è sinonimo di equivalenza clinica; la review clinico-informatica resta necessaria.

## 10. Schema evolution

Major rompe compatibilità; minor aggiunge elementi opzionali; patch chiarisce senza cambiare wire semantics. Gli enum sono extensible con `unknownCode`. Producer/consumer supportano N/N-1 durante rolling update. Migration non riscrive raw; crea CSE vNext collegato al predecessore.

Un event type entra nel core solo se riutilizzato fra più standard/use case e con semantica stabile. Contenuto nazionale/vendor resta extension namespaced finché non maturo.

## 11. Validazione

1. schema/type;
2. invariant common;
3. domain invariant;
4. terminology/version;
5. temporal/identity consistency;
6. privacy/purpose;
7. destination fitness.

Finding include CSE path, source pointer, rule/version, severity e disposition. La validazione del CSE non sostituisce quella dello standard sorgente o target.

## 12. Prestazioni e storage

Il CSE piccolo può stare nel broker; attachment/document/pixel restano referenziati. Serializzazione target ≤256 KiB per evento ordinario; oltre soglia usa content chunk/reference. Schemi compilati, terminology cache e mapping precompilato evitano chiamate remote nel Tier 0. Batch è permesso solo preservando identity/lineage per record.

CSE è derivato immutabile, partizionato per scope/time/domain. Indici clinici full text non sono default. Cache è ricostruibile da raw+versioned artifacts. Back-pressure semantic/analytics non blocca delivery clinica.

## 13. Sicurezza, HA e DR

Authorization è scope e purpose-aware. Field-level masking crea view/derivato tracciato, non mutazione. CSE è cifrato con tenant/dataset context. Accesso di supporto è break-glass.

Schema, mapping e terminology snapshot sono replicati con il runtime bundle; una cella opera ≥24 ore senza Control Plane. Restore prova CSE↔raw↔lineage e version artifact. Tier 0 eredita SLO/RTO/RPO dal flow; la rigenerabilità non giustifica la perdita di output già ACKed se serve alla riconciliazione.

## 14. Proiezioni

- FHIR: Resource/Bundle conforme a release e IG, con loss report;
- CDA: documento nuovo solo con template, narrative e attestation policy;
- HL7 v2: message profile target con ACK/delivery ledger;
- OMOP 5.5: dataset version asincrona, vocabulary snapshot e DQ gate;
- audit/monitoring: metadata minimizzati, mai copia del payload.

Ogni proiezione dichiara target, version, mapping, terminology, input watermark e fitness for use.

## 15. Casi d'uso europei 2026

### UC-CSE-01 — laboratorio multi-LIS

Codici e unità locali diversi convergono senza perdere source coding. Accettazione: LOINC/UCUM approvati, conversione riproducibile, ambiguità bloccata per OMOP e consegna clinica non ritardata.

### UC-CSE-02 — ADT multi-facility

Eventi v2/FHIR alimentano EncounterEvent. Accettazione: facility/assigner preservati, ordering bounded, merge solo da MPI, proiezioni indipendenti.

### UC-CSE-03 — patient summary transfrontaliero

FHIR/CDA nazionali producono CSE con lingua, coding e provenance originari. Accettazione: contenuto non mappabile visibile come gap e non trasformato speculativamente.

### UC-CSE-04 — studio multicentrico

Eventi CSE proiettano dataset OMOP locali. Accettazione: ogni riga risale a CSE/raw/mapping, dataset e vocabulary version; query federata non centralizza identificativi.

## 16. Test e gate

- schema/invariant e compatibility N/N-1;
- golden mapping da HL7/FHIR/CDA/DICOM;
- round-trip dove significativo e semantic diff sempre;
- null/absent/precision/timezone/unit test;
- ambiguous/missing terminology e loss budget;
- multi-tenant isolation e field masking;
- throughput/burst/replay e terminology outage;
- restore completo con version artifact.

## 17. Fonti ufficiali verificate

- [HL7 FHIR R5 — datatypes e resources](https://hl7.org/fhir/R5/), consultata il 1 settembre 2026; usata come riferimento, non come modello interno obbligatorio.
- [HL7 FHIR ConceptMap](https://hl7.org/fhir/R5/conceptmap.html), consultata il 1 settembre 2026.
- [W3C PROV-O Recommendation](https://www.w3.org/TR/2013/REC-prov-o-20130430/), consultata il 1 settembre 2026 per il modello di provenance interoperabile.
- Fonti specifiche HL7 v2, CDA/IHE, DICOM e OMOP sono censite nei rispettivi documenti `../standards/` e `../omop/`.

## 18. Collegamenti

- `integration-envelope.md`
- `../mapping/mapping-engine.md`
- `../semantic/semantic-mapping-registry.md`
- `../semantic/terminology-architecture.md`
- `../semantic/lineage-and-provenance.md`
