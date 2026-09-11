# Registro dei rischi

Stato: baseline di programma 1.1 — checkpoint WP1-00
Data di riferimento: 10 settembre 2026
Ambito: sviluppo, rilascio e operazione enterprise di HyperHealth Connect

## 1. Scopo

Il registro identifica rischi che possono compromettere sicurezza del paziente, integrità dei dati, disponibilità, conformità, performance, costi, tempi o adozione. È un artefatto operativo: viene rivisto almeno mensilmente, a ogni release gate, dopo incidenti e quando cambiano standard, dipendenze o intended purpose.

Un rischio non è “chiuso” perché esiste una mitigazione scritta. È chiuso soltanto quando il controllo è implementato, verificato e il rischio residuo è accettato dall'autorità competente.

### Checkpoint WP1-00 — R0.2

La mobilitazione R0.2 ha chiuso le decisioni su baseline tecnologica, transazione raw/ledger, ACK, idempotenza, ordering e bundle locale negli ADR-025–028. Il registro machine-readable `governance/risks.yml` aggiunge P1-R01…P1-R09 con owner, controllo e release gate.

Il rischio aggregato della Fase 1 resta **alto** finché non esistono prove G3/G4/M2/M4/M5/G8. In particolare non sono accettati: ACK senza raw+ledger verificabili, evento cross-scope, side effect duplicato inspiegato, alterazione semantica non rilevata o PHI nei segnali/evidence. La modalità solo maintainer è registrata come P1-R09: consente il build, ma non sostituisce i reviewer indipendenti elencati nel RACI.

## 2. Metodo di valutazione

### 2.1 Probabilità

| Valore | Definizione |
|---:|---|
| 1 | Rara: eccezionale nel ciclo di vita considerato |
| 2 | Improbabile: possibile ma non attesa |
| 3 | Possibile: può verificarsi durante il programma |
| 4 | Probabile: attesa senza azioni specifiche |
| 5 | Quasi certa: già osservata o sistemica |

### 2.2 Impatto

| Valore | Definizione |
|---:|---|
| 1 | Minore: nessun impatto clinico, recupero ordinario |
| 2 | Moderato: degrado limitato, nessuna perdita sostanziale |
| 3 | Significativo: SLA, costo, dati o una facility impattati |
| 4 | Grave: più facility, dati sensibili, obblighi o assistenza impattati |
| 5 | Critico: patient safety, perdita dati, grave breach o indisponibilità sistemica |

### 2.3 Livello

`Score = probabilità × impatto`

| Score | Livello | Trattamento |
|---:|---|---|
| 15–25 | Critico | sponsor e owner; piano immediato; blocca gate se residuo non approvato |
| 10–14 | Alto | mitigazione finanziata e milestone; review quindicinale |
| 5–9 | Medio | controllo nel backlog e review mensile |
| 1–4 | Basso | monitoraggio o accettazione documentata |

Per patient safety, privacy e security un impatto 5 non viene automaticamente accettato anche con bassa probabilità. Il risk owner decide; il delivery team non può auto-accettare.

## 3. Ruoli

| Ruolo | Responsabilità |
|---|---|
| Executive Sponsor | accetta rischio business critico e finanzia mitigazione |
| Product Owner | scope, valore, sequencing e claim |
| Clinical Safety Officer | hazard clinici e patient-safety acceptance |
| Chief Architect | coerenza tecnica e debito strutturale |
| CISO / Product Security | cyber risk, SSDLC e vulnerability response |
| DPO / Privacy Lead | rischio privacy e data protection |
| SRE Lead | SLO, capacity, incident, BC e DR |
| Data/Semantic Lead | mapping, terminologie, lineage e OMOP quality |
| Quality/Regulatory Lead | conformance, evidence e claim normativi |
| Delivery Lead | dipendenze, capacità team, milestone e fornitori |
| Customer Program Owner | readiness di facility, rete, processi e sistemi esterni |

## 4. Registro consolidato

La colonna score mostra `iniziale → residuo target`. Il residuo è valido solo dopo verifica dei controlli.

### 4.1 Patient safety e correttezza clinica

