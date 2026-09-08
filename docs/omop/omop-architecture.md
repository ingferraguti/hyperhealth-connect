# Architettura OMOP enterprise

Stato: baseline di produzione 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Baseline CDM: OMOP CDM 5.5  
Target: istanze analitiche europee multi-azienda, multi-facility e multicentriche

## 1. Decisione architetturale

OMOP è una proiezione analitica asincrona e versionata; non è il modello canonico, non è il system of record clinico e non entra nel percorso di ACK/delivery. Il sito OHDSI ufficiale indica CDM 5.5 come versione corrente. HHC fissa DDL, conventions, vocabulary e tooling per dataset release: nessuna dipendenza `latest`.

La pagina OHDSI segnala al 1 settembre 2026:

- CDM R package: legacy e feature support 5.5 rilasciati;
- DataQualityDashboard, Achilles, ARES, ATLAS, WhiteRabbit/Rabbit-in-a-Hat, FeatureExtraction e CohortDiagnostics: legacy support rilasciato, feature support 5.5 ancora segnalato come non interamente testato/rilasciato.

Di conseguenza, la baseline di schema è 5.5, ma ogni tool deve superare una compatibility qualification HHC. Un dataset può essere CDM 5.5 valido senza essere utilizzabile con tutte le feature 5.5 da ogni tool.

## 2. Principi

1. Dataset version immutabile dopo `READY`.
2. Raw/CSE/mapping/terminology lineage per ogni load.
3. Source values preservati secondo conventions.
4. Standard concepts dallo snapshot vocabulary dichiarato.
5. Clinical delivery e OMOP failure sono isolati.
6. Tenant/organization/project isolation configurabile fino a cluster/account.
7. Pseudonimizzato non significa anonimo.
8. Quality è fitness-for-use, non un singolo score.
9. Tool compatibility è provata, non dedotta dal nome CDM.
10. Dataset publication è atomica e reversibile tramite alias/catalog, non update in-place.

## 3. Topologia

```text
Operational Data Plane
  raw + envelope + CSE + lineage
             │ asynchronous watermark
             ▼
OMOP Build Zone (isolata per dataset)
  landing → staging → identity tokenization → domain ETL
           → vocabulary mapping → derived tables → validation
             │ quality gates
             ▼
Immutable Dataset Version
  omop_cdm + omop_vocab + results + metadata
             │ atomic catalog publication
             ▼
Secure Analytics / Federated Execution
```

Build worker non accede direttamente al sistema sorgente se il data plane fornisce eventi; estrazioni batch legacy hanno connector e manifest dedicati. Nessun analista scrive in `omop_cdm`.

## 4. IsolationProfile

| Profilo | Impiego | Confine |
|---|---|---|
| schema-per-dataset | sviluppo/low risk | role+schema+RLS difensiva |
| database-per-organization | default enterprise | DB, credential, backup e key separati |
| cluster/account-per-tenant | regolato/critical | admin/failure domain separato |
| project enclave | studio/permit | dataset read-only e compute/output controls |
| federated local CDM | multicentrico | dati patient-level restano nel sito |

Un CDM enterprise unico è ammesso solo con base/purpose, identity resolution e governance comuni. Il default è istanza per organization o data-holding boundary; `CARE_SITE` non è un meccanismo di tenancy.

## 5. Schemi

```text
omop_cdm_<version>    sole tabelle ufficiali CDM 5.5
omop_vocab_<version>  vocabulary tables dello snapshot
omop_results          DQD, Achilles, characterization, cohorts/results
omop_work_<run>       staging/temp per build, non visibile agli analisti
hhc_omop_metadata     manifest, watermark, lineage bridge, quality, permits
hhc_omop_audit        indice operativo; journal audit separato
```

Estensioni HHC non sono aggiunte alle tabelle CDM né presentate agli OHDSI tool come standard. Sono in schema separato con view documentate. Tutte le tabelle richieste dalla 5.5 esistono anche se vuote.

## 6. DatasetVersion

