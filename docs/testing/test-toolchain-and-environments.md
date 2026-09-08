# Toolchain, ambienti e automazione dei test

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ambito | Tool, runner, ambienti, pipeline ed evidence per HHC |
| Target | Produzione multi-azienda, multi-facility e multi-site |
| Ultimo aggiornamento | 4 settembre 2026 |
| Ultima verifica fonti | 4 settembre 2026 |
| Owner | Quality Engineering |
| Co-owner | Platform Engineering, Product Security, SRE e Interoperability |

## 1. Scopo

Questo documento definisce una toolchain moderna, automatizzabile e sostenibile per dimostrare che HHC è corretto, interoperabile, sicuro, performante, resiliente e operabile. Non trasforma un elenco di tool in una prova di qualità: ogni strumento serve una capability, ogni capability ha un owner e ogni risultato qualificante è collegato a requisito, rischio, build e ambiente.

La baseline è intenzionalmente portabile tra CI enterprise. I nomi dei prodotti indicano una scelta raccomandata o un candidato qualificabile, non autorizzano il download della versione più recente. Le versioni effettive sono fissate nel **Test Toolchain BOM**, tramite package lock, image digest e checksum.

## 2. Decisioni vincolanti

1. Un solo tool è authoritative per una capability/gate; strumenti secondari aggiungono segnale ma non producono decisioni contraddittorie.
2. Nessun riferimento `latest`, branch mobile o installazione da URL non verificato nei runner qualificanti.
3. La toolchain è trattata come supply chain: SBOM, licenze, firma/provenienza, CVE, owner e ciclo di patch.
4. I test critici funzionano offline rispetto a servizi SaaS non essenziali e conservano evidence localmente.
5. I runner non hanno route verso produzione e non ricevono PHI o segreti di cliente.
6. L'ambiente di performance o DR è production-like, esclusivo durante la prova e descritto da manifest.
7. Testcontainers riutilizzabili non sono usati in CI: la documentazione ufficiale li qualifica sperimentali e non adatti alla CI.
8. I tool generativi o gli agenti non possono essere l'oracle unico, modificare i gate o auto-approvare risultati.
9. La compatibilità di un tool con la versione reale di JDK, Kubernetes, database e protocolli è provata prima dell'adozione.
10. Output non parseabile, scanner non avviato o upload evidence fallito produce `INFRA_ERROR`, mai `PASS`.

## 3. Test Toolchain BOM

Il BOM è un artifact machine-readable firmato per release della piattaforma di test. Contiene almeno:

```yaml
schemaVersion: hhc.test-toolchain/v1
profileId: enterprise-jvm-k8s-2026q3
qualifiedAt: 2026-09-04
components:
  - id: junit-platform
    version: qualified-version
    packageCoordinates: official-coordinate
    sourceRepository: official-url
    checksum: sha256:...
    license: declared-spdx-id
    capabilities: [unit, parameterized, extension]
  - id: test-container-image-postgresql
    image: approved-registry/postgresql@sha256:...
    upstreamVersion: qualified-version
    capabilities: [component, migration, restore]
runner:
  image: approved-registry/hhc-test-runner@sha256:...
  os: declared
  architecture: amd64
  runtimeVersions: {}
policies:
  networkProfile: test-egress-v3
  dataProfile: synthetic-only
  evidenceSchema: hhc.test-evidence/v1
approvals: [quality, security, platform]
```

Il BOM separa:

- tool di authoring da tool qualificanti;
- librerie di test da componenti reali sotto test;
- image di infrastruttura da binary di runner;
- tool open source da eventuali servizi enterprise;
- versione osservata upstream da versione approvata HHC.

## 4. Criteri di ammissione di uno strumento

Prima dell'uso qualificante:

| Area | Evidenza richiesta |
|---|---|
| Provenienza | repository/documentazione ufficiali, coordinate e maintainer verificati |
| Maturità | release history, community/adozione, issue e security policy valutate |
| Licenza | compatibilità di codice, plugin, rule pack, fixture e output |
| Sicurezza | CVE/transitive dependency, firma/checksum, privilegi, rete e telemetry |
| Riproducibilità | installazione pinnabile, execution non interattiva, stable output/schema |
| Portabilità | self-hosted/on-prem, proxy/mirror, air-gapped quando richiesto |
| Prestazioni | costo runner, parallelismo, memoria, durata e scalabilità suite |
| Audit | exit code affidabile, raw result, SARIF/JUnit/JSON o adapter versionato |
| Manutenibilità | owner, patch SLA, upgrade/rollback ed exit plan |
| Agentic use | termini d'uso, comportamento con coding agent e rischio di prompt/tool injection |

Un tool non conforme può essere usato in esplorazione separata, marcando i risultati `NON_QUALIFYING`.

## 5. Stack raccomandato per codice JVM

L'architettura HHC usa adapter attorno a componenti JVM diffusi come HAPI, Apache Camel, IPF e dcm4che. Per moduli JVM la baseline è:

| Capability | Default/candidato | Uso HHC | Regola |
|---|---|---|---|
| Unit/parameterized | JUnit Platform/Jupiter | dominio, parser, policy, state machine | BOM coerente; niente mix involontario di engine |
| Assertion | AssertJ o assertion native approvata | messaggi diagnostici e structured comparison | una convenzione per modulo |
| Architecture | ArchUnit | piani, layer, dipendenze e accessi vietati | regole P0 in PR |
| Mutation | PIT | authz, ACK, dedup, mapping, audit, retention | delta in PR, full mirato nightly/RC |
| Microbenchmark | JMH | hot path CPU/allocation | non sostituisce load test distribuito |
| Async/concurrency | Awaitility più clock/executor controllabili | stato eventuale senza sleep arbitrari | deadline esplicita e diagnostica |
| Integration | Testcontainers for Java | PostgreSQL, broker, object store, IdP e proxy | container effimeri con digest; no reuse in CI |

Al 4 settembre 2026 il repository ufficiale indica JUnit 6.1.0 come GA del 19 maggio 2026, Testcontainers 2.0.5 del 20 aprile 2026, ArchUnit 1.4.2 del 18 aprile 2026 e PIT 1.25.3 del 29 maggio 2026. Sono informazioni di valutazione, non un pin automatico: il BOM può mantenere una versione precedente supportata finché compatibility e security gate lo giustificano.

### 5.1 Property-based testing

La capability è obbligatoria, il prodotto non è fissato finché il linguaggio e il profilo agentico non sono qualificati. Generator e shrink devono coprire identifier, Unicode, date parziali, DST, decimal, dimensione, duplicate, reorder, null, malformed e sequenze di stato.

Al 4 settembre 2026 il progetto jqwik dichiara nel repository che dalla release 1.10 l'uso con agenti di coding è fortemente sconsigliato. Perciò jqwik 1.10+ **non è il default del profilo agentico HHC** finché Legal, Quality e Platform non hanno verificato termini, compatibilità e comportamento. Alternative o generator interni restano soggetti agli stessi criteri; non si congela una release obsoleta per aggirare la posizione upstream.

Ogni failure conserva seed, generated example minimizzato, versione generator e locale. Una property senza collegamento a un'invariante non aumenta da sola la confidenza.

## 6. Component e integration testing

### 6.1 Testcontainers

Uso raccomandato per dipendenze effimere in test JVM:

- image copiate nel registry approvato e referenziate per digest;
- startup readiness basata sul protocollo, non su sleep;
- schema/migration eseguiti come in produzione;
- network dedicato per test e porte assegnate dinamicamente;
- log redatti e allegati solo al failure;
- cleanup sempre eseguito e verificato;
- resource limit per impedire noisy-neighbour sui runner;
- failure esplicita se il container runtime non è disponibile.

Non usare il riuso sperimentale in CI. Il riuso locale è opt-in, mai per test di isolamento, migration, cleanup, retention o secret rotation.

### 6.2 Service virtualization

