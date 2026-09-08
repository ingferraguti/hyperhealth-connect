# Strategia di test e quality engineering

Stato: baseline di produzione 1.0  
Data di aggiornamento: 4 settembre 2026  
Ultima verifica delle fonti ufficiali: 4 settembre 2026  
Target: HHC enterprise multi-azienda e multi-facility per la sanità europea

## 1. Obiettivo

La strategia deve produrre evidenza ripetibile che HHC preservi dati e significato, isoli tenant e failure domain, rispetti contratti e SLO e recuperi da guasti senza perdita silenziosa. Il testing è parte del design e del release artifact, non una fase finale.

Nessun singolo indicatore — coverage, numero di test, scanner o penetration test — dimostra qualità o sicurezza. Il gate usa requisiti, rischi, threat model, casi d'uso, risultati, limitazioni e approvazioni.

## 2. Principi vincolanti

1. **Risk-based e requirement-based**: P0, patient safety, privacy e blast radius hanno priorità.
2. **Deterministico e riproducibile**: versioni, seed, clock, locale, input e ambiente sono fissati.
3. **Test isolation first**: i test negativi cross-tenant precedono le feature.
4. **Source fidelity**: checksum, conteggi e semantic diff rilevano perdita o reinterpretazione.
5. **Failure is a first-class path**: timeout, crash, duplicate, reorder e partial commit sono normali casi di test.
6. **No production PHI in CI**: fixture sintetiche per default; ogni eccezione è autorizzata e isolata.
7. **No mock-only confidence**: contract test e dipendenze reali qualificate bilanciano la virtualizzazione.
8. **Shift left e shift right**: controlli in PR, qualification, canary, synthetic monitoring e game day.
9. **Evidence as code**: test pack, risultati e waiver sono versionati e firmati.
10. **Fail closed sui gate critici**: un'infrastruttura test guasta non trasforma un controllo in successo.
11. **AI output is untrusted input**: codice, test, fixture e documentazione generati da un agente richiedono provenienza, review e oracle indipendente proporzionati al rischio.
12. **Human accountability**: un agente non approva merge, release, waiver, rischio clinico o accesso privilegiato.

### 2.1 Sviluppo assistito e vibe coding

Il vibe coding è ammesso per esplorare e prototipare in workspace isolati. Per entrare nella baseline, il risultato deve attraversare lo stesso processo di una modifica progettata tradizionalmente: requisito esplicito, classificazione A0–A4, diff controllabile, test sensibili al difetto, independent challenge e release evidence. Una demo non è una qualification e il fatto che l'agente abbia anche scritto i test non costituisce indipendenza.

La [politica di qualità per agentic coding](./agentic-coding-quality-policy.md) è normativa per prompt/context data, sandbox, autonomia, Agentic Change Manifest, anti-gaming, qualifica di modello/tool e incident response. La [toolchain](./test-toolchain-and-environments.md) definisce runner, BOM e ambienti ammessi.

## 3. Quality model

| Attributo | Evidenza minima |
|---|---|
| Correttezza | unit/property/model test, golden corpus, reconciliation |
| Interoperabilità | conformance pack e test partner/standard |
| Sicurezza | threat-based test, ASVS/API, SAST/DAST/SCA, pen test |
| Privacy | leakage, purpose/scope, retention/deletion e re-identification test |
| Affidabilità | crash consistency, idempotenza, retry, failover e restore |
| Prestazioni | benchmark, load/stress/soak, capacity e regression budget |
| Resilienza | failure injection, chaos, dependency isolation e degraded mode |
| Auditabilità | coverage eventi, tamper/gap, correlation ed evidence export |
| Usabilità/accessibilità | task-based test, WCAG 2.2 AA, keyboard/screen reader |
| Manutenibilità | compatibility N/N-1, migration, rollback e mutation score |

## 4. Tracciabilità

Ogni test case ha ID stabile e collega:

```yaml
testId: T-EVT-003-CRASH-01
requirements: [FR-EVT-003, NFR-BC-001]
useCases: [UC-OPS-003]
risks: [R-CLIN-03, R-REL-02]
threats: [optional-threat-id]
changeOrigin: human|ai-assisted|agentic
agenticChangeId: optional-ACM-id
component: flow-runtime
level: component|integration|system|operational
environmentProfile: ref-env-ha-v1
toolchainBom: enterprise-jvm-k8s@digest
fixture: synthetic-pack-id|version
oracle: ledger-and-destination-reconciliation
oracleOwner: role-id
expected: explicit
evidenceRetention: policy-ref
owner: team-id
```

La matrice requisito → rischio → test → risultato → evidence → defect/waiver deve coprire il 100% dei P0 prima della release candidate. Requisito senza test automatizzabile richiede verifica manuale formalizzata e motivazione. Per una modifica agentica A3, l'oracle non può essere posseduto soltanto dall'autore della modifica o derivato esclusivamente dall'output della stessa sessione.

## 5. Livelli di test

### 5.1 Static verification

- formatter/lint e compiler strict;
- schema/OpenAPI/AsyncAPI/config validation;
- architecture dependency rule;
- secret scan, SAST e IaC/container policy;
- SCA, license policy, SBOM e vulnerability reachability;
- artifact signature/provenance e reproducible build check;
- documentation link, claim e version consistency.

### 5.2 Unit e property-based

Unit test veloci e isolati per invariant, parser adapter, mapping rule, policy e error model. Property-based test genera Unicode, cardinalità, date/offset, decimal, identifier, duplicate e malformed input. Seed e minimal failing example sono conservati.

Mutation testing è obbligatorio sui moduli ad alta criticità: authorization, scope, ACK/durability, dedup, mapping semantico, retention e audit. Il mutation score è interpretato per rischio; mutanti equivalenti sono giustificati.

### 5.3 Component test

Servizio con datastore/broker/object store reali effimeri, clock/identity simulabili e dependency fault proxy. Verifica transazioni, outbox/inbox, migration, concurrency, cache e restart. I mock non rappresentano semantics di commit o failure storage.

### 5.4 Contract test

Consumer/provider contract per API, event schema, connector SDK e protocol adapter. Copre success/error, timeout, limits, idempotency, compatibility N/N-1 e capability negotiation. Un contract non può contraddire standard o profilo pinnato.

### 5.5 Integration test

Più componenti reali verificano flow, identity, policy, telemetry, secret rotation e delivery. Include dipendenze degradate e versioni miste ammesse dalla support matrix. Non usa solo happy path.

### 5.6 End-to-end

Pochi scenari ad alto valore attraversano ingress → durability → mapping → destinazione → lineage/audit e, asincrono, OMOP. Oracle confronta eventi inviati, accettati, consegnati, duplicati, quarantinati e persi; “HTTP 200” o “pod ready” non basta.

### 5.7 Operational acceptance

SRE/NOC/SOC e operatori di facility provano dashboard, paging, runbook, failover, restore, break-glass, evidence export e handover 24×7. Il sistema non è production-ready se solo il team di sviluppo sa recuperarlo.

## 6. Ambiti obbligatori

### 6.1 Tenancy e authorization

Matrice attore × azione × tenant × organization × facility × purpose. Testa object-level, function-level e property-level authorization, confused deputy, token audience, stale grant, ID enumeration, cache/index leakage, backup/export e admin delegation. Ogni test positivo ha una variante negativa cross-scope.

### 6.2 Event lifecycle

Envelope obbligatorio, raw immutabile, checksum, ACK point, crash prima/dopo commit, at-least-once, duplicate/collision, ordering, retry/jitter, circuit breaker, DLQ, unknown receipt, replay e reconciliation.

### 6.3 Mapping e semantica

Golden source→CSE→target, no-map/ambiguous, broader/narrower, unit conversion, precision/timezone, terminology version drift, semantic loss budget, deterministic replay e approval scope.

### 6.4 Data e OMOP

ETL restart/idempotenza, deterministic keys, correction/deletion, domain assignment, source preservation, lineage row→raw, DQD, characterization, immutable publication e tool compatibility 5.5.

