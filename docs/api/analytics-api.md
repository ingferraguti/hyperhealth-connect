# Clinical Analytics API

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ambito | API governate su dataset OMOP e prodotti analitici HHC |
| Target | Uso sanitario europeo primario/secondario autorizzato |
| Ultimo aggiornamento | 1 settembre 2026 |
| Responsabili | Analytics Platform, Data Governance, Privacy, Security, Clinical Informatics, SRE |
| Classe | Tier 2: SLO 99,9%, RTO ≤8 ore, RPO ≤1 ora |

## 1. Finalità

La Clinical Analytics API espone primitive riproducibili e governate per data source, dataset version, vocabolari, concept set, coorti, feature, quality, characterization, lineage ed export. È il confine autorizzativo tra applicazioni/BI/AI e dati analitici.

Non espone SQL arbitrario, tabelle OMOP direttamente, payload raw operativi o credenziali database. Nessuna chiamata analitica partecipa al percorso di ACK, routing o allerta clinica. Le regole comuni sono in [api-standards.md](./api-standards.md); modello e pipeline sono in [omop-architecture.md](../omop/omop-architecture.md) e [omop-projection-and-etl.md](../omop/omop-projection-and-etl.md).

## 2. Principi

1. **Dataset immutabile per analisi riproducibile.** Ogni esecuzione pinna una DatasetVersion `READY`.
2. **Purpose e permit prima della query.** Autenticazione non equivale ad autorizzazione d'uso.
3. **No raw SQL.** Solo definizioni tipizzate, template qualificati e parametri validati.
4. **Privacy by default.** Risultati aggregati, minimizzazione e disclosure control prima dell'export.
5. **Lineage obbligatorio.** Ogni risultato collega dataset, vocabolari, definizione, engine e parametri.
6. **Job asincroni e cancellabili.** Le analisi costose non occupano request thread.
7. **Workload isolation.** Query interattive, batch, export e tool OHDSI hanno pool/quote separati.
8. **Tool compatibility verificata.** CDM 5.5 non implica supporto completo di ogni feature in ogni tool.
9. **Nessuna decisione clinica implicita.** Un risultato di ricerca non diventa alert, diagnosi o terapia senza prodotto/processo distinto e qualificato.
10. **Federazione esplicita.** Analisi multi-azienda richiede permit e restituisce solo aggregati consentiti; non unisce record individuali per default.

## 3. Contesto europeo 2026

Nel 2026 l'EHDS è in vigore ma l'applicazione è scaglionata e gli atti di esecuzione continuano a definire dettagli. HHC prepara policy, permit, dataset catalog, provenance, secure processing ed export controllato; non dichiara automaticamente conformità EHDS né assume il ruolo di Health Data Access Body.

Il deployment registra ruoli concreti — titolare/responsabile, health data holder, data user, HDAB o equivalenti — sulla base del caso e della giurisdizione. GDPR, EHDS, diritto nazionale, segreto professionale, etica e contratto determinano se una finalità è consentita.

## 4. Topologia

```text
Client / BI / Research App / AI Tool
                 │
        API Gateway + OAuth/OIDC
                 │
          Policy + Permit PDP
                 │
      Clinical Analytics API
       │       │        │
 Definition  Job/Quota  Disclosure
 Registry    Scheduler   Control
       │       │        │
       └── Governed Query Engine ── DatasetVersion READY
                              ├── OMOP CDM 5.5
                              ├── Vocabulary snapshot
                              └── Lineage/evidence
```

Il query engine usa credenziali read-only per DatasetVersion. Definition registry e job store non hanno privilegi di scrittura sul CDM pubblicato. Export worker ha storage egress separato e policy più restrittiva.

## 5. Scope e authorization

La decisione è l'intersezione di:

- subject/workload e ruolo;
- tenant, organization e data source;
- DatasetVersion;
- purpose of use e permit/approval;
- popolazione/coorte ammessa;
- domini/campi e granularità;
- località di elaborazione e data residency;
- periodo, volume, export e destinatario;
- disclosure policy e obligations.

Esempio di context:

```json
{
  "subjectId": "researcher-opaque",
  "tenantId": "t-...",
  "datasetVersionId": "dsv-...",
  "purpose": "approved-protocol-2026-041",
  "permitId": "permit-...",
  "requestedAction": "COHORT_GENERATE",
  "requestedFields": ["condition_concept_id", "visit_start_date"]
}
```

