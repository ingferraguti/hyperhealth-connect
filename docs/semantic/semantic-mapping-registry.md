# Semantic Mapping Registry

Stato: specifica baseline 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Target: registro enterprise multi-tenant dei mapping concettuali

## 1. Scopo

Il Registry conserva e pubblica mapping tra code system e value set, con contesto, relazione, versione, evidence e lifecycle. È il system of record HHC per le release di mapping approvate; non è l'autorità dei code system sorgente/target e non sostituisce il Terminology Service.

## 2. Modello dati

```yaml
mapSet:
  id: canonical-uri
  version: exact
  sourceScope: value-set-uri|version
  targetScope: value-set-uri|version
  purpose: clinical-exchange|analytics|reporting|display
  jurisdiction: EU|country|organization
  status: draft|approved|deprecated|retired
  effectivePeriod: {start, end?}
  terminologyDependencies: [snapshot-id]
  licenseRefs: [evidence-id]
  entries:
    - source: {system, version, code}
      target: {system, version, code}
      relationship: equivalent|source-is-broader|source-is-narrower|related|not-related
      dependsOn: [{property, value}]
      products: []
      rule: optional-expression
      noMap: false
      confidence: reviewed-score
      status: proposed|approved|rejected
      reviewer: role-reference
      evidence: [reference]
      validPeriod: {start, end?}
```

Label/display sono descrittivi e multilingual; identity è system+version+code. `purpose` impedisce il riuso improprio: una mappa per reporting statistico non è automaticamente valida per decision support o interoperabilità clinica.

## 3. Relazioni e ambiguità

Equivalenza è dichiarata solo se il significato nel contesto è sostituibile. Broader/narrower richiede che il consumer ammetta perdita/aumento di generalità. `related` non abilita conversione automatica. `not-related`/no-map impedisce fallback errato.

Mapping uno-a-molti o condizionali richiedono `dependsOn` con specimen, method, body site, route, dose form, unit, age, sex o status quando semanticamente necessari. Se il contesto manca, risposta è ambiguous, non il primo target.

## 4. API

API interne tipizzate:

- resolve map release per scope/purpose/effective time;
- translate source→target e reverse solo se autorizzato;
- bulk translate asincrono;
- diff release;
- impact query map→flow/dataset;
- export FHIR ConceptMap quando rappresentabile;
- import candidato con validation, mai auto-publish.

La semantica è compatibile con FHIR ConceptMap/`$translate` dove applicabile. Risposta include result, tutte le match, relationship, context richiesto, original map, release, terminology snapshot e warnings. Cache key include tutti questi elementi.

## 5. Versioni e release

MapSet approvato è immutabile. Nuova entry o cambio relationship crea versione. Release bundle aggrega mapset compatibili e una lockfile di terminology snapshot. Effective date non è publish date. Le query storiche risolvono la release effettiva al tempo dell'elaborazione, non quella corrente.

Compatibilità:

- patch: metadata non semantici;
- minor: nuove entry senza cambiare outcome esistenti;
- major: target/relationship/rule/no-map modificati o scope ristretto.

## 6. Multi-tenancy

Layer: global standard reference → EU/country → tenant → organization → facility. Precedence è deterministica e ogni override cita l'entry sovrascritta. Mappe proprietarie non sono visibili cross-tenant. Condivisione richiede entitlement e license check. Il runtime riceve soltanto il sottoinsieme necessario alla cella.

## 7. Workflow di stewardship

Proposta → automated validation → terminology steward → domain/clinical review → data/use-case review → approval → signing → staging/canary → active. Segregazione e classi di rischio seguono `../mapping/mapping-governance.md`.

Automated validation controlla code existence/active status, domain, target standard concept quando OMOP, cycles, contradictory entries, duplicate conditions, unreachable rules, license/attribution e test coverage.

## 8. Quality e drift

Indicatori: coverage weighted by source frequency, no-map, ambiguous, inactive/deprecated source/target, context completeness, agreement inter-reviewer, escaped defects, mapping age e facility divergence. Frequency non rende corretto un mapping.

Ogni nuovo terminology snapshot produce diff: inactivation, replacement, hierarchy/domain change, new concept e designation change. Nessun target viene sostituito automaticamente per similarità testuale.

## 9. Performance e disponibilità

Runtime lookup è locale, read-only e precompilato; nessun database centrale nel Tier 0. Target p99 lookup cache <5 ms nelle condizioni dichiarate. Bulk e authoring usano Control/Semantic Plane separato. Bloom/index può accelerare negative lookup ma non sostituisce risposta autorevole.

Il Registry authoring ha SLO 99,9%, RTO ≤4 ore e RPO ≤15 minuti; i Runtime Cell continuano ≥24 ore con release firmata. Backup include DB, artifact, audit, signature, license evidence e test; restore verifica historical resolution.

## 10. Sicurezza e audit

RBAC/ABAC per tenant, purpose e ruolo; MFA per approvazioni; dual control per publish/rollback; artifact signature e immutable audit. API runtime è read-only. Export massivo è autorizzato/licensed e auditato. Terminology label/codes non sono sempre liberamente redistribuibili.

Audit: create/edit/import/review/approve/reject/sign/publish/activate/deprecate/export e accesso privilegiato. Ogni change conserva semantic diff, ticket, evidence e attore.

## 11. Casi d'uso europei 2026

### UC-SMR-01 — codici laboratorio multi-azienda

Ogni LIS ha source system distinto; mapset condiviso traduce a LOINC dove equivalente. Accettazione: versioni locali non collidono, specimen/method context applicato, no-map misurato per facility.

### UC-SMR-02 — ICD nazionale e ICD-11

Mapping è usato per reporting/transition, non per sostituire il significato clinico source. Accettazione: release WHO/nazionale, relationship e perdita documentate, nessuna inferenza bidirezionale.

### UC-SMR-03 — OMOP standard concepts

Il source concept è collegato allo standard concept del vocabulary snapshot. Accettazione: domain corretto, validity, source preservation e dataset version pinned.

### UC-SMR-04 — aggiornamento terminologico

LOINC/SNOMED update produce impact report e nuova map release. Accettazione: nessun effetto sui run storici, canary e rollback disponibili.

## 12. Test e gate

- schema, referential integrity e contradictory map;
- all relationships/dependsOn/no-map;
- FHIR ConceptMap export/import round-trip loss report;
- concurrency/cache determinism;
- tenant isolation e entitlement;
- performance/soak e Registry outage;
- historical restore e reproducibility;
- clinical golden set e reviewer approval.

## 13. Fonti ufficiali verificate

- [FHIR R5 ConceptMap](https://hl7.org/fhir/R5/conceptmap.html) e [`$translate`](https://hl7.org/fhir/R5/conceptmap-operation-translate.html), consultate il 1 settembre 2026.
- [OHDSI OMOP CDM/Vocabularies](https://ohdsi.github.io/CommonDataModel/), consultato il 1 settembre 2026.
- [SNOMED CT RF2 specification](https://docs.snomed.org/snomed-ct-specifications/snomed-ct-release-file-specification/1-introduction), consultata il 1 settembre 2026.
- [LOINC versioning policy](https://loinc.org/kb/versioning), consultata il 1 settembre 2026.

## 14. Collegamenti

- `terminology-architecture.md`
- `lineage-and-provenance.md`
- `../mapping/mapping-governance.md`