| ID | Rischio | Score | Owner | Segnali precoci | Mitigazione preventiva | Contingency |
|---|---|---:|---|---|---|---|
| R-CLIN-01 | Mapping errato trasforma un dato valido in un concetto clinico sbagliato. | 4×5=20 → 2×5=10 | Clinical Safety + Semantic Lead | mismatch campioni, override frequenti, coverage improvvisamente alta | mapping review clinica, test golden, versioni immutabili, domain validation, no silent default | bloccare mapping/versione, quarantena dominio, rollback e reprocessing controllato |
| R-CLIN-02 | Eventi ADT o ordini fuori sequenza producono stato paziente/episodio errato. | 4×5=20 → 2×4=8 | Runtime Lead | gap sequence, late-event rate, merge manuali | partition key, version/causation, bounded reorder, reconciliation | sospendere route interessata, ricostruire sequenza da raw, validazione con system of record |
| R-CLIN-03 | Duplicazioni dopo retry/replay generano doppio ordine, risultato o documento. | 4×5=20 → 2×4=8 | Runtime Lead | duplicate receipt, collisioni idempotency, retry storm | idempotency key, delivery ledger, dedup window, destination-specific contract | circuit open, stop replay, reconciliation e correzione tramite sistema autorevole |
| R-CLIN-04 | Perdita silenziosa di un evento accettato. | 3×5=15 → 1×5=5 | SRE + Runtime Lead | ingress/durable/delivery count divergenti | durable-before-ACK, outbox/inbox, checksums, reconciliation e fault injection | dichiarare incident critico, ricostruire da raw/spool, comunicare gap e verificare sistemi clinici |
| R-CLIN-05 | Patient identity ambigua collega dati a persona errata. | 3×5=15 → 2×5=10 | Clinical Safety + Customer | aumento unmatch/merge, identificativi discordanti | MPI adapter, confidence state, no implicit merge, quarantine e human resolution | bloccare fan-out, revocare derivati, correggere nel master e reprocessare con audit |
| R-CLIN-06 | Risultato corretto/cancellato viene trattato come nuovo risultato. | 3×5=15 → 1×5=5 | Clinical Informatics | duplicate result, status mismatch | profile test, business version, causation link e correction use case | sospendere connector, notificare destinatari, reprocessare versione corretta |
| R-CLIN-07 | Dato parziale viene presentato come completo in emergency care. | 3×5=15 → 1×5=5 | Product + Clinical Safety | timeout, partial source count, UI senza completeness flag | completeness/provenance mandatory, explicit degraded status, timeout contract | disabilitare presentazione aggregata, fallback a procedure locali |
| R-CLIN-08 | Clock skew altera sequenza e plausibilità temporale. | 3×4=12 → 1×3=3 | SRE + Data Lead | NTP drift, future timestamp, negative durations | UTC, preserve source offset, clock monitoring, plausibility rules | marcare dataset/flow, correggere source clock, reprocessare senza sovrascrivere raw |

### 4.2 Architettura, performance e affidabilità