### 6.5 Observability e audit

Correlation end-to-end, metric cardinality, log/trace redaction, buffer collector 24 ore, audit coverage, hash/signature/gap, time sync, SIEM outage e alert routing. I synthetic test devono essere distinguibili e non contaminare sistemi clinici o dataset.

### 6.6 UI e accessibilità

Task critici: deploy, rollback, Message Explorer, quarantine, replay, audit ed emergency access. Test automatizzati più valutazione manuale keyboard, focus, zoom, contrast, screen reader, error identification e accessible authentication. Target WCAG 2.2 AA; il solo scanner automatico non basta.

### 6.7 Sicurezza clinica e contesto europeo

Ogni use case clinico collega hazard, controllo, test e rischio residuo. La suite copre identificazione errata, dato obsoleto, perdita di precisione, unità incoerente, duplicato, ritardo, routing errato, allarme mancato e comportamento dell'operatore in modalità degradata. I test con esito clinicamente rilevante richiedono un oracle approvato da clinical safety o informatica clinica.

Per ogni paese e installazione si mantiene una matrice di applicabilità separata dal codice: protezione dati, conservazione, audit, accessibilità, cybersecurity, scambio transfrontaliero e, se applicabili alla classificazione effettiva del prodotto, requisiti per dispositivo medico o sistema di IA. Il superamento tecnico dei test produce evidenza, ma non sostituisce valutazione legale, valutazione clinica o certificazione richiesta.

## 7. Secure testing program

Baseline applicativa: OWASP ASVS 5.0.0 con requisito identificato come `v5.0.0-x.y.z`; API: OWASP API Security Top 10 2023. La control matrix seleziona requisiti applicabili e test/evidence, senza dichiarare conformità all'intero standard se non verificata.

Attività:

- abuse/misuse cases dal threat model;
- SAST, DAST, IAST dove utile e manual review;
- API fuzzing/schema mutation e parser fuzzing;
- authn/authz/session/token/SSRF/injection/deserialization test;
- rate/resource exhaustion e business-flow abuse;
- crypto/configuration/secret/key rotation test;
- container/Kubernetes/IaC/admission/network policy test;
- dependency confusion, typosquat, provenance e signature test;
- penetration test indipendente su release candidate e retest;
- annual red/purple-team scenario sul blast radius enterprise.

NIST SSDF 1.1 struttura il secure SDLC. Supply-chain evidence punta almeno a provenance SLSA 1.2 adeguata al rischio; OpenSSF Scorecard è un segnale per dipendenze, non un approval automatico.

## 8. Test data management

### 8.1 Classi

| Classe | Uso | Controllo |
|---|---|---|
| Synthetic deterministic | CI/regression | default, versionato |
| Synthetic generated/fuzz | property/security | seed e shrink artifact |
| Public standard examples | conformance | licenza/provenance |
| De-identified authorized | pre-production mirata | DPIA/basis, enclave, expiry |
| Production replay | eccezionale | vietato fuori processo break-glass approvato |

Fixture coprono lingue europee, alfabeti, DST/timezone, partial date, decimals, large payload, duplicate, correction, rare codes e malformed input. Non devono incorporare identificativi reali in branch, artifact, screenshot o ticket.

### 8.2 Synthetic identity

ID, MRN, accession e UID sono generati in namespace test riconoscibile e impossibile da confondere con produzione. Destination test rifiuta traffico senza marker. Data generator ha versione e seed; test pack è firmato.

### 8.3 Leakage detection

Canary token sintetici univoci attraversano log, trace, metriche, error, support bundle, export e crash dump. Scanner blocca la pipeline se compaiono in canali vietati. Il risultato è conservato come evidence privacy.

## 9. Ambienti