| Tool/capability | Uso | Limite |
|---|---|---|
| WireMock | HTTP/S errori, delay, reset, malformed response e scenario partner | non prova TLS stack o comportamento del prodotto reale |
| Toxiproxy | latency, bandwidth, timeout, reset, half-open e partition TCP | non sostituisce node/storage fault |
| Pact JVM | consumer-driven contract HTTP/message | non sostituisce standard/profile conformance né broker semantics |
| Simulatori HHC | MLLP, SFTP, DICOM, FHIR e vendor quirks | scenario e versione firmati; test periodico col partner reale |

Stub e proxy sono componenti non fidati: API amministrative isolate, nessuna esposizione pubblica, artifact pinnati e log privi di PHI.

### 6.3 Contratti

Pact può validare aspettative minime consumer/provider e version compatibility. Il contratto consumer non può rendere conforme un payload che viola OpenAPI, AsyncAPI, FHIR, IHE, DICOM o il profilo HHC. Provider verification usa build identificata dal commit; risultati e deployment environment alimentano la matrice di compatibilità.

## 7. API, schema e event testing

| Capability | Tool raccomandato | Applicazione |
|---|---|---|
| OpenAPI lint | Spectral o equivalente qualificato | style, security scheme, error model, extension HHC |
| OpenAPI generative | Schemathesis | boundary, malformed, stateful workflow e schema response |
| HTTP JVM | REST Assured/client HHC | auth, header, idempotency, pagination, conditional request |
| AsyncAPI | AsyncAPI CLI/parser ufficiale | schema, channel, binding e compatibility |
| JSON Schema | validator conforme alla draft pinnata | envelope/config/event contract |
| XML/XSD/WSDL | validator hardened | CDA/SOAP, XXE/entity/size limit |
| Event compatibility | registry rule più golden corpus | backward/forward/full secondo contratto |

Regole:

- generator schema-based non sostituisce abuse test autorizzativo;
- operazioni distruttive sono escluse o dirette a tenant sintetico effimero;
- rate e dimensioni sono limitati per non trasformare il test in DoS del runner;
- sequenze stateful conservano la history e il minimal reproducer;
- ogni risposta è controllata anche per redaction, cache header, correlation e audit;
- un `5xx` inatteso, schema drift o response fuori scope è failure anche se il client sopravvive.

## 8. Interoperabilità sanitaria

La toolchain segue il [piano di conformità](./conformance-and-interoperability.md). Capability e tool:

| Dominio | Tool/reference | Prova |
|---|---|---|
| HL7 v2 | HAPI HL7 v2 più Conformance Pack HHC | parse, profile, ACK, escape, segmenti, charset, batch |
| FHIR | validator HL7 FHIR ufficiale e package IG pinnati | struttura, cardinalità, binding, invariant, terminology |
| IHE | Gazelle e test profile-specifici | ruolo/transaction, content e workflow |
| DICOM/DICOMweb | standard DICOM e toolkit dcm4che qualificato | association, transfer syntax, UID, storage commitment, web |
| CDA/XML | schema, schematron/validator del profilo | header, templateId, narrative, signature e security |
| Terminologie | server/snapshot qualificato | code system/version, expansion, subsumption e mapping |
| OMOP 5.5 | OHDSI Data Quality Dashboard più controlli HHC | structural, conformance, completeness e plausibility |

Il report conserva output raw del validator, versione, package digest, parameter, severity mapping e normalizzazione HHC. Nessun tool da solo certifica l'interoperabilità end-to-end.

## 9. UI e accessibilità

Playwright è il default per E2E browser e supporta parallelismo, sharding, trace, screenshot e report aggregabili. Il profilo HHC impone:

- locator accessibili e user-visible, evitando selettori fragili;
- isolation per test, account e tenant sintetici;
- timezone, locale, lingua, high contrast, zoom e viewport rappresentativi;
- Chromium più almeno un secondo engine supportato per la release;
- trace al primo retry/failure; mai uploadare trace contenenti dati sensibili a servizi pubblici;
- screenshot diff con maschere solo per campi realmente dinamici;
- axe-core o scanner equivalente come supporto, non sostituto della prova manuale WCAG;
- sessioni manuali keyboard e screen reader sui task critici.