| ID | Rischio | Score | Owner | Segnali precoci | Mitigazione preventiva | Contingency |
|---|---|---:|---|---|---|---|
| R-ARC-01 | Il Control Plane diventa dipendenza del data path. | 3×5=15 → 1×5=5 | Chief Architect | chiamate sync runtime→control, outage correlati | ADR, signed local bundle, architecture test e dependency rule | disabilitare feature dipendente, fallback config locale, redesign prima del gate |
| R-ARC-02 | Microservizi prematuri aumentano failure mode e delivery time. | 4×3=12 → 2×2=4 | Chief Architect | call graph crescente, distributed transaction, ownership incerta | modular monolith first, extraction criteria e performance budget | riaccorpare moduli o introdurre facade/event boundary |
| R-ARC-03 | Un broker/storage condiviso crea failure domain multi-azienda. | 3×5=15 → 1×5=5 | Platform Architect | noisy-neighbour, shared credentials, blast radius test fallito | Runtime Cell, isolation profile, quota, dedicated option e fault-domain map | isolare tenant, spostare su cluster dedicato, throttle traffico non critico |
| R-PERF-01 | Throughput/latency non soddisfano target Tier 0. | 4×4=16 → 2×3=6 | Performance Lead | p95/p99 regression, CPU >70%, queue age | benchmark continuo, profiling, streaming, pool separati, capacity factor 1,5 | scale-out, disabilitare enrichment non critico, rate limit e hot-path optimization |
| R-PERF-02 | Downstream lento causa backlog e saturazione globale. | 5×4=20 → 2×3=6 | SRE + Runtime Lead | connection pool saturo, queue age, timeout spike | bulkhead, circuit breaker, per-destination limit, durable queue | aprire circuito, store-and-forward, concordare catch-up rate con destinatario |
| R-PERF-03 | Autoscaling amplifica carico su sistemi clinici fragili. | 3×4=12 → 1×3=3 | SRE | scale-out seguito da failure downstream | scale su queue age con destination budget, max concurrency e token bucket | bloccare autoscaler, ridurre concurrency, drain controllato |
| R-PERF-04 | Imaging/grandi payload esauriscono memoria, rete o storage. | 4×4=16 → 2×3=6 | Imaging Lead + SRE | heap spike, disk fill, bandwidth saturation | streaming, size limit, dedicated cell, lifecycle e capacity test multi-GB | isolare imaging, spool dedicato, bandwidth cap e reroute concordato |
| R-REL-01 | Split-brain durante failover produce scritture divergenti. | 2×5=10 → 1×5=5 | SRE Lead | doppio leader, replica lag, fencing failure | quorum, fencing, single-writer ownership e DR test | fermare entrambi i writer se necessario, scegliere recovery point, reconcile |
| R-REL-02 | Restore riesce tecnicamente ma dati/applicazione sono incoerenti. | 3×5=15 → 1×4=4 | SRE + Data Lead | restore mai validato, FK/count mismatch | restore automation, application validation, manifest/checksum e drill | clean-room restore da punto precedente, rebuild indici/dataset, incident review |
| R-REL-03 | Local spool si satura durante isolamento prolungato. | 3×5=15 → 1×4=4 | Site SRE | 60/75/85% threshold, growth forecast | 24–72h sizing, compression sicura, back-pressure e capacity reserve | rifiuto prima dell'ACK secondo protocollo, storage expansion e priority routing |
| R-REL-04 | Catch-up dopo outage sovraccarica destinazione e prolunga recovery. | 4×4=16 → 2×3=6 | SRE | retry success seguito da nuovi timeout | paced replay, token bucket, priority queue e recovery model | ridurre rate, processare Tier 0 prima, concordare recovery window |
| R-REL-05 | Aggiornamento introduce versioni miste incompatibili. | 3×5=15 → 1×4=4 | Release Manager | deserialize error, schema incompatibile, mixed outcome | N/N-1 matrix, expand/migrate/contract, canary e preflight | arrestare rollout, rollback app, mantenere schema espanso e reprocessare |

### 4.3 Sicurezza, privacy e supply chain

| ID | Rischio | Score | Owner | Segnali precoci | Mitigazione preventiva | Contingency |
|---|---|---:|---|---|---|---|
| R-SEC-01 | Credenziale o certificato compromesso consente accesso a più facility. | 3×5=15 → 1×5=5 | CISO | anomalie identity, impossible travel, endpoint nuovo | workload identity per scope, mTLS, short-lived secret, rotation e least privilege | revoca immediata, quarantena cella, forensic preservation e clean credentials |
| R-SEC-02 | PHI finisce in log, trace, metriche o ticket. | 4×5=20 → 1×4=4 | Privacy + Engineering | leakage scanner, cardinalità anomala, support attachment | redaction library, no payload default, synthetic tests e DLP | bloccare export, restringere accesso, purge secondo legge, breach assessment |
| R-SEC-03 | Componente open source compromesso o vulnerabile. | 4×5=20 → 2×4=8 | Product Security | CVE, malicious release, signature mismatch | pinning, SBOM, SCA, provenance, mirror e patch policy | bloccare build/deploy, sostituire/version rollback, CRA/NIS2 assessment e notice |
| R-SEC-04 | Connector di terza parte evade il confine SDK. | 3×5=15 → 1×4=4 | SDK Lead + CISO | filesystem/network access inatteso | signed publisher, permission manifest, process isolation e contract security test | revocare connector, isolare runtime, forensic review e migrate a connector trusted |
| R-SEC-05 | Audit viene alterato, perso o reso indisponibile. | 3×5=15 → 1×5=5 | CISO + Audit Owner | chain gap, export lag, clock drift | append-only, hash chain, external checkpoint, durable buffer e separate admin | preservare copie, dichiarare integrity incident, ricostruire da fonti correlate |
| R-SEC-06 | Ransomware compromette primario, repliche e backup. | 3×5=15 → 1×5=5 | CISO + SRE | encryption activity, backup deletion, privilege anomaly | separate domain, immutable/offline copies, MFA, EDR e recovery drill | isolate, activate cyber response, clean-room restore e credential reset |
| R-SEC-07 | Segreti presenti in config, export o repository. | 3×5=15 → 1×4=4 | Product Security | secret scan, unexpected env dump | vault reference, admission policy, pre-commit/CI scan | revoke/rotate, purge artefact/history secondo procedura, impact assessment |
| R-SEC-08 | Autorizzazione cross-tenant/facility errata. | 3×5=15 → 1×5=5 | IAM Lead | negative test fail, unusual access graph | explicit scope, ABAC, RLS defense, isolation test e policy-as-code | deny-all affected scope, revoke sessions, audit investigation e patch urgente |
| R-PRV-01 | Pseudonimizzazione insufficiente consente re-identificazione. | 3×5=15 → 1×5=5 | DPO + Data Lead | small cells, linkage finding, repeated token | purpose-scoped token, key separation, disclosure control e DPIA | sospendere dataset/output, rotate token/key, notify authority per processo applicabile |
| R-PRV-02 | Retention/cancellazione non viene propagata a derivati e backup. | 4×4=16 → 2×3=6 | DPO + Data Owner | deletion backlog, lineage gap | retention metadata, lineage, lifecycle automation e backup expiry | restrict access, manual remediation, evidence delle eccezioni e customer notice |
| R-PRV-03 | Supporto tecnico accede a raw senza base o autorizzazione. | 3×5=15 → 1×4=4 | Support Lead + DPO | frequent break-glass, long sessions | scoped JIT access, reason, MFA, approval, masking e session audit | revoke, investigate, breach assessment e disciplinary/vendor process |