Le list API filtrano prima della paginazione. Per ridurre enumeration, risorse non autorizzate risultano non trovate. Accesso a person-level data richiede capability separata, ambiente controllato e audit ad alta severità.

## 6. Catalogo delle risorse

| Risorsa | Scopo |
|---|---|
| DataSource | fonte logica governata, owner, country, residency |
| DatasetVersion | snapshot analitico immutabile e qualificato |
| VocabularySnapshot | release OMOP/terminologie e digest |
| Concept | vista governata dei concetti del snapshot |
| ConceptSetDefinition | definizione versionata e risolvibile |
| CohortDefinition | logica di inclusione/esclusione versionata |
| CohortGeneration | popolazione materializzata per dataset/definizione |
| FeatureDefinition | feature computabile e semantica |
| FeatureRun | matrice/aggregato prodotto |
| QualityRun | DQD e controlli HHC |
| CharacterizationRun | profilo statistico del dataset/coorte |
| LineageQuery/Export | provenienza e impact |
| AnalysisJob | esecuzione asincrona comune |
| ResultManifest | parti, checksum, schema e policy di risultato |
| AccessPermit | reference alla decisione/approvazione, non documento legale completo |

## 7. DatasetVersion

Stati vincolanti:

`BUILDING → VALIDATING → READY → SUPERSEDED → ARCHIVED`

`FAILED` è terminale per quel tentativo. Solo `READY` è interrogabile dalle API analitiche standard. `SUPERSEDED` resta interrogabile per riproduzione se policy/retention lo consentono; `ARCHIVED` richiede restore job.

```json
{
  "datasetVersionId": "dsv-2026q3-01",
  "dataSourceId": "ds-hospital-group-a",
  "status": "READY",
  "omopCdmVersion": "5.5",
  "vocabularySnapshotId": "vocab-2026-08",
  "sourceCutoff": "2026-08-31T23:59:59Z",
  "publishedAt": "2026-09-01T08:00:00Z",
  "manifestDigest": "sha256:...",
  "qualitySummary": {"fatal": 0, "errors": 0, "warnings": 17},
  "lineageRef": "lineage://dataset/dsv-2026q3-01"
}
```

Invarianti:

- dataset, vocabulary, schema e ETL release sono immutabili;
- primary key e conteggi sono riconciliati;
- source cutoff, freshness e missing windows sono visibili;
- quality exception è motivata, approvata e non nasconde finding;
- una nuova pubblicazione crea nuovo ID/versione;
- query già avviata continua sul dataset pinnato o fallisce esplicitamente, mai migrazione silenziosa.

## 8. Data source API

```text
GET /api/v1/analytics/data-sources
GET /api/v1/analytics/data-sources/{id}
GET /api/v1/analytics/data-sources/{id}/dataset-versions
GET /api/v1/analytics/dataset-versions/{id}
GET /api/v1/analytics/dataset-versions/{id}/capabilities
```

La response pubblica solo metadata consentiti: owner, geography, CDM/version, domains, date coverage, refresh cadence, quality status, population summary controllato, access process e limitations. Non espone connection string, schema fisico o counts che violano disclosure policy.

## 9. Vocabulary e concept API

Endpoint:

```text
GET  /api/v1/analytics/vocabulary-snapshots/{id}
GET  /api/v1/analytics/concepts/{conceptId}?vocabularySnapshotId=...
POST /api/v1/analytics/concepts:search
POST /api/v1/analytics/concepts:ancestors
POST /api/v1/analytics/concepts:descendants
POST /api/v1/analytics/concepts:relationships
POST /api/v1/analytics/concepts:map-source
```

Ogni response include snapshot, vocabulary, concept ID/code, validity, standard/class/domain, relationship e source attribution. Search ha exact/prefix/fuzzy espliciti, language, vocabulary/domain filters, page/cost limit e sort deterministico.

Il servizio non assume che tutti i contenuti siano liberamente redistribuibili. License entitlement può limitare display, export e country. Per interoperabilità FHIR, adapter può esporre `$lookup`, `$validate-code`, `$expand`, `$subsumes` e `$translate` secondo capability dichiarata; l'API HHC non finge piena equivalenza quando la semantica OMOP differisce.

## 10. Concept set

### 10.1 Definition

```json
{
  "conceptSetDefinitionId": "csd-diabetes-01",
  "version": "2.1.0",
  "name": "Diabetes mellitus",
  "vocabularySnapshotId": "vocab-2026-08",
  "items": [
    {
      "conceptId": 201826,
      "includeDescendants": true,
      "includeMapped": false,
      "exclude": false
    }
  ],
  "status": "APPROVED"
}
```

