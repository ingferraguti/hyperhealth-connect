# HL7 FHIR — profilo di integrazione HHC

Stato: baseline di sviluppo 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica delle fonti ufficiali: 1 settembre 2026  
Target: API e scambio FHIR enterprise nella sanità europea

## 1. Scopo e posizione di FHIR

FHIR è un contratto di interoperabilità esterno e una famiglia di modelli/API; non è il modello universale interno di HHC. HHC preserva raw, Integration Envelope e Canonical Semantic Event, quindi genera o consuma Resource conformi alla release e all'Implementation Guide (IG) selezionati.

Baseline:

- FHIR R4 4.0.1 per gli IG e gli ecosistemi che lo richiedono;
- FHIR R4B 4.3.0 soltanto per profili/partner che lo dichiarano;
- FHIR R5 5.0.0 per nuove capability quando IG ed ecosistema sono maturi;
- FHIR R6 è work in progress/ballot al 1 settembre 2026 e non è baseline di produzione.

R5 è la versione pubblicata corrente, ma include contenuti Trial Use. “Usa R5” non equivale a “tutto è normativo”. Ogni capability registra maturity/status di resource, IG e operation effettivamente usati.

## 2. ConformancePack

Ogni endpoint espone o consuma un pacchetto immutabile:

```yaml
id: fhir/<realm>/<organization>/<purpose>
version: semver
fhirRelease: 4.0.1|4.3.0|5.0.0
implementationGuides:
  - package: canonical.package.id
    version: exact
    digest: sha256
profiles: [canonical-url|version]
operations: [read, search, create, update, transaction, bulk-export]
searchParameters: [canonical-url|version]
terminologySnapshot: immutable-reference
validationPolicy: immutable-reference
capabilityStatement: immutable-reference
securityProfile: immutable-reference
serviceTier: TIER_0|TIER_1|TIER_2
```

Le dipendenze NPM transitive sono risolte e archiviate in un registry approvato. Nessun pacchetto `current`, CI build o versione floating entra in produzione. Canonical URL e versione restano distinti.

## 3. Pattern di integrazione

| Pattern | Uso | Regole HHC |
|---|---|---|
| RESTful API | read/search/write operativi | CapabilityStatement, ETag/version, scope, timeout e audit |
| Transaction/Batch Bundle | operazioni aggregate | limiti size/entry; transaction solo con atomicità server |
| Messaging Bundle | eventi | idempotenza, MessageHeader, focus e receipt |
| Document Bundle | patient summary/referto | Composition first, integrità e persistenza |
| Subscription | notifiche | versione-specifica, delivery ledger e back-pressure |
| Bulk Data | uso secondario/export | asincrono, permit, minimizzazione e output control |
| Terminology API | validate/expand/translate | release pinned, cache, licenza e provenance |

`Bundle.type=transaction` non offre atomicità fra server differenti. HHC non simula transazioni distribuite; usa saga, compensazione e reconciliation.

## 4. Ruoli del prodotto

HHC può agire come client verso EHR/repository/terminologie, facade policy-aware, endpoint di ingest/event delivery, validation service o projection service dal canonical model. Non diventa system of record clinico solo perché espone una Resource. Ownership, persistenza autorevole e lifecycle sono dichiarati per endpoint.

Search cross-organization richiede policy e indice dedicati; non si ottiene unendo query runtime senza limiti. Analytics e OMOP non entrano mai nel percorso di ACK clinico.

## 5. Versioni e trasformazioni

Le trasformazioni R4↔R5 sono mapping espliciti con perdita documentata; un resource non è semplicemente rietichettato. Il pipeline registra source/target release, IG, StructureMap o mapping custom e warning. Campi senza equivalente restano nel raw/canonical. Extension sono preservate o mappate solo se comprese.

1. Una Resource valida rispetto al core può non esserlo rispetto all'IG.
2. Profili nazionali prevalgono sul generico soltanto nel proprio realm/purpose.
3. Slicing, invariant, mustSupport e binding sono verificati alla versione fissata.
4. Unknown extension non viene scartata silenziosamente.
5. ModifierExtension non compresa blocca l'uso che dipende dal suo significato.
6. La narrativa non è usata come fonte strutturata salvo processo dedicato.
7. Reference resolution ha confine, profondità, timeout e autorizzazione espliciti.

## 6. Identificativi e riferimenti

`Resource.id`, business `identifier` e logical reference sono distinti. HHC conserva system, value, type, assigner e period; non usa il solo value come chiave globale. Reference assolute sono validate contro allowlist per impedire SSRF. Conditional create/update è ammesso solo con criteri univoci testati.