Gli scenari principali sono deploy/rollback, Message Explorer, quarantena/replay, audit, break-glass, approvazione mapping e visualizzazione degraded mode.

## 10. Security testing stack

### 10.1 Controlli in commit e PR

| Capability | Default/candidato | Gate |
|---|---|---|
| Secret scan | Gitleaks | pre-commit e full history controllata in onboarding |
| SAST rapido/custom | Semgrep con rule pack HHC | diff PR più full scheduled |
| SAST data-flow | CodeQL per linguaggi supportati | PR/default branch e full RC |
| SCA/license | scanner del package manager più Trivy/servizio approvato | lockfile e artifact |
| Container/IaC | Trivy più policy OPA/Conftest | image, manifest, Helm/IaC |
| Kubernetes schema | kubeconform o equivalente | CRD/schema versionati |
| SBOM | Syft o Trivy in CycloneDX/SPDX | ogni artifact rilasciabile |
| Firma/provenance | Cosign/Sigstore-compatible o PKI enterprise | build e release bundle |

Semgrep e CodeQL sono complementari soltanto se la coverage matrix attribuisce regole e ownership; duplicati normalizzati non gonfiano i KPI. SARIF è un formato di scambio, non garantisce qualità del finding.

### 10.2 Dynamic e manuale

- OWASP ZAP Automation Framework o DAST enterprise equivalente su API/UI di test;
- Schemathesis e fuzz harness per parser e API;
- test specifici per BOLA, BFLA, property authorization, SSRF, injection e deserialization;
- TLS/mTLS, certificate rotation, revocation e trust-store test;
- resource exhaustion e business-flow abuse con limiti;
- penetration test indipendente sulla release candidate;
- red/purple-team annuale e dopo cambiamenti di trust boundary.

Gli scanner non ricevono credenziali oltre lo scope e non accedono a produzione. Finding critical/high sono triaged da una persona; l'agente può proporre remediation ma non accettare il rischio.

## 11. Performance e capacity

k6 è il default per load test HTTP, WebSocket e gRPC e per workload estendibili qualificati. Threshold formalizzano pass/fail e devono riflettere SLO e business invariant, non solo response time. JMH misura microbenchmark in-process; non viene confrontato direttamente con percentile end-to-end.

Per MLLP, DICOM, file, JDBC e messaging si usano generator HHC protocol-specifici con:

- timestamp di invio/ACK/durability/delivery;
- payload-size distribution e mix clinico;
- open workload model per arrival rate quando realistico;
- conteggi inviati, accettati, rifiutati, duplicati, quarantinati e consegnati;
- unique ID e checksum per reconciliation;
- backpressure e downstream rate dichiarati;
- generator utilization sotto il limite qualificato;
- clock sync e raw time series.

Il riferimento resta [performance-and-chaos.md](./performance-and-chaos.md). Nessuna ottimizzazione supera il gate se migliora latenza perdendo audit, lineage, isolamento o durabilità.

## 12. Fault injection e chaos engineering

| Livello | Tool | Esempi |
|---|---|---|
| Processo/componente | hook controllati, Toxiproxy | crash point, reset, timeout, latency, bandwidth |
| Container/pod | Kubernetes e Chaos Mesh | pod kill, network, DNS, CPU, memory, I/O, time |
| Nodo/zone/site | orchestrazione infrastrutturale qualificata | drain, zone loss, route/identity/KMS outage |
| Dati/storage | fault harness e restore lab | corrupt object copy, stale replica, PITR, key unavailable |
| Operativo | game day/tabletop | alert, escalation, handover, decisione failover/failback |

