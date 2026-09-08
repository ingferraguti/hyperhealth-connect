# Analytics e compatibilità OHDSI

Stato: baseline di produzione 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Baseline dataset: OMOP CDM 5.5 con qualification per tool

## 1. Scopo

Definisce come HHC qualifica e opera tool OHDSI, coorti, studi federati e ambienti analitici. “Compatibile OMOP” richiede schema/conventions/vocabulary/quality; “compatibile con un tool” richiede inoltre una matrice testata di versione, DB dialect e feature usate.

## 2. Stato ufficiale al 1 settembre 2026

La pagina OMOP ufficiale dichiara CDM 5.5 corrente. La relativa matrice riporta:

| Tool/famiglia | Legacy support 5.5 | Feature support 5.5 | Decisione HHC |
|---|---:|---:|---|
| CommonDataModel R package | rilasciato | rilasciato | baseline DDL/docs |
| DataQualityDashboard | rilasciato | non ancora interamente testato/rilasciato | qualification + gap checks |
| Achilles | rilasciato | non ancora interamente testato/rilasciato | qualification per characterization |
| ARES | rilasciato | non ancora interamente testato/rilasciato | solo risultati compatibili |
| ATLAS | rilasciato | non ancora interamente testato/rilasciato | feature flag/compatibility test |
| WhiteRabbit/Rabbit-in-a-Hat | rilasciato | non ancora interamente testato/rilasciato | design aid, non runtime authority |
| FeatureExtraction | rilasciato | non ancora interamente testato/rilasciato | study-specific qualification |
| CohortDiagnostics | rilasciato | non ancora interamente testato/rilasciato | study-specific qualification |

La tabella è una fotografia datata, non un vincolo eterno. Ogni product release ricontrolla i repository ufficiali e aggiorna la matrice/evidence.

## 3. Stack logico

| Capability | Candidati OHDSI | Funzione |
|---|---|---|
| CDM/DDL | CommonDataModel | schema e documentation |
| Data quality | DataQualityDashboard | conformance/completeness/plausibility |
| Characterization | Achilles, ARES | profiling e comparison |
| Cohort authoring | ATLAS/WebAPI, CirceR | definizione e traduzione coorte |
| Cohort execution | CohortGenerator | generazione riproducibile |
| Diagnostics | CohortDiagnostics | valutazione phenotype |
| Covariates | FeatureExtraction | feature generation |
| SQL portability | SqlRender | render dialect SQL |
| DB connectivity | DatabaseConnector | accesso controllato |
| Analytics | HADES packages | characterization/estimation/prediction |
| ETL design | WhiteRabbit/Rabbit-in-a-Hat, Usagi | profiling/mapping assistito |

L'elenco non obbliga l'adozione. Ogni componente passa license/security/maintenance/compatibility assessment e viene isolato dietro API/job runner HHC.

## 4. Compatibility manifest

```yaml
qualificationId: ohdsi-qual-...
dataset:
  cdmVersion: "5.5"
  vocabularyVersion: exact
  databaseDialect: exact
tool:
  repository: official-url
  version: exact-tag-or-commit
  imageDigest: sha256
  dependenciesLock: artifact-ref
features:
  legacy: tested
  cdm55: [table/field/operation list]
testPack: immutable-ref
results: {pass, fail, waived}
knownLimitations: []
securityReview: evidence-ref
licenseReview: evidence-ref
approvedAt: instant
expiresAt: instant
```

Un tool con feature gap può essere usato su subset 5.4-compatible se query e output sono verificati; il dataset resta 5.5 e la limitazione è visibile.

## 5. Qualification suite

- install/schema discovery su CDM 5.5;
- tutte le tabelle/field usate e concept metadata nuovi;
- representative DQD/Achilles/ATLAS/cohort workflows;
- SQLRender sul dialect effettivo;
- Unicode, timezone, large IDs/count e empty tables;
- concurrent workload, cancellation, timeout e temp quota;
- auth/RBAC/tenant isolation;
- no PHI in log/error;
- backup/restore dei metadata tool;
- upgrade/rollback e result reproducibility.

Golden dataset sintetico include inpatient, ED, outpatient, home/telehealth, lab, drug, procedure, device, note, episodes e edge cases europei. Expected cohort/count sono versionati.

## 6. ATLAS e WebAPI

