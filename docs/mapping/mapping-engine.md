# Mapping Engine HHC

Stato: specifica di sviluppo 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Target: trasformazioni deterministiche, sicure e ad alte prestazioni

## 1. Responsabilità

Il Mapping Engine trasforma parsed artifacts, Canonical Semantic Event e formati target usando asset immutabili. Separa mapping tecnico (struttura/sintassi) da mapping semantico (concetti/terminologie). Non decide patient identity, base giuridica, diagnosi o policy di accesso.

## 2. Pipeline

```text
raw reference
  → parse(profile, parser version)
  → validate(source contract)
  → technical map
  → canonical validate
  → semantic translate(snapshot, map release)
  → target projection
  → validate(target contract)
  → immutable output + findings + lineage
```

Ogni step può essere eseguito indipendentemente e produce input/output checksum, rule IDs, tempi e disposition. Un flow può omettere il canonical pass solo con ADR e mapping diretto comunque governato; ciò non elimina raw/lineage.

## 3. Asset di mapping

```yaml
mappingId: map/<domain>/<source>/<target>
version: 3.2.0
status: draft|review|approved|deprecated|retired
sourceContract: canonical-ref
targetContract: canonical-ref
engineApiVersion: hhc-map-api/v1
rulesArtifact: immutable-ref
semanticRelease: immutable-ref
terminologySnapshot: immutable-ref
effectiveFrom: instant
effectiveTo: optional
scope: {tenant, organization, facility, application, endpoint}
serviceTiersAllowed: [TIER_0, TIER_1]
performanceClass: fast|standard|batch
owners: {technical, clinical, data, operational}
approvals: [signed-reference]
testSuite: immutable-ref
sbom: immutable-ref
digest: sha256
```

La precedence è esplicita: endpoint > application > facility > organization > tenant > product default, ma un override non può indebolire invariant di sicurezza o standard senza waiver. La risoluzione produce una singola release deterministica o fallisce.

## 4. DSL e runtime

La DSL è dichiarativa, tipizzata e side-effect-free nel core. Supporta select, rename, compose, split, join bounded, default esplicito, conditional, lookup versionato, unit conversion approvata, validation e emit. Loop/recursion sono limitati; accesso filesystem/network/process, clock corrente e random non sono permessi.

Funzioni custom girano in sandbox con:

- API allowlist e capability manifest;
- CPU, memoria, wall time e output size limit;
- dependency lock/SBOM/signature;
- deterministic clock/locale/timezone;
- nessun egress salvo connector semantic service governato fuori fast path;
- kill/quarantine e circuit breaker.

L'engine compila mapping in artifact cache per `mapping version + schema + terminology snapshot + engine build`. Cache corruption è rilevata da digest. Nessun runtime download da repository pubblico.

## 5. Type e null semantics

Conversioni implicite rischiose sono vietate. String→decimal richiede locale/formato; decimal mantiene precision/scale; date/time mantiene precisione e offset; code richiede system/version; identifier richiede assigner; null tecnico è distinto da absent reason, masked e not-applicable.

Un default clinico richiede rule ID e approvazione; default tecnici innocui sono comunque visibili. Truncation, overflow, invalid Unicode e unit mismatch sono errori, non correzioni silenziose.

## 6. Mapping semantico

Il runtime interroga una Semantic Mapping Release immutabile. La risposta include source/target coding, relationship, dependsOn/context, status, confidence, reviewer e provenance. Per outcome multipli:

- `equivalent` univoco e approvato può essere applicato;
- broader/narrower è applicato solo se il target use case lo consente;
- ambiguous richiede contesto o review;
- no-map conserva source e finding;
- fallback generico è consentito solo se dichiarato e misurato.

Per FHIR `$translate`, il motore controlla `result`, tutte le `match`, relationship e input scope; non sceglie il primo match. Per OMOP usa standard concept del vocabulary snapshot e preserva `*_source_value`/`*_source_concept_id` secondo CDM.

## 7. Error model

| Categoria | Retry | Disposition tipica |
|---|---:|---|
| invalid source | no | reject/quarantine |
| missing required context | no | quarantine |
| mapping not found | no | route-specific block |
| ambiguous semantics | no | stewardship queue |
| terminology temporarily unavailable | sì/bounded | cache fallback o retry |
| engine resource exhaustion | sì/bounded | retry/isolate mapping |
| custom function failure | dipende | circuit/quarantine |
| target validation failed | no | block target; preserve other routes |
| internal integrity failure | no | stop cell path e incident |

Finding include error code stabile, source/CSE/target path, rule/version, severity, retryable, redacted detail e correlation. Stack trace resta nel secure diagnostic store.

## 8. Idempotenza e determinismo

Stesso input checksum + mapping release + terminology snapshot + engine build + context canonico deve produrre stesso output checksum. Il context include timezone/locale/policy version e non include stato volatile. Eccezioni sono proibite nel Tier 0 e documentate nei batch.

Il motore non effettua delivery. Emissioni multiple sono raccolte in un atomic result manifest; il Flow Runtime decide fan-out e ledger. Crash dopo output ma prima del commit viene riconciliato via result key deterministica.