Il canonical subject ID è opaco e scoped. Link/merge proviene da un identity service autorizzato; `Patient.link` non autorizza da solo un merge HHC. L'autorizzazione include Tenant → Organization → Facility → Application → Endpoint.

## 7. Semantica API

### 7.1 Read e versioning

- ETag/version ID e `If-Match` prevengono lost update;
- 404, 410 e deny policy sono distinti senza creare data oracle;
- audit di accesso e purpose non porta payload nei log;
- timeout/circuit breaker proteggono da upstream lenti;
- cache solo con classification, tenant key e invalidation governata.

### 7.2 Search

I SearchParameter supportati sono dichiarati. Sono obbligatori limiti `_count`, pagination stabile, total policy, sort ammessi, compartment/scope e cost guard. Reverse chain, `_include` e custom parameter possono essere disabilitati o budgetizzati. Query non selettive e fan-out cross-facility diventano job asincroni.

### 7.3 Write

Create/update/patch richiedono content type, profile validation, authorization e idempotenza. Client-generated ID è capability esplicita. PATCH è limitato per path/operation. Ogni write accettata registra raw request, response, version e audit; PHI non entra nell'error telemetry.

### 7.4 OperationOutcome

Ogni errore produce `OperationOutcome` congruente con HTTP, con issue severity/code e expression/location quando sicuro. Diagnostic non espone stack, query, secret o dati di altri tenant.

## 8. Terminologia

Validation e mapping usano CodeSystem/ValueSet/ConceptMap versionati. Operazioni candidate: `$validate-code`, `$expand`, `$lookup`, `$subsumes`, `$translate`. `$translate` può restituire più match o mapping esclusi: il consumer valuta relationship, target, dependsOn e provenance, non prende il primo risultato.

`sourceScope` è fornito quando noto. Risposte dipendenti da contenuto `latest` non entrano nel percorso clinico. La cache key include operation, parametri normalizzati, versioni, lingua e terminology release.

## 9. Sicurezza e privacy

La baseline usa OAuth 2.0/OIDC e workload identity, con mTLS dove richiesto. SMART on FHIR può far parte del profilo ma non sostituisce policy HHC. I token hanno audience/scope minimi, TTL contenuto e rotazione; non sono inoltrati a audience diversa né registrati. Tenant, organization, purpose e delegation sono verificati a ogni boundary.

FHIR `Consent` può rappresentare parte delle regole, ma decisione legale e autorizzativa restano nel policy decision point e nelle fonti autorevoli. Break-glass richiede motivo, durata, privilegio minimo, alert e audit. Bulk export è soggetto a permit e secure processing environment.

## 10. Prestazioni e resilienza

Server/proxy sono stateless quando possibile; rate limit, connection pool e concurrency budget sono per tenant e upstream. Binary e payload grandi usano streaming. Bundle ha limiti di entry, byte e processing time.

Per Tier 0: SLO 99,99%, RTO ≤30 minuti, RPO locale 0 dopo ACK durabile, RPO cross-site ≤5 minuti. Una write sincrona è confermata solo al commit point dichiarato. Se la risposta si perde, si riconcilia con idempotency key, conditional semantics o read-back.

Il Control Plane non è nel request path. Una Runtime Cell usa ConformancePack e policy cache firmati per almeno 24 ore. Terminology remota non indispensabile non entra nel fast path: usa snapshot locale o fallisce esplicitamente.

## 11. Audit e monitoraggio

Per endpoint/profile/operation:

- rate, status e p50/p95/p99;
- validation outcome per invariant/profile;
- deny, break-glass e privileged operation;
- pool saturation, timeout e circuit state;
- bundle size, search cost e pagination failure;
- subscription lag/oldest age e delivery outcome;
- terminology cache hit, release e unavailable time;
- mapping loss/warning per trasformazione.

W3C Trace Context propaga `traceparent`/`tracestate`; baggage non contiene PHI, token, MRN o codici clinici. Il tracing non sostituisce l'audit append-only/tamper-evident.

## 12. HAPI FHIR e validator

HAPI FHIR è la dipendenza Java candidata. Il repository verificato supporta moduli/test R4, R4B e R5; la versione è pin-nata dopo compatibility, security e license review. L'HL7 FHIR validator `org.hl7.fhir.core` e i package IG sono la base di conformance, ma la release HHC congela:

- engine/validator version;
- package/dipendenze e digest;
- terminology snapshot;
- validation flag;
- custom extension/SearchParameter;
- golden result e known deviation.