ATLAS è UI, WebAPI backend; non sono security boundary sufficiente. HHC pone reverse proxy/identity federation, project/purpose authorization e DB connection broker. Connection details/secret non sono visibili agli utenti. Cohort definition, concept set, vocabulary/dataset version e execution sono auditati.

Write è limitato a results/cohort schema; CDM/vocab read-only. Arbitrary SQL è disabilitato per utenti standard. Upgrade database schema WebAPI è separato da CDM upgrade e ha backup/rollback.

## 7. Data Quality Dashboard

DQD applica check parametrizzati e migliaia di verifiche. HHC conserva tool version, control files, threshold overrides, SQL rendered, start/end, failures e violated-row access audit. Fatal devono essere zero; convention failure richiede remediation/waiver; characterization informa fitness.

Threshold locali sono versionati per source/dataset/use case. Personalizzare non significa nascondere failure: default e override/delta sono entrambi conservati. Violated rows contengono PHI potenziale e sono accessibili solo a ETL steward autorizzati.

## 8. Achilles e ARES

Characterization è eseguita dopo DQD strutturale. Results sono separati per dataset version. Small cells e rare concepts possono re-identificare; accesso e export sono controllati. ARES visualizza risultati solo se versioni schema/output compatibili. Drift dashboard confronta predecessor e facility senza esposizione patient-level.

## 9. Coorti e phenotype

Una CohortDefinition immutabile include concept sets, logic, observation window, exclusions, vocabulary expectations e clinical rationale. Authoring→peer review→diagnostics→approval→execution. Cohort ID non è semantic identity; canonical definition hash/version lo è.

CohortDiagnostics valuta phenotype su ciascun sito. Differenze possono derivare da population/source/ETL/vocabulary, non automaticamente da bug. Modifiche producono nuova version, non rewrite della coorte precedente.

## 10. Studi federati

```text
coordinating center
  signed analysis package + protocol + output policy
           │
   ┌───────┼────────┐
 site A   site B   site C
 local CDM/read-only execution
 output disclosure review
   └───────┼────────┘
      permitted aggregate results
```

Package contiene code, lockfile/container digest, required CDM/vocabulary/tool versions, resource budget, expected outputs e signature. Site verifica permit, protocol, compatibility, DQ fitness e egress. Nessuna query arbitraria remota. Output è schema-validato, small-cell checked, malware scanned e approvato.

## 11. Secure processing environment

- identity federata, MFA e JIT;
- dataset read-only e project-scoped;
- egress deny/allowlist; no clipboard/download se richiesto;
- container/package firmati e registry interno;
- compute, query e output budget;
- session/job/query audit;
- workspace ephemeral con retention;
- disclosure review e dual control;
- re-identification key assente;
- emergency termination e evidence preservation.

## 12. AI e machine learning

FeatureExtraction/HADES o pipeline custom operano su DatasetVersion dichiarata. Split, labels, code, parameters, model/container, metrics e fairness assessment sono versionati. Nessun output diventa decisione clinica senza intended-purpose, MDR/IVDR/AI Act assessment e lifecycle separato.

Un LLM può assistere ricerca concetti/coorte via API tipizzate; non riceve dump patient-level, non esegue SQL arbitrario, non auto-pubblica. Dal 2 agosto 2026 si applica una fase ulteriore dell'AI Act: classification e obligations vanno valutate per use case; HHC non dichiara conformità generica.

## 13. Prestazioni e workload management

Classi: interactive cohort, scheduled characterization, batch study, DQD, export. Ognuna ha queue, concurrency, timeout, CPU/memory/temp/result quota. Priority inversion è evitata; kill/cancel è auditato. Query plan e statistics sono monitorati per dataset version.

SLO Tier 2: 99,9% per la piattaforma analytics; job SLO è per classe (queue start, completion). RTO ≤8 ore e RPO ≤1 ora per service/catalog/results. Un servizio analitico contrattualmente promosso a Tier 1 deve dimostrare capacity e DR dedicati. CDM READY replica e backup secondo architecture. Un job lungo è restartable o idempotente; partial output non è pubblicato.

## 14. Security e supply chain

Versione esatta/tag/commit non basta: container digest, R package lock, Java/npm dependency, SBOM, signature e CVE sono verificati. Release GitHub non è scaricata al runtime. Plugin/package non approvati sono vietati. DatabaseConnector usa credential broker e least privilege.

