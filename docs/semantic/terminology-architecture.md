# Architettura delle terminologie

Stato: baseline enterprise 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Target: terminology service europeo, multi-tenant, versionato e resiliente

## 1. Scopo

Il Terminology Plane importa, valida, indicizza, serve e governa code system, value set, concept map e designations. Mantiene release storiche per riproducibilità e distribuisce snapshot firmati alle Runtime Cell. Non crea concetti ufficiali, non risolve da solo ambiguità cliniche e non presuppone che tutte le terminologie siano liberamente redistribuibili.

## 2. Terminologie baseline

| Sistema | Uso principale | Baseline verificata 1-9-2026 | Nota |
|---|---|---|---|
| SNOMED CT | concetti clinici | International Edition luglio 2026, status Production | estensioni nazionali e licenza per territorio |
| LOINC | osservazioni, test, documenti | 2.83 del 19 agosto 2026 | license/attribution; API poteva avere lag rispetto al file release |
| UCUM | unità computabili | release esatta nel snapshot | conversioni solo per unità compatibili |
| ICD-11 MMS | classificazione/reporting | WHO release 2026 | API v2 e release `2026-01`; non sinonimo di terminologia clinica completa |
| ICD-10 | reporting nazionale/storico | versione/estensione nazionale | non usare una generica release mondiale al posto di requisiti nazionali |
| ATC/DDD | drug utilization | indice annuale applicabile | DDD è unità tecnica di ricerca, non dose prescritta |
| EDQM/standard terms e drug catalogs | medicinali/forme/vie | entitlement nazionale/EMA | condizioni/licenze separate |
| OMOP standardized vocabularies | analytics CDM | Athena snapshot esatto | bundle e concept validity pin-nati |
| DICOM Controlled Terminology | imaging | DICOM edition pin-nata | PS3.16/content mapping resource |
| HL7 Terminology | binding HL7/FHIR/v2 | package/versione esatta | dipendenze IG pin-nate |

Una baseline di prodotto non obbliga ogni deployment ad avere ogni sistema. Entitlement, lingua, estensione nazionale e intended use sono parte dell'`TerminologySnapshot`.

## 3. Snapshot manifest

```yaml
snapshotId: ts-2026-09-eu-prod-01
status: staged|approved|active|retired
createdAt: instant
components:
  - canonicalUri: http://loinc.org
    version: "2.83"
    sourceArtifactDigest: sha256
    effectiveDate: 2026-08-19
    licenseRef: evidence-id
    languages: [en]
    importToolVersion: exact
    qualityReport: evidence-id
mapReleases: [immutable-ref]
omopVocabularyVersion: exact-string-from-VOCABULARY
signature: artifact-ref
```

Il file sorgente è conservato se la licenza lo consente; altrimenti si conserva digest, receipt, entitlement e procedura di re-download. “Current” non è un valore valido nel runtime.

## 4. Componenti

### 4.1 Import/validation

Adapter per RF2, LOINC CSV, WHO API/export, FHIR CodeSystem/ValueSet, OMOP vocabulary e formati nazionali. Import in staging, verifica checksum/signature, schema, referential integrity, duplicate, hierarchy cycle, active/effective dates, language refset/designation, map consistency e delta/full relation.

### 4.2 Terminology store

Store relazionale/graph/search separa canonical content, edition/module, designation, relationship, refset/value set, property, map e release metadata. Le release approvate sono immutabili. Indici sono ricostruibili; artifact e manifest sono autorevoli secondo ruolo.

### 4.3 Runtime service

API: lookup, validate-code, expand, subsumes, translate, designation search e batch. Response include system, version, result, issues e snapshot. Pagination/limit/cost guard obbligatori. Expansion grandi sono job/materialized artifact.

### 4.4 Authoring/stewardship

UI/API separata dal runtime. Gestisce local code systems, refset/value set e mapping candidati con review. Non modifica distribuzioni ufficiali; contenuto locale ha namespace/owner/version/license.