Chaos Mesh è ammesso nel resilience lab con RBAC minimo, namespace allowlist, selector esplicito, deadline, steady-state probe, abort condition e audit. Poiché il daemon può richiedere privilegi elevati, non viene installato in produzione come conseguenza automatica della scelta di test. Esperimenti production-like o in canary richiedono processo separato, blast radius e approvazione SRE/Clinical Operations.

Ogni esperimento ha ipotesi, steady state, fault, raggio, durata, safety control, expected telemetry, abort, cleanup e reconciliation. Un fault non rimosso o una steady-state probe guasta è `INFRA_ERROR/ABORTED`, non successo.

## 13. Observability verification

OpenTelemetry Collector riceve trace, metriche e log dal test environment; Prometheus-compatible backend e dashboard versionate permettono query riproducibili. Il test verifica:

- correlation `request → event → delivery → audit`;
- assenza di PHI e secret in attribute, label, exception e baggage;
- cardinalità bounded per tenant/facility/flow senza patient identifier;
- sampling che preserva errori e tracce critiche secondo policy;
- buffer e retry durante collector/backend outage;
- alert multi-window/burn rate e routing NOC/SOC;
- timestamp, clock skew e gap detection;
- versione di instrumentation e semantic convention.

La telemetria non è l'oracle unico per perdita eventi: reconciliation usa anche raw store e delivery ledger.

## 14. Matrice degli ambienti

| Ambiente | Topologia | Dati | Rete/identity | Test autorizzati | Evidence |
|---|---|---|---|---|---|
| Developer | processo/container locale | sintetici | no produzione | unit/component/smoke | non qualificante salvo test deterministici |
| PR ephemeral | per commit, singolo trust domain | sintetici firmati | egress allowlist | static, unit, contract, component, security diff | qualificante PR |
| Integration | servizi reali condivisi con namespace isolati | sintetici | identity test | cross-service, migration, failure | nightly/build |
| Conformance lab | partner simulator e validator pinnati | pack ufficiali/sintetici | trust test | HL7/FHIR/IHE/DICOM/OMOP | claim-scoped |
| Performance | hardware/topologia dichiarati, esclusivi | sintetici volume | rete modellata | load/stress/spike/soak | raw series e capacity envelope |
| Resilience/DR | multi-zone/site production-like | sintetici | fault domains reali | chaos, failover, restore, failback | RTO/RPO e runbook |
| Pre-production | release candidate completa | sintetici; eccezione controllata | prod-like, segregata | E2E, OAT, pen test, upgrade | release evidence |
| Production canary | cell/facility e synthetic marker limitati | synthetic only | produzione | health/synthetic/limited experiment | operational evidence |

### 14.1 Multi-tenant topology minima

Ogni suite system/RC usa almeno:

- due tenant;
- due organization per almeno un tenant;
- due facility per organization;
- ID locali volutamente collidenti;
- due Runtime Cell in failure domain distinti;
- identity e key scope separati;
- un hot/noisy tenant e un tenant di controllo;
- un endpoint lento/guasto e uno sano;
- un flow Tier 0 e uno analytics Tier 2 concorrenti.

La topologia DR aggiunge secondo site/region, replica, backup immutabile e identity path alternativo.

## 15. Provisioning e reset

L'ambiente è descritto come codice e creato dalla pipeline:

1. verifica firma/digest di runner e dipendenze;
2. crea namespace/account/key temporanei;
3. applica network policy e resource quota;
4. carica fixture per seed e checksum;
5. esegue readiness e self-test dell'harness;
6. registra manifest, versioni, node type e clock status;
7. esegue test con lease esclusiva quando richiesto;
8. esporta raw evidence e checksum;
9. distrugge risorse o esegue reset verificabile;
10. revoca identity e segnala residue/leak.

Reset che fallisce impedisce di riutilizzare l'ambiente. Database snapshot accelerano test solo se content-addressed, privi di dati reali e invalidati da migration/version change.

## 16. Pipeline di riferimento

### 16.1 Pre-commit, obiettivo minuti

- format/lint/type/compile;
- unit mirati;
- secret scan;
- schema e architecture fast rules;
- nessuna chiamata cloud obbligatoria.