## 9. Prestazioni e isolamento

Mapping `fast` è precompilato, senza I/O remoto e con allocation budget. Obiettivo piattaforma per HL7 v2 ≤64 KiB: overhead complessivo HHC p95 ≤50 ms/p99 ≤100 ms nelle condizioni di benchmark. Metriche specifiche misurano parse, map, terminology e validation separatamente.

Worker pool e queue sono per service tier/tenant/destination. Mapping costosi non causano head-of-line blocking. Autoscaling usa CPU, in-flight, throughput, queue oldest age e predicted drain time. Il benchmark di cella resta ≥2.000 eventi/s da 8 KiB/60 min, CPU media ≤70%, p99 ingest ≤250 ms e zero mismatch.

## 10. HA, BC e DR

Engine stateless; mapping bundle, schema e terminologia sono replicati e firmati in ogni Runtime Cell. L'ultima release valida continua ≥24 ore senza Control Plane. Un deploy è canary/rolling, health-gated e rollbackable; eventi riportano sempre la versione effettiva.

Mapping artifact e approval evidence fanno parte del backup/cyber-vault. Restore verifica signature, digest, dependency availability e golden tests prima di riaprire. Tier 0 eredita SLO 99,99%, RTO ≤30 minuti e RPO del ledger/raw; mapping non è un punto di perdita.

## 11. Sicurezza e supply chain

- artifact firmati e provenienza di build;
- four-eyes per codice custom e mapping clinico critico;
- SAST/SCA/secret scan e SBOM;
- sandbox e deny network;
- no eval/script arbitrario in produzione;
- service account per sola lettura degli asset;
- audit di publish, activate, rollback, replay e override;
- tenant isolation test e cache key scoped;
- input hostile test per XML/JSON/HL7/DICOM.

## 12. Audit e monitoraggio

Metriche per mapping/release senza valori clinici: throughput, p95/p99, error/warn, no-map/ambiguous, target validation, output/input ratio, memory/CPU, cache hit, timeout, circuit open e semantic drift. Label ad alta cardinalità sono vietate.

Ogni risultato collega envelope, run, mapping, rules, terminology, input/output checksum e worker build. Audit umano registra change/ticket/approvals. Alert su spike no-map, checksum non deterministico, unknown mapping, rollout divergence, queue age e sandbox kill.

## 13. Test strategy

- unit test per ogni rule e branch;
- golden source→CSE→target con semantic assertions;
- negative/boundary/property-based/fuzz;
- mutation test per regole critiche;
- round-trip quando semanticamente lecito;
- regression diff su corpus sintetico e campione autorizzato;
- determinism/repeatability e concurrency;
- performance/soak/burst/noisy neighbor;
- terminology outage/cache expiry;
- canary/shadow comparison e rollback;
- restore/replay con versioni storiche.

Quality gate: 100% branch delle rule safety-critical, zero unexplained diff, approvazione clinico-informatica per semantic change, performance budget rispettato e runbook disponibile.

## 14. Casi d'uso europei 2026

### UC-MAP-01 — laboratorio locale → FHIR/OMOP

Il codice locale è tradotto a LOINC e l'unità a UCUM. FHIR operativo è prodotto subito; OMOP asincrono usa standard concept. Mapping ambiguo blocca OMOP ma può consentire consegna source-preserving. Accettazione: no valore inventato, source preservata e lineage campo-a-campo.

### UC-MAP-02 — patient summary nazionale

CDA/FHIR nazionale è trasformato verso adapter transfrontaliero. Elementi senza equivalente restano come gap e non vengono omessi senza warning. Accettazione: IG/versione pin-nati, lingua/source coding preservati e regression clinica approvata.

### UC-MAP-03 — rollout multi-facility

Mapping v4 è canary in una facility, comparato a v3 in shadow e promosso a wave. Accettazione: nessuna versione mista per evento, rollback rapido e audit delle approvazioni.

### UC-MAP-04 — reprocessing storico

Un errore terminologico richiede replay. Nuovo output/dataset non sovrascrive il precedente. Accettazione: impact set determinato dal lineage, rate limit, evidence diff e idempotenza.

## 15. Fonti ufficiali verificate

- [FHIR R5 ConceptMap](https://hl7.org/fhir/R5/conceptmap.html) e [`$translate`](https://hl7.org/fhir/R5/conceptmap-operation-translate.html), consultate il 1 settembre 2026.
- [W3C PROV-O](https://www.w3.org/TR/2013/REC-prov-o-20130430/), consultata il 1 settembre 2026.
- [OHDSI OMOP CDM 5.5](https://ohdsi.github.io/CommonDataModel/cdm55.html), consultato il 1 settembre 2026.
- Le fonti di parser/formato sono elencate in `../standards/`.

## 16. Collegamenti

- `mapping-governance.md`
- `../canonical-model/canonical-semantic-event.md`
- `../semantic/semantic-mapping-registry.md`
- `../semantic/terminology-architecture.md`