Definition è immutabile dopo approvazione. Expansion è una risorsa/job con input digest, engine version, included/excluded concept, count, checksum e warnings. Concept inattivi non sono sostituiti silenziosamente.

Endpoint:

```text
POST /api/v1/analytics/concept-set-definitions
POST /api/v1/analytics/concept-set-definitions/{id}:validate
POST /api/v1/analytics/concept-set-expansion-jobs
GET  /api/v1/analytics/concept-set-expansions/{id}
POST /api/v1/analytics/concept-set-definitions/{id}:compare
```

## 11. Cohort API

### 11.1 CohortDefinition

La definizione usa un AST/schema HHC versionato, non SQL. Rappresenta entry event, inclusion rule, temporal window, exit strategy, observation requirement e concept set reference.

```json
{
  "cohortDefinitionId": "cohort-cvd-01",
  "version": "1.3.0",
  "title": "Cardiovascular cohort",
  "logicSchemaVersion": "hhc-cohort-1.0",
  "conceptSetRefs": ["conceptset://csd-cvd@2.0.0"],
  "definition": {},
  "intendedUse": "research",
  "status": "APPROVED"
}
```

Validate produce schema/invariant findings, concept resolution, unsupported construct, estimated cost e privacy risk. Compile genera SQL o piano per dialect internamente, conserva digest e impedisce injection.

### 11.2 Generation

```text
POST /api/v1/analytics/cohort-generation-jobs
GET  /api/v1/analytics/cohort-generations/{id}
GET  /api/v1/analytics/cohort-generations/{id}/summary
POST /api/v1/analytics/cohort-generations/{id}:compare
```

Request pinna cohort definition, DatasetVersion, permit e parameter. Output standard è count/summary; person-level membership non è restituita salvo capability e secure environment specifici. Rerun con stessi input/engine produce stesso digest, al netto di elementi esplicitamente non deterministici vietati nel core.

## 12. Feature API

FeatureDefinition dichiara:

- population/cohort input;
- temporal anchor/window;
- domain/concept set;
- aggregation e unità;
- missingness e censoring;
- output type e range;
- privacy class;
- versioni engine/schema.

FeatureRun è asincrono e genera aggregate, person-level matrix autorizzata o artifact per model development. Non consente codice utente arbitrario nella baseline. Estensioni custom girano in sandbox/secure enclave, con image digest, SBOM, egress deny, resource limit e review.

## 13. Data quality API

QualityRun comprende Data Quality Dashboard più controlli HHC:

- conformance a schema/convention;
- completeness;
- plausibility temporale, fattuale e di valore;
- source-to-CDM reconciliation;
- duplicate/key/domain/unit;
- trend/drift rispetto a versioni precedenti;
- lineage/orphan/gap;
- privacy e forbidden value scan.

```text
POST /api/v1/analytics/quality-run-jobs
GET  /api/v1/analytics/quality-runs/{id}
GET  /api/v1/analytics/quality-runs/{id}/checks
GET  /api/v1/analytics/quality-runs/{id}/trend
POST /api/v1/analytics/quality-runs/{id}/evidence-export-jobs
```

Ogni finding ha check ID/version, category, table/field, threshold, observed, severity, status, sample policy, waiver e owner. Le soglie DQD non sono universali: sono motivate per data source e caso d'uso. Un fatal applicabile impedisce `READY`; waiver non elimina il finding.

## 14. Characterization API

Produce profili controllati:

- person/visit/event counts;
- observation period e temporal density;
- domain/concept prevalence;
- age/sex/geography solo a granularità consentita;
- missingness, source value distribution e trend;
- vocabulary/concept coverage;
- cohort diagnostics.

La response applica minimum-cell, suppression/rounding/noise o altre regole stabilite dalla disclosure policy. Non esiste una soglia unica incorporata: policy, permit e giurisdizione la determinano. Differencing attack è mitigato limitando query equivalenti, budget, history e combinazioni di filtri.

## 15. Lineage API

```text
POST /api/v1/analytics/lineage:upstream
POST /api/v1/analytics/lineage:downstream
POST /api/v1/analytics/lineage:explain
POST /api/v1/analytics/lineage:impact
POST /api/v1/analytics/lineage-export-jobs
```