### 16.2 Pull request, obiettivo feedback rapido

- build riproducibile;
- unit/parameterized/property con seed;
- ArchUnit e policy test;
- mutation delta sui moduli critici;
- component e consumer/provider contract;
- OpenAPI/AsyncAPI/schema diff;
- SAST, SCA, secret, license, IaC/container;
- cross-tenant negative test interessati;
- Agentic Change Manifest se applicabile;
- merge dei report da shard anche quando uno shard fallisce.

### 16.3 Main/nightly

- integration full e mixed version N/N-1;
- parser fuzz corpus e stateful API test;
- migration/rollback e deterministic replay;
- browser/accessibility automatici;
- failure dependency, medium load e memory/leak trend;
- full SAST/SCA con rule pack aggiornato ma pinnato per run;
- coverage requirement/risk drift.

### 16.4 Release candidate

- regression e conformance complete;
- performance average/stress/spike/soak secondo piano;
- chaos/resilience, HA, backup/restore/failback;
- upgrade/rollback e schema compatibility;
- pen test/retest, accessibility manuale e OAT;
- SBOM, provenance, firme e reproducible build;
- evidence package, waiver validity e decision record.

### 16.5 Post-release

- canary e synthetic transaction;
- SLO/error-budget e data-quality drift;
- vulnerability e dependency monitoring;
- reconciliation e audit-gap monitor;
- game day e restore rehearsal pianificati.

## 17. Parallelismo, sharding e determinismo

- shard assegnati per test ID/durata storica, non ordine del filesystem;
- ogni shard ha tenant, schema, bucket, topic e port namespace propri;
- seed, timezone, locale e clock sono parametri espliciti;
- report aggregato conserva anche shard mancanti/falliti;
- test order randomizzato in nightly con seed; PR può usare ordine stabile;
- concurrency test usa barrier/latch e scheduler controllabile, non sleep come oracle;
- test seriali hanno motivo, owner e lock con timeout;
- parallelismo non satura SUT o generator oltre il profilo dichiarato;
- Playwright blob report/trace e JUnit XML/JSON vengono normalizzati nell'evidence schema senza perdere raw artifact.

## 18. Evidence pipeline

Struttura logica per run:

```text
evidence/<release>/<run-id>/
  manifest.json
  environment.json
  toolchain-bom.json
  requirements.json
  results/raw/<tool-id>/...
  results/normalized/results.json
  coverage/requirements.json
  security/findings.sarif
  performance/series-and-summary/...
  conformance/validator-output/...
  resilience/timeline.json
  logs/redacted/...
  attestations/checksums.txt
  decision/review-and-waivers.json
```

Il normalizzatore:

- conserva `PASS`, `FAIL`, `SKIP`, `ABORTED`, `INFRA_ERROR` distinti;
- non converte automaticamente warning in successo;
- registra tool/version/digest, command profile, start/end e exit code;
- collega test ID, requirement, risk, fixture, environment e commit;
- applica redaction prima dell'export;
- firma manifest e checksum dopo la chiusura;
- impedisce modifiche in-place; una correzione crea una nuova versione.

## 19. Test data factory

Una libreria centrale genera:

- persone, episodi, ordini, risultati, documenti e studi immaginari;
- tenant/facility namespace e ID collidenti controllati;
- flussi multilingue europei, Unicode e transliteration;
- timestamp con timezone/DST, partial dates e clock skew;
- unità, range, decimal precision e value set versionati;
- duplicate, correction, merge, cancel, late arrival e reorder;
- payload a dimensione limite e malformed con classificazione;
- canary token privacy e marker `SYNTHETIC-NOT-FOR-CARE`.

Generator, seed e schema sono versionati. Un test che crea casualità non registrata è non riproducibile. I synthetic ID devono essere rifiutati dai destination clinici reali mediante marker e allowlist.

## 20. Flakiness e salute della test platform

La piattaforma misura separatamente:

- flake rate primo tentativo;
- test infrastructure error rate;
- queue time e execution time per percentile;
- shard imbalance;
- resource saturation del runner;
- quarantena, owner, età ed expiry;
- false pass scoperti downstream;
- costo per suite e per finding utile.

Un test P0/security/tenancy/durability non viene quarantinato per sbloccare una release. L'infrastruttura guasta attiva rerun diagnostico ma conserva il primo esito. Tre run verdi dopo un fail non cancellano il difetto senza root cause.

## 21. Patch, upgrade e dismissione tool

- critical security fix: valutazione immediata e patch entro SLA di Product Security;
- minor/patch: qualification automatica in canary toolchain almeno mensile;
- major: compatibility branch, migration notes, benchmark e rollback;
- rule/database feed: snapshot identificato per ogni run, aggiornamento controllato;
- browser/OS/JDK/Kubernetes matrix: riesame trimestrale e prima di major release;
- tool senza maintainer/security posture adeguata: freeze, compensazione ed exit plan;
- dismissione: export storico leggibile, adapter mantenuto per retention e rimozione credenziali.

Un aggiornamento che aumenta finding non viene silenziato: si distingue nuovo difetto, regola cambiata e false positive con decisione tracciata.

## 22. Casi d'uso della toolchain

### TT-01 — Pull request agentica su deduplica

**Stack:** JUnit, property generator, PIT, Testcontainers PostgreSQL/broker, Toxiproxy, SAST e ACM.

**Prova:** duplicate e crash concorrenti con due facility; reconciliation dei ledger.

**Gate:** zero side effect duplicati dove l'idempotency contract lo garantisce; oracle indipendente; nessun test indebolito.

### TT-02 — Nuova versione Connector SDK

**Stack:** contract test kit, Pact per contratti HHC dove utile, container runtime N/N-1, simulatori protocollari e ArchUnit.

**Prova:** lifecycle, capability negotiation, config, secret rotation, drain, rollback e third-party isolation.

**Gate:** matrice SDK/runtime valida, artifact firmato, SBOM e nessuna API interna usata.

### TT-03 — API Control Plane multi-tenant

**Stack:** OpenAPI lint, Schemathesis, REST test, CodeQL/Semgrep, ZAP e Playwright.

**Prova:** BOLA/BFLA, pagination, bulk action, rate limit, stale token e UI delegation.

**Gate:** deny cross-scope e audit decision completi; schema response e error model conformi.

### TT-04 — FHIR Patient Summary europeo

**Stack:** validator HL7 FHIR ufficiale, package IG/country pack pinnati, terminology service snapshot, golden corpus e accessibility/UI test.

**Prova:** profilo, terminology, narrative/structured consistency, consent/purpose e rendering sicuro.

**Gate:** claim limitato alle capability provate; obblighi non computabili registrati per review.

### TT-05 — Imaging inter-facility

**Stack:** dcm4che qualificato, DICOM conformance pack, Toxiproxy e storage reale effimero.

**Prova:** association, transfer syntax, UID collision, storage commitment, interruption e authorization facility.

**Gate:** nessun falso commit, stream integro e audit/lineage correlati.

### TT-06 — Capacity del riferimento enterprise

**Stack:** k6 per API più generator MLLP/messaging HHC, OpenTelemetry e raw metric backend.

**Prova:** almeno 2.000 eventi/s da 8 KiB per 60 minuti nella reference cell, live più replay e noisy tenant.

**Gate:** target di latenza/capacità documentati, CPU media entro 70%, zero reconciliation mismatch e generator non saturo.

### TT-07 — Perdita di una zone

**Stack:** Chaos Mesh nel resilience lab, probe SLO, ledger reconciler e dashboard/alert test.

**Prova:** pod/node/zone failure con carico Tier 0 concorrente.

**Gate:** disponibilità entro SLO, nessun ACK falso, capacity N+1, alert e recovery automatico/operativo provati.

### TT-08 — Restore da backup immutabile