### 4.4 Semantica, dati e analytics

| ID | Rischio | Score | Owner | Segnali precoci | Mitigazione preventiva | Contingency |
|---|---|---:|---|---|---|---|
| R-DATA-01 | Canonical model diventa troppo generico o copia di FHIR/OMOP. | 4×4=16 → 2×3=6 | Semantic Architect | campi `extension` indiscriminati, conversioni forzate | bounded domains, ADR, invariants e use-case modelling | freeze model, adapter-specific path, major version con migration |
| R-DATA-02 | Vocabulary update cambia risultati storici senza tracciabilità. | 4×4=16 → 1×4=4 | Vocabulary Owner | count drift, cache mixed snapshot | immutable snapshot, dataset manifest e parallel activation | rollback snapshot, supersede dataset e controlled rebuild |
| R-DATA-03 | Mapping locale riusato fuori dal proprio scope. | 3×5=15 → 1×4=4 | Data Steward | anomaly per facility, mapping overlap | scope/validity mandatory, approval e negative tests | revoke mapping, quarantine outputs e reprocess affected scope |
| R-DATA-04 | Dataset OMOP viene pubblicato con qualità insufficiente. | 4×4=16 → 1×4=4 | Data Product Owner | DQD fail, unresolved unmapped codes | immutable build, release state e quality gate | ritirare alias `READY`, mark `FAILED/SUPERSEDED`, rebuild |
| R-DATA-05 | Analytics compete con workload clinico. | 3×5=15 → 1×4=4 | Platform Architect | latency correlation, DB saturation | separate DB/pool/queue, resource quota e async boundary | suspend batch/query, scale analytics separately, restore clinical headroom |
| R-DATA-06 | Lineage incompleto impedisce audit o diritto dell'interessato. | 3×4=12 → 1×4=4 | Lineage Owner | orphan node, trace gap, deletion miss | mandatory lineage edge, reconciliation e quality gate | restrict affected dataset, rebuild lineage from ledgers, manual case handling |
| R-DATA-07 | Uso secondario supera permit, purpose o scadenza. | 3×5=15 → 1×5=5 | Data Access Owner | query after expiry, export anomaly | DataUseGrant enforcement, SPE, egress control e output review | revoke access, freeze evidence, assess breach e notify HDAB/controller |
| R-AI-01 | Agente AI genera query/azioni non governate o advice clinico. | 4×5=20 → 1×5=5 | AI Governance Lead | raw SQL, prompt leakage, autonomous write | typed tool API, deny raw/write, human review, model risk assessment | disable AI gateway, revoke tokens, preserve audit e assess AI Act/MDR impact |