## 5. SNOMED CT

RF2 supporta version tracking, moduli, refset ed estensioni. Import rispetta dependency fra International Edition ed estensione nazionale. La release luglio 2026 ha aumentato la capacità delle description fino a 4096 caratteri: schema HHC non deve avere limite fisso 255. Full/Snapshot/Delta sono gestiti secondo specification; snapshot runtime deriva da una full chain verificata.

Licensing è territoriale. Nei Member countries non è normalmente applicata una fee affiliate da SNOMED International, ma registrazione/distribuzione nazionale e condizioni restano obbligatorie. Deployment in non-Member territory richiede licenza annuale. HHC conserva entitlement per tenant/territorio e impedisce export non autorizzato.

## 6. LOINC

LOINC 2.83 è stata pubblicata il 19 agosto 2026. Il sito raccomanda uso della versione corrente e aggiornamento tempestivo; HHC non aggiorna automaticamente: importa, diff, valida mappe e promuove. Le release note del 26 agosto segnalavano un lag temporaneo del Terminology Service rispetto al file 2.83: il manifest registra quindi fonte concreta e digest, non assume equivalenza API/download.

La licenza permette uso commerciale e non commerciale con condizioni/attribution. Il prodotto include notice richiesto e non usa il materiale per promulgare uno standard concorrente. Status Active/Deprecated/Discouraged/Trial influenza validation e mapping.

## 7. ICD-11 e ICD-10

WHO ha pubblicato ICD-11 2026; API v2 fornisce accesso REST a Foundation/MMS e release multiple, con OAuth e local deployment. HHC pin-na `2026-01` e lingua/linearization. Il supporto FHIR WHO eventualmente prerelease non è assunto production contract senza verifica dedicata.

ICD-11 Foundation e MMS sono distinti. Codici MMS non sono URI Foundation. Post-coordination e extension code richiedono representation completa. ICD-10 resta governata per versione/modifica nazionale finché richiesta. Mapping ICD-10↔11 è use-case specific e loss-aware.

## 8. ATC/DDD e medicinali

ATC ha cinque livelli; DDD è dose media di mantenimento tecnica per drug-utilization research, non PDD e non istruzione clinica. Il catalogo è aggiornato annualmente e la versione è citata nei risultati longitudinali. Prodotti nazionali sono collegati a sostanza/ATC con validazione esperta; route/strength/combinations possono cambiare code. Nessun ATC è assegnato per similarità testuale automatica.

## 9. ValueSet expansion e cache

Expansion key: canonical URL, version, parameters, filter, language, activeOnly, includeDesignations, system versions e snapshot. Expansion è immutabile per key e ha checksum. Limiti proteggono da esplosioni; un client deve dichiarare se accetta expansion incomplete. Cache invalidation avviene solo attivando nuovo snapshot.

## 10. Unit conversion

UCUM valida unit code e compatibilità dimensionale. La conversione registra valore/unità originali, formula/library version, rounding e output. Temperature offset, molar/mass conversion e unità dipendenti da sostanza richiedono contesto; conversioni dimensionalmente impossibili falliscono. Range e comparator sono preservati.

## 11. Multilingua

Designation conserva language, use, module/refset e acceptability. Fallback chain è configurata per tenant/realm e mai modifica concept identity. Display mancante non invalida automaticamente il code; display mismatch è finding. Search usa analyzer per lingua ma non determina equivalenza.

## 12. Sicurezza, privacy e licenze

Il contenuto terminologico non è PHI, ma query code-context possono rivelare informazioni; log e metriche non registrano valori clinici. API authoring richiede MFA/RBAC. Runtime usa mTLS/workload identity e rate limit. Artifact repository applica entitlement, export control, audit e checksum.

## 13. Performance, HA e DR