Stati:

```text
PLANNED → BUILDING → VALIDATING → READY → SUPERSEDED → ARCHIVED
                ↘ FAILED
```

Manifest obbligatorio:

```yaml
datasetId: opaque
datasetVersion: immutable
cdmVersion: "5.5"
ddlDigest: sha256
vocabularyVersion: exact-from-VOCABULARY
vocabularySnapshotId: immutable-ref
sourceOrganizations: [opaque]
sourceFacilities: [opaque]
sourcePeriod: {from, to}
inputWatermark: exact
mappingReleases: [exact]
terminologySnapshot: exact
etlBuild: image-digest
databaseDialect: exact
rowCounts: artifact-ref
checksums: artifact-ref
qualityPolicy: exact
dqdResult: artifact-ref
characterizationResult: artifact-ref
permitRefs: [opaque]
pseudonymisationProfile: exact
publishedAt: instant
approvedBy: [role-reference]
```

`CDM_SOURCE.cdm_version`, `cdm_version_concept_id`, `vocabulary_version`, ETL reference e source/release dates sono popolati coerentemente col manifest.

## 7. Identity e privacy

`person_id` è surrogate scoped al dataset. Cross-facility linkage avviene prima dell'ETL tramite identity service autorizzato e qualità misurata; niente probabilistic merge nel CDM senza governance. Token diversi per tenant/purpose riducono linkage indebito. Source identifiers sensibili sono cifrati/tokenizzati; source value OMOP è minimizzato secondo fitness e policy.

Date shifting e generalizzazione sono profili versionati e coerenti per persona/dataset. La chiave di re-identificazione è in trust domain separato e non disponibile agli analisti. Small-cell e output disclosure controls si applicano anche a query federate.

## 8. Vocabulary architecture

Vocabulary è scaricato tramite Athena/authorized source, verificato, caricato in schema immutabile e associato al dataset. Include validity e standard concept status. Custom concepts seguono conventions OHDSI e governance locale; non sovrascrivono concept ufficiali.

Aggiornare vocabulary senza ricostruire/qualificare il dataset è vietato. Cohort definitions e analyses dichiarano vocabulary version. Mapping source→standard conserva source concept/value; concept ID 0 è usato solo secondo conventions e monitorato.

## 9. Storage e prestazioni

DB analytical column/row-store compatibile con OHDSI è dimensionato su table/domain, concurrency e workload. Partitioning fisico privilegia date/person/domain senza rompere DDL/API; indici/statistiche sono environment-specific e versionati come deployment config. Work schema separa spill/temp.

Workload class:

- ETL bulk write;
- DQD/characterization scan;
- cohort generation;
- interactive ATLAS/WebAPI;
- federated packaged analysis;
- data export autorizzato.

Resource group/queue impediscono che una query ad hoc esaurisca ETL o altri tenant. Query timeout, concurrency, memory, temp quota e result limit sono obbligatori. Read replica o warehouse dedicato serve analytics intensivo.

## 10. Alta disponibilità e disaster recovery

OMOP, analytics, batch e catalogazione sono Tier 2 di default: SLO 99,9%, RTO ≤8 ore e RPO ≤1 ora per dataset/catalog/results. Uno studio o servizio può imporre un tier più stringente tramite contratto e capacity/DR qualification dedicati. Dataset `READY` immutabili possono essere replicati tramite snapshot/object/database backup; build intermedi sono ricostruibili ma manifest e checkpoint sono protetti.

Business continuity:

- catalog/alias HA e rollback al predecessor;
- read replica/failover testato;
- backup cifrato, immutabile e cross-site;
- cyber-vault e clean-room restore;
- vocabulary/mapping/container artifact inclusi;
- quarterly restore sample e almeno annual full DR per profilo;
- reconciliation di row count/checksum/DQD dopo restore.

Il recovery non dichiara `READY` finché DDL, vocabulary, manifest, quality results e access policy non sono coerenti.

## 11. Sicurezza