### 4.5 Normativa, standard e mercato europeo

| ID | Rischio | Score | Owner | Segnali precoci | Mitigazione preventiva | Contingency |
|---|---|---:|---|---|---|---|
| R-REG-01 | Atti di esecuzione EHDS richiedono cambi incompatibili. | 4×4=16 → 2×3=6 | Regulatory Lead + Architect | draft diverge da adapter, new mandatory component | regulatory watch, adapter boundary, schema registry e reserved capacity | re-prioritizzare pack, compatibility layer e customer migration plan |
| R-REG-02 | Claim “EHDS compliant” o “certificato” non supportato. | 3×5=15 → 1×4=4 | Product + Legal | marketing non allineato a evidence | claim review gate, exact capability statement e legal sign-off | ritirare claim/materiale, customer notice e corrective plan |
| R-REG-03 | Obbligo CRA di reporting non viene rispettato nei tempi. | 3×5=15 → 1×4=4 | PSIRT + Legal | exploited CVE, unclear ownership, no 24h workflow | PSIRT, reporting runbook, contact registry e tabletop | activate legal/security crisis, early warning con fatti disponibili, remediation |
| R-REG-04 | Implementazioni nazionali NIS2/GDPR divergono fra Paesi. | 4×4=16 → 2×3=6 | Legal/Regulatory | conflicting retention/reporting | country packs, configurable policy e local counsel | limit support matrix, dedicated deployment e contract amendment |
| R-REG-05 | Nuova funzione cambia intended purpose e qualifica HHC come medical device. | 3×5=15 → 1×5=5 | Regulatory + Product | diagnostic/triage claim, clinical recommendation | intended-purpose governance, feature boundary e MDR screening | feature freeze, separate regulated product line e conformity program |
| R-REG-06 | AI capability rientra in obblighi/high-risk non previsti. | 3×5=15 → 1×5=5 | AI Governance + Legal | triage/eligibility use, model provider change | AI inventory, classification, human oversight e prohibited-use policy | disable capability, conduct conformity/gap assessment, customer notice |
| R-STD-01 | Versione/profilo standard viene dichiarato supportato senza conformance. | 4×4=16 → 1×4=4 | Quality Lead | vendor-specific exception, failing profile tests | capability matrix, conformance harness e precise claims | downgrade a experimental, block deployment e issue fixed pack |
| R-LIC-01 | Licenze di terminologie o componenti vietano distribuzione prevista. | 3×4=12 → 1×4=4 | Legal + Dependency Owner | missing entitlement, territorial limit | license inventory, customer-provided packs e distribution boundary | remove package, require licensed external service, offer migration |

### 4.6 Programma, fornitori e adozione

| ID | Rischio | Score | Owner | Segnali precoci | Mitigazione preventiva | Contingency |
|---|---|---:|---|---|---|---|
| R-PRG-01 | Scope cresce più rapidamente della capacità del team. | 5×4=20 → 3×3=9 | Product + Sponsor | carry-over, WIP alto, gate compressi | outcome roadmap, WIP limit, funded parallel streams e change control | ridurre breadth, proteggere P0/gate, spostare pack non critici |
| R-PRG-02 | Competenze sanitarie/standard insufficienti causano rework. | 4×4=16 → 2×3=6 | Delivery Lead | defect di profilo, dipendenza da singolo esperto | clinical informatics, training, review esterna e pair ownership | ingaggiare specialisti, fermare claim/use case e correggere design |
| R-PRG-03 | Dipendenza da singolo maintainer/vendor open source. | 3×4=12 → 2×3=6 | Chief Architect | release ferme, issue senza risposta | abstraction boundary, fork contingency, support contract e alternatives | maintain internal fork temporaneo o sostituire adapter |
| R-PRG-04 | Cliente pilota non fornisce ambienti, dati sintetici o owner. | 4×4=16 → 2×3=6 | Customer Program Owner | milestone esterne slittano | entry criteria contrattuali, readiness checklist e sandbox | spostare pilot, usare simulatori, scegliere seconda design partner |
| R-PRG-05 | Migrazione legacy sottostima interfacce e dipendenze. | 5×4=20 → 3×3=9 | Migration Lead | inventario cambia, undocumented channel | discovery, traffic observation, CMDB e dependency mapping | waves più piccole, coexistence estesa e rollback per interface |
| R-PRG-06 | Operatori non adottano UI/runbook e ricorrono ad accessi manuali. | 4×3=12 → 2×2=4 | Adoption Lead | bypass, ticket ripetitivi, spreadsheet shadow | role-based UX, training, game day e feedback loop | embedded support, restrict unsafe paths, redesign high-friction tasks |
| R-PRG-07 | Costi infrastrutturali crescono per retention, replica e telemetry. | 4×3=12 → 2×3=6 | FinOps + Architect | storage growth sopra forecast | tiering, retention by class, sampling sicuro e chargeback/showback | ridurre copie/retention dove legale, archive, dedicate high-cost tenant |
| R-PRG-08 | Documentazione e runbook divergono dal prodotto. | 4×4=16 → 2×2=4 | Quality + Docs Owner | support mismatch, broken procedures | docs-as-code, release gate e executable checks | bloccare release/rollout, correggere e riesercitare runbook |