Runtime nodes read-only sono replicati per cella. Lookup/validate p99 target <10 ms e cached translate <5 ms nelle condizioni dichiarate. Bulk/expansion/authoring sono isolati. Nessuna chiamata Internet nel Tier 0.

Terminology authoring service: SLO 99,9%, RTO ≤4 ore, RPO ≤15 minuti. Runtime eredita il tier del flow e continua ≥24 ore offline. Backup comprende artifact, manifests, licensing evidence, keys references e historical releases. Restore prova lookup/expand/translate per snapshot storici.

## 14. Monitoraggio

Metriche: lookup/validate/translate rate e p99, cache hit, unknown/inactive/deprecated, expansion size/time, snapshot age, import finding, license expiry, replica lag, checksum/signature, memory/index size e facility divergence. Alert su snapshot mancante, entitlement scaduto, import corruption, unexpected code spike e runtime fallback.

## 15. Casi d'uso europei 2026

### UC-TERM-01 — laboratorio multinazionale

Codici locali sono validati/mappati a LOINC 2.83 e unità UCUM, mantenendo lingua originale. Accettazione: mapping contestuale, source preservato, snapshot per risultato e nessuna dipendenza Internet.

### UC-TERM-02 — SNOMED nazionale

International luglio 2026 più extension nazionale sono importate secondo dependency. Accettazione: module/version corretti, description >255 supportata, entitlement territoriale e rollback.

### UC-TERM-03 — reporting ICD-11 2026

Eventi clinici alimentano classificazione MMS con revisione prevista. Accettazione: release/linearization/language citate, post-coordination preservata e nessuna equivalenza automatica con ICD-10.

### UC-TERM-04 — farmacoutilizzazione

Prodotti nazionali sono collegati ad ATC/DDD per analisi. Accettazione: versione annuale, route/strength/combinations considerate e DDD mai mostrata come prescrizione.

## 16. Release gate

- checksum/signature/schema e referential integrity;
- diff da predecessor e impact mapping;
- license/entitlement/attribution;
- domain/hierarchy/inactivation checks;
- API conformance e deterministic cache;
- performance/soak/large expansion;
- multi-tenant isolation e export controls;
- offline operation, backup/restore e rollback;
- clinical/data steward approvals.

## 17. Fonti ufficiali verificate

- [SNOMED CT July 2026 International Edition release notes](https://conf.spaces.snomed.org/wiki/spaces/RMT/pages/972029981/SNOMED%2BCT%2BJuly%2B2026%2BInternational%2BEdition%2B-%2BSNOMED%2BInternational%2BRelease%2Bnotes), consultate il 1 settembre 2026; status Production, release date 20260701.
- [SNOMED RF2 specification](https://docs.snomed.org/snomed-ct-specifications/snomed-ct-release-file-specification/1-introduction) e [licensing/get SNOMED](https://www.snomed.org/get-snomed), consultati il 1 settembre 2026.
- [LOINC 2.83](https://loinc.org/), [release notes](https://loinc.org/kb/loinc-release-notes), [license](https://loinc.org/license) e [versioning](https://loinc.org/kb/versioning), consultati il 1 settembre 2026.
- [WHO ICD-11 2026](https://www.who.int/news/item/16-02-2026-icd-11-2026-release) e [ICD API v2](https://icd.who.int/icdapi/docs2/APIDoc-Version2/), consultati il 1 settembre 2026.
- [WHO ATC/DDD Toolkit](https://www.who.int/tools/atc-ddd-toolkit) e [methodology](https://www.who.int/tools/atc-ddd-toolkit/methodology), consultati il 1 settembre 2026.
- [OHDSI Athena](https://athena.ohdsi.org/) e [OMOP CDM](https://ohdsi.github.io/CommonDataModel/), consultati il 1 settembre 2026.

## 18. Collegamenti

- `semantic-mapping-registry.md`
- `lineage-and-provenance.md`
- `../omop/omop-projection-and-etl.md`