- analyst role read-only; ETL role solo work/target build;
- schema/database isolation e deny cross-tenant;
- MFA/federated identity, JIT e purpose-based access;
- network private, egress deny, secret vault e TLS;
- query/activity audit, break-glass e export approval;
- backup key separation e restore role distinto;
- container/SBOM/signature/CVE gate per OHDSI tools;
- no PHI in SQL logs, error telemetry o job names.

## 12. Audit e monitoraggio

Audit: dataset create/build/validate/publish/supersede/archive, access grant, query/job, cohort execution, export, output review, vocabulary/mapping change e break-glass. Query text può essere sensibile: è cifrato/scoped, con hash per correlazione.

Metriche: load throughput, watermark lag, row/domain counts, concept 0/non-standard rate, DQD severity, query p95/p99, concurrency/queue, temp/storage headroom, replication/backup lag, restore status, grant expiry e unusual export. SOC/NOC ricevono alert senza patient-level detail.

## 13. EHDS e uso secondario

Nel 2026 OMOP supporta preparazione EHDS ma non è formato EHDS obbligatorio né prova di conformità. Dataset per secondary use richiede data permit/purpose, minimizzazione, pseudonimizzazione, secure processing environment, expiry/revocation e output review secondo diritto applicabile. General EHDS application parte nel 2027 e le principali disposizioni secondary-use dal marzo 2029.

## 14. Casi d'uso europei 2026

### UC-OMOP-ARCH-01 — gruppo multi-azienda

Tre aziende mantengono CDM separati con vocabulary/mapping comuni. Un catalogo espone metadati e quality, non righe. Accettazione: isolamento DB/key, versioni comparabili, nessun `CARE_SITE` usato come security boundary.

### UC-OMOP-ARCH-02 — studio federato

Un package firmato gira localmente. Accettazione: dataset/vocabulary/tool version compatibili, output small-cell controlled, audit per sito e nessun patient-level export.

### UC-OMOP-ARCH-03 — DR regionale

Il sito primario è perso. Il secondario ripristina dataset READY, results, catalog e grants. Accettazione: RTO/RPO, checksum, DQD sample e access policy verificati prima del servizio.

### UC-OMOP-ARCH-04 — upgrade 5.4→5.5

Nuovo dataset 5.5 è costruito accanto al 5.4. Tool qualification verifica feature support; alias cambia solo dopo gate. Accettazione: predecessor disponibile, semantic/data diff e rollback.

## 15. Gate architetturale

- DDL ufficiale 5.5 e schema purity;
- vocabulary exact e licensing;
- isolation/threat/privacy assessment;
- tool compatibility matrix eseguita;
- performance/capacity/concurrency;
- HA/failover/backup/restore/cyber recovery;
- DQD/characterization e quality policy;
- lineage, manifest, audit e permit;
- publication atomic e rollback.

## 16. Fonti ufficiali verificate

- [OHDSI OMOP CDM — current version e tool support](https://ohdsi.github.io/CommonDataModel/), consultato il 1 settembre 2026; current CDM 5.5 e matrice di supporto verificati.
- [OMOP CDM 5.5 specification](https://ohdsi.github.io/CommonDataModel/cdm55.html), consultata il 1 settembre 2026.
- [OHDSI CommonDataModel repository e DDL](https://github.com/OHDSI/CommonDataModel), consultato il 1 settembre 2026.
- [OHDSI Athena vocabularies](https://athena.ohdsi.org/), consultato il 1 settembre 2026.
- [Regolamento EHDS](https://eur-lex.europa.eu/eli/reg/2025/327/oj/) e [calendario Commissione](https://health.ec.europa.eu/ehealth-digital-health-and-care/european-health-data-space-regulation-ehds_en), consultati il 1 settembre 2026.

## 17. Collegamenti

- `omop-projection-and-etl.md`
- `analytics-and-ohdsi-compatibility.md`
- `../architecture/data-architecture.md`
- `../semantic/lineage-and-provenance.md`