## 5. Rischi critici prioritari

### 5.1 R-CLIN-01 — Mapping clinico errato

**Decision trigger.** Ogni mapping con impatto diagnostico, terapeutico, alert o patient identity richiede approvazione clinica indipendente.

**Controlli verificabili.** Golden dataset, source/target domain validation, dual review, coverage per facility, shadow compare, rollback e lineage.

**Kill criterion.** Un errore non spiegato su dati clinicamente critici blocca la promozione del mapping e può sospendere il dominio, anche se il tasso aggregato è basso.

### 5.2 R-CLIN-04 — Perdita evento accettato

**Decision trigger.** Ogni modifica a ACK, raw store, broker, spool o ledger riapre il rischio.

**Controlli verificabili.** Crash matrix per durability point, checksum, count reconciliation, broker failover e 7-day endurance.

**Kill criterion.** Un solo evento accettato non ricostruibile in test di qualification blocca la release.

### 5.3 R-PERF-02 — Downstream lento

**Decision trigger.** Onboarding di ogni nuovo endpoint richiede timeout, retry, rate e backlog capacity.

**Controlli verificabili.** Slow consumer test, circuit breaker, catch-up rehearsal e forecast di oldest queue age.

**Kill criterion.** Se una destinazione può esaurire il pool Tier 0 di un'altra, il flow non entra in production.

### 5.4 R-SEC-03 — Supply chain

**Decision trigger.** Nuova dipendenza, major upgrade o vulnerabilità attivamente sfruttata.

**Controlli verificabili.** Provenance, signature, SBOM diff, SCA, license scan, dependency owner e rollback artefatto.

**Kill criterion.** Artefatto non verificabile o vulnerabilità critica senza compensating control blocca la release.

### 5.5 R-SEC-06 — Ransomware

**Decision trigger.** Modifica a identity domain, backup account, KMS o DR topology.

**Controlli verificabili.** Immutable/offline backup, separate credentials, restore clean-room, credential reset e integrity validation.

**Kill criterion.** Nessun restore verificato entro RTO impedisce GA e rinnovo dell'attestazione DR.

### 5.6 R-AI-01 — AI non governata

**Decision trigger.** Qualunque feature che invia dati a un modello o esegue un output generato.

**Controlli verificabili.** AI inventory, data-flow, provider assessment, typed tools, no raw/default, purpose check, human approval e complete audit.

**Kill criterion.** Accesso SQL/raw arbitrario o mutazione clinica autonoma impedisce il rilascio nella linea HHC standard.

### 5.7 R-REG-01 — Evoluzione EHDS

**Decision trigger.** Pubblicazione di atti di esecuzione, common specifications o aggiornamenti nazionali.

**Controlli verificabili.** Gap assessment entro 30 giorni, backlog finanziato, adapter con version contract e conformance environment.

**Kill criterion.** Nessun claim di supporto viene mantenuto se il pack non supera la versione di conformance applicabile.

### 5.8 R-PRG-01 — Scope/capacità

**Decision trigger.** Due incrementi consecutivi con carry-over >20%, quality debt crescente o gate rinviato.

**Controlli verificabili.** WIP limit, throughput trend, dependency board, scope trade-off e hiring/vendor plan.

**Kill criterion.** Security, reliability, audit o DR gate non vengono compressi; si riduce lo scope o si sposta la data.

## 6. Rischio aggregato per fase