| Ambiente | Scopo | Dati | Mutabilità |
|---|---|---|---|
| Developer | unit/component | sintetici | effimero |
| PR ephemeral | contract/integration/security | sintetici | per commit |
| Integration | cross-service | sintetici | reset controllato |
| Conformance lab | standard/profile/partner simulator | pack ufficiali/sintetici | versionato |
| Performance | hardware/topologia dichiarati | sintetici ad alto volume | esclusivo durante run |
| Resilience/DR | fault/failover/restore | sintetici | topologia production-like |
| Pre-production | RC/operational acceptance | sintetici; eccezione autorizzata | change-controlled |
| Production canary | synthetic transaction/limited experiment | solo synthetic marker | blast radius minimo |

Account, cluster, chiavi e identity sono separati da produzione. Sanitizzazione post-test e TTL sono automatici. Service virtualization è versionata; per ogni dipendenza critica esiste almeno un test con implementazione reale qualificata.

## 10. Pipeline e quality gate

### PR gate

Build riproducibile, static checks, unit/property, mutation delta, component/contract, secret/SAST/SCA/IaC, test data scan e coverage dei requisiti modificati. Per modifiche assistite: Agentic Change Manifest, classificazione, scope diff, tool permission evidence e controllo che test/gate non siano stati indeboliti. Target durata breve e feedback parallelo.

### Main/nightly

Integration, database dialect, parser fuzz corpus, cross-tenant, migration N/N-1, deterministic replay, browser/accessibility automatico, dependency failure e medium load.

### Release candidate

Full regression, conformance, performance/soak, failure/chaos in lab, upgrade/rollback, backup/restore, pen test/retest, SBOM/provenance/signature, operational acceptance e evidence package. Un modello, prompt pack, tool o sandbox profile non qualificato non può produrre l'unica implementazione/evidenza di un controllo A3.

### Post-release

Canary, synthetic end-to-end, SLO burn, data-quality drift, vulnerability monitoring e scheduled game day/DR. Un signal critico sospende rollout.

## 11. Gate quantitativi

- 100% P0 coperti da test/evidenza; un waiver può riguardare solo un criterio non critico, deve essere firmato e non scaduto;
- zero known event loss o reconciliation mismatch;
- zero cross-tenant/privacy/audit critical failure;
- zero vulnerabilità oltre soglia senza risk acceptance/expiry;
- zero failed fatal DQD per dataset pubblicato;
- performance e error budget entro baseline;
- upgrade/rollback e restore entro target;
- nessun flaky test critico; flakiness globale sotto soglia del programma;
- claim di conformance limitato alle capability passate.

Non sono derogabili perdita silenziosa, corruzione semantica clinicamente pericolosa, accesso cross-tenant, ACK positivo senza la durabilità prevista, bypass di autenticazione/autorizzazione, audit critico assente o restore Tier 0 non dimostrato. L'eventuale accettazione di rischio non trasforma un test fallito in un test superato e resta visibile nel release decision record.

Coverage line/branch è diagnostica, non gate unico. Per moduli safety/security-critical si definiscono branch, mutation e requirement coverage più stringenti.

## 12. Flaky test e test debt

Retry automatico non converte fail in pass. Il primo risultato e ogni retry sono conservati. Test flaky è quarantinabile solo se non copre gate P0/security/conformance; ha owner, issue, expiry e impatto visibile. Superata l'expiry blocca il merge/release. Si misurano flake rate, mean time to repair e quarantined coverage.

## 13. Defect e severity

| Severity | Esempio | Release |
|---|---|---|
| Critical | perdita evento, cross-tenant, firma bypass, audit tamper | stop immediato |
| High | semantic corruption, failover fuori RTO, auth bypass scoped | blocco RC |
| Medium | failure non critica con workaround | risk review |
| Low | cosmetic/documentation senza claim errato | backlog controllato |

Severity combina patient safety, privacy, security, availability, scope e detectability. La priorità non è decisa dal solo numero di record.

## 14. Evidence package

Manifest release, requirements matrix, environment/hardware, build/SBOM/provenance, test plans/results, fixture versions, conformance claims, performance raw series, chaos/DR reports, security findings/retest, accessibility report, waivers, approvals e checksums. Artifact è immutable, firmato, access-controlled e retained per policy.

## 15. Ruoli