**Stack:** orchestrazione DR, scanner artifact, consistency checker, replay harness e evidence signer.

**Prova:** restore pulito, PITR, key access, forward recovery e canary.

**Gate:** RTO/RPO del tier, chain of custody, conteggi e failback approvato.

### TT-09 — Supply-chain compromise simulata

**Stack:** Gitleaks, SCA, Trivy, SBOM, signature/provenance policy e registry mirror.

**Prova:** package typosquat, image digest cambiato, secret in archive e provenance mancante.

**Gate:** build/promotion bloccata e finding correlato, senza esecuzione dell'artefatto non fidato.

### TT-10 — Operations console accessibile

**Stack:** Playwright shardato, scanner accessibilità e sessione manuale keyboard/screen reader.

**Prova:** quarantine, replay preview, approvazione, rollback e break-glass in italiano e inglese.

**Gate:** WCAG 2.2 AA applicabile, nessuna azione irreversibile ambigua, audit e focus/error recovery corretti.

## 23. Fonti ufficiali verificate

Fonti consultate il **4 settembre 2026**:

- [JUnit Framework](https://github.com/junit-team/junit-framework), incluso stato release GA;
- [Testcontainers for Java](https://github.com/testcontainers/testcontainers-java) e [Reusable Containers](https://java.testcontainers.org/features/reuse/), che risultano sperimentali e non adatti alla CI;
- [ArchUnit](https://github.com/TNG/ArchUnit), test di regole architetturali sul bytecode JVM;
- [PIT](https://github.com/hcoles/pitest), mutation testing JVM;
- [jqwik releases](https://github.com/jqwik-team/jqwik/releases), inclusa l'avvertenza upstream sull'uso con agenti dalla release 1.10;
- [Pact — come funziona](https://docs.pact.io/getting_started/how_pact_works) e [Pact JVM](https://github.com/pact-foundation/pact-jvm);
- [WireMock](https://github.com/wiremock/wiremock/releases);
- [Schemathesis](https://github.com/schemathesis/schemathesis);
- [Playwright — sharding](https://playwright.dev/docs/test-sharding) e [best practices](https://playwright.dev/docs/best-practices);
- [Grafana k6](https://grafana.com/docs/k6/latest/) e [threshold](https://grafana.com/docs/k6/latest/using-k6/thresholds/);
- [CodeQL code scanning](https://docs.github.com/en/code-security/concepts/code-scanning/codeql/codeql-code-scanning);
- [Semgrep CI](https://semgrep.dev/docs/semgrep-ci/sample-ci-configs) e [custom rule](https://semgrep.dev/docs/writing-rules/rule-ideas);
- [Trivy repository scanning](https://trivy.dev/docs/dev/guide/target/repository/) e [SBOM scanning](https://trivy.dev/docs/latest/guide/target/sbom/);
- [Gitleaks](https://github.com/gitleaks/gitleaks);
- [OWASP ZAP](https://www.zaproxy.org/docs/automate/automation-framework/);
- [Toxiproxy](https://github.com/Shopify/toxiproxy) e relative release ufficiali;
- [Chaos Mesh 2.8.4](https://chaos-mesh.org/docs/) e [workflow](https://chaos-mesh.org/docs/create-chaos-mesh-workflow/);
- fonti sanitarie elencate in [Conformità e interoperabilità](./conformance-and-interoperability.md).

Prima di modificare il BOM, il Toolchain Owner ricontrolla repository, licenza, security advisory, support matrix e digest. La data di questo documento non sostituisce la qualification date del singolo componente.

## 24. Collegamenti

- [Strategia di test](./test-strategy.md)
- [Politica agentic coding](./agentic-coding-quality-policy.md)
- [Conformità e interoperabilità](./conformance-and-interoperability.md)
- [Performance, resilienza e chaos](./performance-and-chaos.md)
- [API standards](../api/api-standards.md)
- [Connector SDK](../connectors/connector-sdk.md)
- [Scalabilità e resilienza](../architecture/scalability-and-resilience.md)