Lineage collega result → job → definition → DatasetVersion → ETL/mapping/terminology → CSE/raw reference. La query è scoped, bounded e redatta. Il ricercatore vede provenienza sufficiente alla riproduzione, non payload clinico o infrastruttura non autorizzata.

Explain include trasformazioni, versioni, quality finding rilevanti e known semantic loss. L'indice lineage è ricostruibile dalla fonte append-only; un indice in ritardo segnala freshness e non restituisce completezza falsa.

## 16. AnalysisJob

```json
{
  "jobId": "aj-...",
  "jobType": "COHORT_GENERATION",
  "status": "RUNNING",
  "datasetVersionId": "dsv-...",
  "definitionRef": "cohort://...@1.3.0",
  "permitId": "permit-...",
  "inputDigest": "sha256:...",
  "engine": {"name": "hhc-analytics", "version": "1.0.0"},
  "progress": {"phase": "EXECUTING", "percent": 42},
  "createdAt": "2026-09-01T10:00:00Z",
  "expiresAt": "2026-09-08T10:00:00Z"
}
```

Stati: `QUEUED`, `VALIDATING`, `RUNNING`, `FINALIZING`, `SUCCEEDED`, `FAILED`, `CANCELLING`, `CANCELLED`, `EXPIRED`. Scheduler applica queue per tenant/purpose/class, concurrency e cost budget. Job ha deadline, checkpoint, cancel, retry classification e cleanup. Un job fallito non pubblica artifact parziale come risultato valido.

## 17. ResultManifest ed export

```json
{
  "resultManifestId": "rm-...",
  "jobId": "aj-...",
  "schemaRef": "schema://cohort-summary@1.0.0",
  "classification": "PSEUDONYMISED",
  "parts": [
    {"name": "part-0001.parquet", "bytes": 12345, "records": 1000, "sha256": "..."}
  ],
  "disclosureDecisionId": "dd-...",
  "expiresAt": "2026-09-03T10:00:00Z"
}
```

Download usa one-time/presigned URL con TTL e recipient binding, o transfer gestito. Manifest è firmato; ogni part ha checksum. Export include data use notice, purpose, limitations, dataset/definition citations e deletion/return obligation. Revoca blocca nuovi download ma avvia un workflow distinto per copie già consegnate.

FHIR population transfer può adottare Bulk Data 3.0.0 con NDJSON e manifest secondo profilo qualificato; non è il formato universale HHC. Parquet/CSV sono ammessi con schema/dialect/encoding espliciti e protezione contro formula injection nei consumer spreadsheet.

## 18. Integrazione OHDSI

OHDSI WebAPI può essere adapter/tool dietro il confine HHC per vocabulary, concept set, cohort e characterization. Non è esposto direttamente a internet o ai tenant perché:

- authorization HHC deve precedere ogni chiamata;
- source key e DB connection sono interni;
- request/response devono essere validate/minimizzate;
- query/job devono rispettare quota e purpose;
- capability effettive dipendono da versione e qualifica.

Al 1 settembre 2026 la release ufficiale più recente rilevata di OHDSI WebAPI è **2.15.2**. Questa informazione è inventory, non un pin automatico. OMOP CDM 5.5 è baseline, ma il sito CommonDataModel segnala che il supporto delle nuove feature 5.5 non è completamente testato/rilasciato per tutti gli strumenti. La support matrix HHC registra funzione per funzione.

## 19. API per AI e agenti

Agenti accedono esclusivamente a tool tipizzati:

- search concept;
- validate concept set;
- submit cohort/feature/quality job;
- read aggregate result;
- explain lineage;
- request export tramite approval.

Sono vietati raw SQL, arbitrary code, autonomous write sul CDM, accesso raw e ampliamento automatico dello scope. Ogni chiamata registra agent/service version, user/delegation, purpose, input digest, tool, output reference e necessità di human review. Output non deve essere presentato come consiglio diagnostico/terapeutico nella baseline HHC.

## 20. Privacy e disclosure control

Controlli:

- attribute/row/domain authorization;
- pseudonymization key separata per tenant/purpose;
- cohort size e small-cell policy configurabili;
- suppression, rounding, top/bottom coding o privacy technique approvata;
- query history per differencing/composition risk;
- rate/cost/privacy budget;
- safe output review per person-level/custom analysis;
- egress allowlist e watermark;
- result TTL, revocation e deletion evidence;
- no PHI in log/metric/trace/error;
- privacy canary in test.

La pseudonimizzazione non rende automaticamente anonimi i dati. Re-identification attempt e linkage esterno sono vietati salvo base/permit specifici e ambiente controllato.