| Fase | Rischi dominanti | Evidenza necessaria per uscire |
|---|---|---|
| 0 Mobilitazione | R-ARC-02, R-SEC-03, R-LIC-01, R-PRG-01/02 | ADR, dependency/license review, team topology, threat/hazard model |
| 1 Technical MVP | R-CLIN-03/04, R-ARC-01, R-PERF-01 | durability fault tests, reconciliation e benchmark |
| 2 Pilot | R-CLIN-01/02/05, R-SEC-01/02/08, R-REL-03/04 | 90-day SLO, isolation, outage e incident evidence |
| 3 Semantic Suite | R-DATA-01…07, R-REG-01/04, R-STD-01 | lineage, quality gate, permit control e conformance |
| 4 Enterprise | R-PERF-04, R-REL-01/02/05, R-SEC-05/06, R-REG-03 | scale, DR/failback, cyber restore, audit integrity e PSIRT |
| 5 Adaptation | R-REG-01…06, R-LIC-01 | regulatory gap, country-pack test e claim review |

## 7. Risk acceptance

Ogni accettazione contiene:

- rischio e scenario specifico;
- scope, tenant/facility/release;
- score residuo ed evidenza dei controlli;
- motivazione e alternative considerate;
- compensating controls;
- owner autorizzato;
- data di scadenza e trigger di riesame;
- conseguenze per claim, SLA e documentazione.

Non sono accettabili in modo permanente:

- perdita silenziosa nota di eventi Tier 0;
- accesso cross-tenant;
- mapping clinico ambiguo trattato come certo;
- assenza di restore verificato;
- secret o PHI esposti intenzionalmente nei log;
- audit privilegiato modificabile dagli stessi amministratori;
- claim normativo falso o non dimostrabile.

## 8. Escalation

| Condizione | Escalation |
|---|---|
| Score iniziale ≥20 | sponsor, CISO/Clinical Safety secondo dominio entro 2 giorni lavorativi |
| Score residuo ≥15 | gate bloccato salvo accettazione executive e funzione indipendente |
| Patient safety impact 5 | Clinical Safety Officer immediato |
| Potenziale personal data breach | DPO/CISO e processo incident applicabile immediato |
| Vulnerabilità sfruttata/incident CRA potenzialmente notificabile | PSIRT/Legal immediato, preservando finestre 24h/72h applicabili |
| SLO burn Tier 0 o event loss | incident P1 e stop rollout |
| Audit integrity gap | security incident e sospensione evidence export |
| Regulatory/claim mismatch | stop marketing/deployment del capability pack |

## 9. Monitoraggio del registro

La review mensile usa:

- variazione score e aging;
- controlli scaduti o non verificati;
- incident e near miss;
- trend SLO, capacity, DLQ, audit e security;
- vulnerabilità e dipendenze EOL;
- modifiche EHDS/nazionali e standard;
- readiness cliente e dipendenze esterne;
- roadmap variance e quality debt.

Il registro viene esportato come snapshot firmato per release. I rischi non spariscono dalla storia: passano a `CLOSED`, `ACCEPTED`, `TRANSFERRED` o `SUPERSEDED` con data ed evidenza.

## 10. Collegamenti e fonti ufficiali

- `mvp-to-enterprise.md`
- `release-plan.md`
- `../product/requirements.md`
- `../product/personas-and-use-cases.md`
- `../architecture/scalability-and-resilience.md`
- `../architecture/data-flow.md`
- `../architecture/data-architecture.md`
- `../security/security-architecture.md`
- `../operations/support-and-runbooks.md`
- [CRA reporting obligations](https://digital-strategy.ec.europa.eu/en/policies/cra-reporting)
- [European Health Data Space timeline](https://health.ec.europa.eu/ehealth-digital-health-and-care/european-health-data-space-regulation-ehds_en)

Fonti consultate o riconfermate il 5 settembre 2026:

- [Regolamento UE 2024/2847 Cyber Resilience Act](https://eur-lex.europa.eu/eli/reg/2024/2847/oj)
- [Direttiva UE 2022/2555 NIS2](https://eur-lex.europa.eu/eli/dir/2022/2555/oj)
- [Regolamento UE 2025/327 European Health Data Space](https://eur-lex.europa.eu/eli/reg/2025/327/oj)
- [NIST Cybersecurity Framework 2.0](https://www.nist.gov/cyberframework)