Il risultato di validazione può cambiare con engine/package: è evidenza versionata, non proprietà eterna del resource.

## 13. Casi d'uso europei 2026

### UC-FHIR-01 — Patient Summary inter-facility

Il sistema clinico richiede un summary da un'altra organization. HHC autentica purpose, risolve identità con servizio autorizzato, applica l'IG approvato e restituisce document Bundle con provenance e completezza. Timeout/fonti parziali sono dichiarati. Accettazione: nessuna unione implicita, source/profile/version visibili, accesso auditato e fallback locale non bloccato.

### UC-FHIR-02 — risultati di laboratorio

Observation/DiagnosticReport R4 da LIS diversi sono validati contro profilo nazionale, mappati al canonical model e consegnati. LOINC/unità mancanti generano finding; il risultato clinico non è alterato per soddisfare OMOP. Accettazione: status/correzioni preservati, lineage campo-a-campo e proiezione asincrona.

### UC-FHIR-03 — MHD document sharing

Un client usa IHE MHD. Il ConformancePack fissa la release: MHD 4.2.4 R4 o 5.0.0 R5 sono Trial Implementation e richiedono assessment/test. HHC non usa una pagina CI/comment come contratto. Accettazione: metadata/documento coerenti, audit profilato, query scoped e receipt.

### UC-FHIR-04 — bulk export autorizzato

Un health data holder prepara dati per uso secondario. Job asincrono applica permit, filtri, pseudonimizzazione, watermark e output review; non compete col Tier 0. Accettazione: dataset/versione/purpose riproducibili, revoca applicabile, no download se vietato.

### UC-FHIR-05 — migrazione R4→R5

Una facility resta R4 mentre un servizio usa R5. HHC esegue trasformazioni versionate e diff semantico, preserva elementi non mappati e rollback. Accettazione: nessun re-tag, incompatibilità documentate, nessuna configurazione mista non dichiarata.

## 14. Release gate

- CapabilityStatement confrontato col comportamento effettivo;
- validator ufficiale e package cache offline riproducibile;
- test profile, slicing, binding e invariant;
- contract test REST/Bundle/Subscription/OperationOutcome;
- authorization matrix multi-tenant e SSRF test;
- concurrency, pagination, large payload, soak e noisy-neighbor;
- failover, lost response, retry/idempotency e dependency isolation;
- regression R4/R4B/R5 e semantic-loss report;
- audit/telemetry redaction e DR restore test.

Una certificazione o un tool di test non autorizza da solo la produzione: il dossier include versione, scope, deviation, evidence e approvazione.

## 15. EHDS

L'EHDS è in vigore dal 26 marzo 2025, con applicazione scaglionata: generale dal 26 marzo 2027; patient summary ed ePrescription/eDispensation e gran parte del secondary use dal marzo 2029; imaging, laboratorio e discharge report dal marzo 2031. Nel 2026 HHC è **EHDS-ready**, non automaticamente conforme a requisiti futuri. Adapter e IG nazionali restano fuori dal core e versionati.

## 16. Fonti ufficiali verificate

- [HL7 — directory versioni FHIR](https://hl7.org/fhir/directory.html), consultata il 1 settembre 2026: R5 5.0.0 pubblicata; R4/R4B storiche; R6 work in progress/ballot.
- [HL7 FHIR R5 5.0.0](https://hl7.org/fhir/R5/) e [version management](https://hl7.org/fhir/versions.html), consultate il 1 settembre 2026.
- [FHIR R5 ConceptMap](https://hl7.org/fhir/R5/conceptmap.html) e [`$translate`](https://hl7.org/fhir/R5/conceptmap-operation-translate.html), consultate il 1 settembre 2026; `$translate` risulta Trial Use/maturity 1.
- [HAPI FHIR repository](https://github.com/hapifhir/hapi-fhir), [release](https://github.com/hapifhir/hapi-fhir/releases) e [validator core](https://github.com/hapifhir/org.hl7.fhir.core), consultati il 1 settembre 2026.
- [IHE ITI — pubblicazioni correnti](https://profiles.ihe.net/ITI/), consultate il 1 settembre 2026.
- [Regolamento EHDS (UE) 2025/327](https://eur-lex.europa.eu/eli/reg/2025/327/oj/) e [calendario Commissione](https://health.ec.europa.eu/ehealth-digital-health-and-care/european-health-data-space-regulation-ehds_en), consultati il 1 settembre 2026.

I contenuti HL7/IHE sono soggetti ai rispettivi termini. Package distribuiti e dipendenze entrano nella SBOM e nella documentation bill of materials.