- developer: unit/component e fix;
- quality engineer: framework, traceability e independent challenge;
- security engineer: threat/security/supply chain;
- clinical informatician/terminology steward: semantic oracle;
- SRE: performance, chaos, HA/DR e runbook;
- privacy/DPO delegate: data/leakage/rights;
- product/flow owner: acceptance e residual risk;
- Human Accountable Owner: perimetro, classe e completezza di una modifica assistita;
- Oracle Owner: risultato atteso indipendente per rischio critico;
- Agent Platform Owner: modelli/tool ammessi, sandbox, DLP, identity, logging e kill switch;
- release manager: evidence completeness e promotion;
- auditor: read-only verification.

La qualità resta responsabilità condivisa; chi implementa un controllo critico non è l'unico approvatore del relativo gate.

## 16. Casi d'uso di qualification

### UC-TEST-01 — ADT Tier 0

Un milione di eventi sintetici include duplicate, reorder, crash e destinazione lenta. Accettazione: RPO locale 0 dopo ACK, zero mismatch, ordering scoped, audit/lineage completi e performance entro target.

### UC-TEST-02 — isolamento multi-azienda

Attori e workload tentano accessi a raw, cache, audit, search, replay, backup e OMOP di altra organization. Accettazione: deny uniforme, nessun side channel sostanziale e security event correlato.

### UC-TEST-03 — upgrade enterprise

Runtime N/N-1, schema migration, canary e rollback sono provati sotto carico. Accettazione: nessun output con versione non dichiarata, nessuna perdita e rollback entro error budget.

### UC-TEST-04 — supporto operativo

NOC/SOC eseguono synthetic incident, alert, break-glass ed evidence export. Accettazione: routing/acknowledgement, least privilege, audit e runbook completi.

## 17. Fonti ufficiali verificate

Fonti verificate o riconfermate il **4 settembre 2026**:

- [OWASP ASVS 5.0.0](https://owasp.org/www-project-application-security-verification-standard/), release stabile del 30 maggio 2025.
- [OWASP API Security Top 10 2023](https://owasp.org/API-Security/).
- [NIST SP 800-218 — SSDF 1.1](https://csrc.nist.gov/pubs/sp/800/218/final).
- [NIST SP 800-218A](https://csrc.nist.gov/pubs/sp/800/218/a/final), profilo SSDF per sviluppo di modelli e sistemi generativi, da usare insieme a SSDF 1.1.
- [NIST AI RMF e Generative AI Profile](https://www.nist.gov/itl/ai-risk-management-framework), riferimento risk-based per qualificare l'uso di sistemi generativi nello sviluppo.
- [SLSA specification 1.2](https://slsa.dev/spec/v1.2/) e [repository ufficiale](https://github.com/slsa-framework/slsa).
- [OpenSSF Scorecard](https://openssf.org/scorecard/), segnale di rischio e non approvazione automatica della dipendenza.
- [W3C WCAG 2.2 Recommendation](https://www.w3.org/TR/WCAG22/).
- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/) e [semantic conventions](https://opentelemetry.io/docs/specs/semconv/general/); le stability label sono verificate per singolo segnale/convenzione.
- [Regolamento (UE) 2025/327 — EHDS](https://eur-lex.europa.eu/eli/reg/2025/327/oj/), applicabile dal 26 marzo 2027 con fasi successive; nel 2026 guida readiness e test design senza autorizzare claim anticipati.

## 18. Collegamenti

- [Conformità e interoperabilità](./conformance-and-interoperability.md)
- [Performance, resilienza e chaos engineering](./performance-and-chaos.md)
- [Politica di qualità per agentic coding](./agentic-coding-quality-policy.md)
- [Toolchain, ambienti e automazione](./test-toolchain-and-environments.md)
- [Requisiti di prodotto](../product/requirements.md)
- [Scalabilità e resilienza](../architecture/scalability-and-resilience.md)
- [Architettura di sicurezza](../security/security-architecture.md)
- [Piano di rilascio](../roadmap/release-plan.md)