## 21. Sicurezza

- OIDC/OAuth secondo RFC 9700; MFA per utenti privilegiati;
- workload token audience-specific e sender-constrained per export/service;
- authorization per oggetto e proprietà;
- template/AST allowlist, parameter binding e SQL compiler testato;
- query timeout, statement limit, memory/temp quota e read-only transaction;
- egress deny dal query worker salvo servizi autorizzati;
- result storage cifrato e segregato;
- secret in secret manager, mai nella definition;
- SBOM/signing per engine/custom runner;
- anomaly detection su bulk access, query pattern ed export.

## 22. Performance e workload management

Classi:

| Classe | Esempio | Priorità | Limite |
|---|---|---:|---|
| Interactive metadata | catalog/concept exact | alta | response bounded |
| Interactive aggregate | summary già materializzata | alta | timeout breve |
| Standard job | cohort/quality/feature | media | queue/concurrency tenant |
| Heavy job | full characterization/export | bassa | window/pool separato |
| Custom secure job | codice qualificato | isolata | quota dedicata |

Query planner stima costo prima dell'esecuzione. Limit, timeout, scanned bytes/rows, temp storage e concurrency sono enforced server-side. Cache key include tenant, permit class, DatasetVersion, definition digest e disclosure policy; nessuna response passa tra scope.

Analytics può essere sospeso per proteggere Tier 0. ETL e query pesanti non condividono pool, I/O quota o connection pool con il percorso clinico.

## 23. Observability

Metriche senza PHI:

- API RPS/error/p50/p95/p99;
- job queue depth/oldest age/start/completion/failure/cancel;
- runtime e scanned byte bucket;
- query timeout/resource deny;
- cache hit/freshness;
- permit deny/expiry;
- disclosure suppression/review count;
- export byte/download/failure/revoke;
- DatasetVersion freshness/status;
- DQD fatal/error/warning e trend;
- lineage freshness/gap;
- per-tenant fair-share saturation.

Alert: access anomaly, query bypass attempt, export non autorizzato, result integrity failure, DQD fatal su candidate, stale dataset oltre policy, runaway query, pool saturation, lineage gap e restore failure.

## 24. Audit ed evidence

Audit obbligatorio per definition create/change/approve, job submit/cancel, permit decision, person-level access, custom runner, result view/download, export/revoke, suppression override e admin action.

Event fields: actor/workload, delegation, tenant/data source/dataset, purpose/permit, action, definition/input/result digest, policy/disclosure decision, time, outcome e correlation. Non include record/patient detail.

Evidence di riproduzione:

- DatasetVersion/manifest;
- vocabulary snapshot;
- definition e concept expansion;
- engine/container digest e config;
- parameters/seed;
- quality state;
- result manifest/checksum;
- permit e disclosure decision references.

## 25. Business continuity e DR

Tier 2: SLO 99,9%, RTO ≤8 ore, RPO ≤1 ora.

- API stateless multi-zona;
- registry/job metadata HA e PITR;
- DatasetVersion READY con backup/replica e checksum;
- result artifact replicati secondo classification/TTL;
- checkpoint di job restartable;
- vocabulary/definition artifact content-addressed;
- clean-room restore e integrity validation;
- query access riaperto solo dopo dataset, policy, key, lineage e DQD smoke check.

In outage, job può restare `UNKNOWN` finché reconciliation non determina checkpoint/result. Non viene marcato `SUCCEEDED` senza manifest completo. Se Analytics è down, flussi clinici continuano senza impatto.

## 26. Testing e qualification

- contract/auth object/field/purpose matrix;
- SQL/AST injection e unsupported construct;
- cross-tenant/data source/result leakage;
- determinismo definition/expansion/generation;
- DQD e reconciliation source→CDM;
- disclosure/small-cell/differencing attack;
- quota/fairness/runaway query;
- job crash/checkpoint/cancel/duplicate;
- result manifest/checksum/expiry/revoke;
- tool compatibility per OHDSI/CDM feature;
- backup/restore/RTO/RPO;
- PHI leakage canary;
- accessibility dei workflow researcher/steward.

## 27. Casi d'uso sanitari europei

### ANL-UC-01 — Sorveglianza epidemiologica multifacility

Un ente autorizzato richiede trend aggregati da più facility.

**Esito:** permit per scope/purpose; DatasetVersion pin; coorte/feature versionate; minimum-cell; aggregati senza linkage individuale; lineage ed export firmato.

