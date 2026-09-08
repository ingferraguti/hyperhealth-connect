# Testing e quality engineering HHC

| Campo | Valore |
|---|---|
| Stato | Indice normativo 1.0 |
| Target | HHC enterprise multi-azienda, multi-facility e multi-site |
| Ultimo aggiornamento | 4 settembre 2026 |
| Owner | Quality Engineering |

## 1. Scopo della sezione

Questa sezione definisce come HHC produce evidenza verificabile di correttezza, sicurezza, interoperabilità, prestazioni, resilienza, business continuity, disaster recovery, auditabilità e operabilità. È parte della baseline di prodotto: un requisito non è completato finché non dispone di criteri osservabili e prove proporzionate al rischio.

I documenti coprono sia il prodotto enterprise finale sia l'MVP. L'MVP usa gli stessi confini e le stesse invarianti del prodotto finale, riducendo ampiezza di feature, combinazioni e automazione; non rimanda isolamento, durabilità, audit, restore o sicurezza.

## 2. Ordine di lettura

1. [Strategia di test](./test-strategy.md): modello di qualità, livelli, gate, ruoli ed evidence.
2. [Politica per agentic e vibe coding](./agentic-coding-quality-policy.md): autonomia, sandbox, oracle indipendente, anti-gaming e tracciabilità delle modifiche assistite.
3. [Toolchain e ambienti](./test-toolchain-and-environments.md): strumenti, BOM, runner, ambienti e pipeline.
4. [Conformità e interoperabilità](./conformance-and-interoperability.md): HL7 v2, FHIR, CDA/IHE, DICOM, terminologie e OMOP.
5. [Performance, resilienza e chaos](./performance-and-chaos.md): capacity, SLO, fault injection, HA, BC/DR e game day.

## 3. Autorità dei documenti

In caso di conflitto:

1. requisito di prodotto e ADR approvato;
2. standard/profilo ufficiale pinnato e matrice di applicabilità;
3. strategia e policy di questa sezione;
4. Toolchain BOM e test plan della release;
5. implementazione e test correnti.

Un test verde non modifica un requisito. Se la documentazione e l'implementazione divergono, si apre un defect/change record e si preserva l'evidenza; non si aggiorna automaticamente l'oracle per adattarlo al codice.

## 4. Invarianti comuni

- isolamento esplicito `Tenant → Organization → Facility → Application → Endpoint`;
- Runtime Cell come confine di failure, scaling e data locality;
- zero perdita silenziosa e riconciliazione di input, ledger, output e quarantena;
- RPO locale 0 per evento Tier 0 dopo ACK durabile;
- at-least-once, idempotenza, deduplica e outcome `UNKNOWN` gestito;
- raw immutabile, checksum, lineage e semantic version ricostruibili;
- OMOP/analytics non dipendono dal fast path clinico;
- audit critico append-only/tamper-evident e monitoring continuo;
- deny-by-default e test negativi cross-tenant per ogni percorso positivo;
- restore, failover, rollback e runtime autonomy dimostrati, non solo documentati;
- codice prodotto da persone o agenti soggetto agli stessi gate, con verifica più indipendente per rischio A3.

## 5. Matrice documentale

| Esigenza | Documento authoritative | Evidenza principale |
|---|---|---|
| Quality gate e release | test-strategy | requirements matrix e release evidence |
| Agentic/vibe coding | agentic-coding-quality-policy | Agentic Change Manifest e independent oracle |
| Tool/versioni/runner | test-toolchain-and-environments | Test Toolchain BOM e environment manifest |
| Standard sanitari | conformance-and-interoperability | Conformance Pack e validator raw output |
| Throughput/latency | performance-and-chaos | raw series, workload e capacity envelope |
| HA/BC/DR | performance-and-chaos | fault timeline, RTO/RPO, reconciliation e runbook |
| Sicurezza | test-strategy più policy agentic | ASVS/control matrix, findings e retest |
| Audit/monitoring | test-strategy e performance-and-chaos | correlation, gap/tamper e alert evidence |

## 6. Gate MVP e prodotto finale

### MVP intermedio

Il gate MVP richiede almeno:

- flusso HL7 v2/MLLP e API/connector prioritari con raw, ACK durabile, retry, DLQ e reconciliation;
- due tenant e più facility con test negativi;
- deploy/rollback, audit, telemetry, backup e restore esercitati;
- golden mapping e nessuna normalizzazione silenziosa;
- pipeline PR, SBOM, secret/SAST/SCA, component/contract/E2E;
- baseline performance della topologia dichiarata;
- Agentic Change Manifest per contributi assistiti e due-person review sui P0.

### Prodotto enterprise finale

Estende l'MVP con:

- matrice protocolli/partner/versioni e conformance lab completa;
- deployment multi-site, capacity N+1, failover/failback e DR periodico;
- performance/soak e noisy-neighbour a scala enterprise;
- pen test, red/purple-team e supply-chain attestata;
- country pack, accessibilità, EHDS readiness e matrici normative applicabili;
- qualification continua di tool, agenti, modelli, runtime N/N-1 e connector di terze parti;
- operational acceptance 24×7 con NOC, SOC, SRE e facility operator.

L'estensione avviene aggiungendo coverage e scala, non sostituendo architettura o semantica dell'MVP.

## 7. Definition of Done trasversale

Una modifica è `DONE` soltanto quando:

- requisito, rischio, use case e classe agentica sono registrati;
- implementazione e test rispettano i confini architetturali;
- test previsti sono eseguiti su build e ambiente identificati;
- failure path, tenancy e audit sono coperti se applicabili;
- fixture e oracle hanno owner e provenance;
- security/privacy/clinical review è presente secondo rischio;
- performance budget è verificato o dichiarato non applicabile con motivo;
- migration, rollout, rollback e observability sono aggiornati;
- finding e flaky test non sono nascosti;
- evidence è immutabile, firmata e collegata al change;
- documentazione e claim descrivono soltanto capability provate.

## 8. Stato delle fonti

Ogni documento riporta le proprie fonti ufficiali e la data di verifica. Prima di una release candidate vengono ricontrollati standard, package, tool, support matrix e normativa applicabile. “Consultato” indica la data della verifica documentale, non una garanzia che una pagina rimanga immutata né una certificazione di conformità.