Vulnerability critica attiva impact analysis via lineage, isolamento e patch/requalification. CRA reporting obligations possono applicarsi dall'11 settembre 2026: security incident/product vulnerability process deve raccogliere evidence e supportare le tempistiche applicabili, senza assumere automaticamente che ogni deployment ricada nel CRA.

## 15. Audit e monitoraggio continuo

Audit: login, project/grant, definition edit/approve, job submit/cancel, query, dataset access, package/import, output review/export, admin/change e break-glass. Monitor: tool health/version, queue/job time, error, DB/resource saturation, DQD/Achilles age, failed controls, output volume, unusual queries/exports, permit expiry e replication/backup.

SOC riceve security event; NOC/SRE riceve reliability; data quality owner riceve semantic/DQ. Correlation ID unisce i domini senza payload.

## 16. Casi d'uso europei 2026

### UC-AN-01 — studio multicentrico farmaco-sicurezza

Protocollo e cohort package firmati girano su CDM locali. Accettazione: version compatibility, DQD fitness, diagnostics, output disclosure e risultati riproducibili senza centralizzare righe.

### UC-AN-02 — sorveglianza sanità pubblica

Job autorizzato produce indicatori per period/facility. Accettazione: legal purpose/permit, source coverage, timeliness, small cells e audit; nessuna confusione tra assenza record e assenza malattia.

### UC-AN-03 — upgrade tool con CDM 5.5

Nuova versione DQD/ATLAS passa qualification sul golden dataset e shadow su snapshot. Accettazione: matrice aggiornata, diff spiegati, rollback e nessuna modifica al dataset.

### UC-AN-04 — accesso ricercatore

Ricercatore entra nel secure environment, esegue coorte approvata e chiede export aggregato. Accettazione: JIT, read-only, audit, budget, review e revoca automatica.

### UC-AN-05 — supporto AI alla coorte

Agente suggerisce concept set usando terminology API. Accettazione: input minimizzato, suggestion provenance, revisione umana e nessun accesso patient-level.

## 17. Release gate

- official repo/version/digest e license/SBOM/CVE;
- compatibility manifest e golden suite;
- CDM/vocabulary/dialect matrix;
- security/RBAC/query isolation;
- performance/concurrency/cancellation;
- audit/redaction/output control;
- upgrade/rollback/backup/restore;
- federated package signature e disclosure;
- known limitation pubblicate al team.

## 18. Fonti ufficiali verificate

- [OHDSI OMOP current CDM e tool support](https://ohdsi.github.io/CommonDataModel/), consultato il 1 settembre 2026.
- [OHDSI CommonDataModel repository](https://github.com/OHDSI/CommonDataModel), consultato il 1 settembre 2026.
- [DataQualityDashboard official docs](https://ohdsi.github.io/DataQualityDashboard/articles/checkIndex.html) e [threshold guidance](https://ohdsi.github.io/DataQualityDashboard/articles/Thresholds.html), consultate il 1 settembre 2026.
- Repository ufficiali OHDSI: [Achilles](https://github.com/OHDSI/Achilles), [ARES](https://github.com/OHDSI/Ares), [ATLAS](https://github.com/OHDSI/Atlas), [WebAPI](https://github.com/OHDSI/WebAPI), [HADES](https://github.com/OHDSI/Hades), [CohortDiagnostics](https://github.com/OHDSI/CohortDiagnostics), [FeatureExtraction](https://github.com/OHDSI/FeatureExtraction), [SqlRender](https://github.com/OHDSI/SqlRender), consultati il 1 settembre 2026.
- [FHIR-to-OMOP IG — CI build](https://build.fhir.org/ig/HL7/fhir-omop-ig/) e [repository HL7](https://github.com/HL7/fhir-omop-ig), consultati il 1 settembre 2026: la CI corrente dichiara versione 2.0.0-ballot e “not an authorized publication”; è un riferimento in evoluzione, non la baseline normativa HHC.
- [AI Act — timeline Commissione europea](https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai) e [CRA reporting](https://digital-strategy.ec.europa.eu/en/policies/cra-reporting), consultati il 1 settembre 2026.

## 19. Collegamenti

- `omop-architecture.md`
- `omop-projection-and-etl.md`
- `../product/personas-and-use-cases.md`
- `../semantic/lineage-and-provenance.md`