### ANL-UC-02 — Studio multicentrico federato

Tre aziende eseguono la stessa cohort definition mantenendo dati localmente.

**Esito:** definition/vocabulary digest comuni; job locali; aggregate-only return; quality comparability; site result manifest; nessun person row trasferito.

### ANL-UC-03 — Farmacovigilanza

Analisi di esposizione farmacologica ed evento avverso con ATC/SNOMED/OMOP.

**Esito:** concept set review; finestre temporali; source concept preservation; confounder feature; quality limitations; nessun causal claim automatico.

### ANL-UC-04 — Migrazione vocabolario

Una nuova release inattiva concetti usati da coorti pubblicate.

**Esito:** vecchi run riproducibili; impact/diff; nuova expansion separata; nessun remap silenzioso; reviewer approval e comparison report.

### ANL-UC-05 — Patient-level data in secure environment

Un protocollo approvato necessita dati pseudonimizzati individuali.

**Esito:** capability/permit dedicati; enclave; no internet egress; output review; key separata; TTL/deletion evidence; audit completo.

### ANL-UC-06 — FHIR Bulk Data import/export

Un partner autorizzato scambia popolazione FHIR R4 via Bulk Data 3.0.0.

**Esito:** backend authorization; manifest/NDJSON; checksum; profile validation; throttling; lineage; error files; nessun impatto al FHIR clinico.

### ANL-UC-07 — Quality gate trimestrale

Un nuovo dataset presenta calo improvviso delle misure laboratorio.

**Esito:** trend detector; DQD finding; publication bloccata; source reconciliation; owner/remediation; nuova build senza nascondere il run fallito.

### ANL-UC-08 — AI assistant per data steward

Un assistente propone concept set e avvia validation.

**Esito:** tool tipizzati; scope delegato; nessun SQL; proposta non approvata automaticamente; input/output digest; human review; no decisione clinica.

### ANL-UC-09 — Revoca del permit

Un'autorizzazione scade mentre un export è in coda.

**Esito:** pre-execution recheck; job cancellato; URL non emesso/revocato; copie già scaricate gestite da workflow; audit e notifica.

### ANL-UC-10 — Disaster recovery analytics

Si perde il sito analitico durante una characterization.

**Esito:** clinico non impattato; restore entro 8 ore/RPO 1 ora; DatasetVersion checksum; job restart da checkpoint o nuovo run; result parziale mai pubblicato.

## 28. Anti-pattern vietati

- connection string o SQL esposto;
- query su dataset BUILDING/FAILED;
- alias “latest” in un'analisi pubblicata;
- person-level default;
- soglia small-cell universale non governata;
- cache condivisa tra tenant/permit;
- OHDSI WebAPI esposta direttamente come boundary di sicurezza;
- job pesante nel request thread;
- result senza manifest/digest;
- AI con write autonomo o consiglio clinico implicito;
- pseudonimizzato dichiarato anonimo;
- Analytics dipendenza dell'ACK clinico.

## 29. Fonti ufficiali e data di verifica

Fonti verificate il **1 settembre 2026**:

- OHDSI OMOP Common Data Model 5.5 e support matrix: <https://ohdsi.github.io/CommonDataModel/>
- OHDSI Data Quality Dashboard, check index: <https://ohdsi.github.io/DataQualityDashboard/articles/checkIndex.html>
- OHDSI WebAPI repository: <https://github.com/OHDSI/WebAPI>
- OHDSI WebAPI release 2.15.2, pubblicata il 24 giugno 2026: <https://github.com/OHDSI/WebAPI/releases>
- HL7 FHIR R5 Terminology Module: <https://www.hl7.org/fhir/terminology-module.html>
- HL7 FHIR Bulk Data Access 3.0.0, current published STU 3: <https://hl7.org/fhir/uv/bulkdata/>
- SMART App Launch 2.2.0, current published version: <https://hl7.org/fhir/smart-app-launch/>
- Regolamento (UE) 2025/327 EHDS: <https://eur-lex.europa.eu/eli/reg/2025/327/oj/>
- Commissione europea, EHDS e calendario: <https://health.ec.europa.eu/ehealth-digital-health-and-care/european-health-data-space-regulation-ehds_en>
- Regolamento (UE) 2016/679 GDPR: <https://eur-lex.europa.eu/eli/reg/2016/679/oj/>

Le versioni runtime reali sono fissate nel BOM e nel Conformance Pack; una release “latest” consultata qui non viene adottata senza qualification.
