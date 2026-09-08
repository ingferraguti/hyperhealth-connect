# Governance dei mapping

Stato: policy baseline 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Target: ciclo di vita controllato dei mapping enterprise e clinici

## 1. Scopo

Questa policy governa proposta, sviluppo, review, approvazione, pubblicazione, monitoraggio, correzione e ritiro dei mapping. Un mapping è un asset regolato: può cambiare significato clinico, routing, reporting e coorti. Git merge o test verde non sono approvazione sufficiente.

## 2. Ruoli e segregazione

| Ruolo | Responsabilità | Non può |
|---|---|---|
| Mapping Author | implementa regole/test | auto-approvare produzione |
| Technical Reviewer | type, performance, sicurezza | approvare solo il significato clinico |
| Clinical Informatician | equivalenza e rischio clinico | pubblicare artifact da solo |
| Terminology Steward | codici, versioni, licenze | cambiare flow routing |
| Data Steward | fitness analytics/OMOP | derogare controlli security |
| Product/Flow Owner | scopo, SLO, rollout | sostituire review indipendenti |
| Security/Privacy | supply chain, scope, minimizzazione | dichiarare equivalenza clinica |
| Release Manager | firma/promozione/rollback | modificare artifact approvato |
| Auditor | verifica evidence read-only | amministrare mapping |

Mapping critici richiedono almeno author + reviewer tecnico + reviewer clinico/terminologico + release approval. Privilegi sono time-bound e auditati.

## 3. Classificazione del rischio

| Classe | Esempio | Gate |
|---|---|---|
| M0 tecnico | rename non clinico, wrapper | review tecnica |
| M1 operativo | routing/facility/status | tecnica + flow owner |
| M2 semantico | code/unit/status clinical | tecnica + clinical + terminology |
| M3 safety-critical | allergy, dose, critical value, identity merge hint | independent validation, hazard analysis, canary e rollback immediato |
| M4 regulatory/secondary-use | reporting, EHDS dataset, OMOP phenotype input | data/privacy/legal owner + reproducibility evidence |

Il livello massimo delle regole incluse determina il gate. Urgenza non riduce il livello; attiva un processo emergency con scadenza e review post-change.

## 4. Stati

```text
DRAFT → IN_REVIEW → APPROVED → SIGNED → STAGED → CANARY → ACTIVE
   ↘ REJECTED        ↘ SUPERSEDED                         ↘ ROLLED_BACK
ACTIVE → DEPRECATED → RETIRED
```

Artifact approvato è immutabile. Una correzione crea nuova versione. `RETIRED` non elimina artifact/evidence necessari a replay e audit entro retention.

## 5. Change request

Ogni richiesta contiene problema, source/target, scope, rischio, owner, casi affetti, esempi sintetici, expected behavior, semantic rationale, versioni standard/terminologia, privacy impact, performance budget, rollout/rollback e migration/reprocessing decision.

Il change impact usa lineage per stimare flow, facility, destination, dataset e historical outputs. Breaking change usa major version; semantic change anche senza schema change non è patch.

## 6. Review semantica

Il reviewer verifica:

- source meaning nel contesto, non solo label;
- target meaning, domain e allowed use;
- equivalence/broader/narrower e information loss;
- dependsOn: specimen, method, body site, unit, route, dose, status;
- code system edition e validity period;
- national extension/translation e licensing;
- effect su clinical display, alerts, reporting, OMOP e research;
- no-map e ambiguous behavior;
- reversibility e source preservation.

Confidence numerica non sostituisce approvazione. AI/ML può suggerire candidati, mai auto-pubblicare; modello, prompt/input minimizzato, output e decisione umana sono auditati.

## 7. Evidence package

```text
manifest + digest + signature
source/target contracts
mapping rules and semantic entries
terminology snapshots/licenses
test corpus and expected outputs
coverage, mutation and performance reports
semantic diff from predecessor
hazard/privacy/security assessments
approvals and segregation evidence
rollout/rollback/reprocessing plan
known limitations and residual risks
SBOM and vulnerability status
```

Evidence è conservata in storage immutabile e referenziata dal release manifest. Firma verifica integrità e identità del processo, non correttezza clinica assoluta.

## 8. Test e acceptance

Test minimi: happy path, boundary/null, invalid input, ambiguous/no-map, deprecated concepts, version drift, locale/timezone/precision, duplicates/corrections, tenant override, performance, deterministic replay e rollback. Corpus usa dati sintetici; campioni reali richiedono base giuridica, minimizzazione e ambiente protetto.

Acceptance criteria sono quantitativi: coverage, zero fatal, warning budget, p95/p99, output cardinality, no-map rate atteso e comparison tolerance. Nessun “sembra corretto”.

## 9. Deployment

Promotion dev→test→preprod→prod usa lo stesso digest. Canary per facility/flow, shadow output senza delivery, comparison e health gate precedono wave rollout. Mapping e runtime compatibility sono controllati prima dell'attivazione. Activation è atomic reference switch; in-flight usa la versione risolta all'inizio.

Rollback non cancella output già consegnati. Registra effective interval, arresta nuove elaborazioni, ripristina release precedente e avvia impact/reconciliation. Se serve correzione clinica, è il system owner a governarla.

## 10. Drift e monitoraggio continuo

Si monitorano no-map/ambiguous, warning, source code nuovi, deprecated/inactive concept, unit mismatch, field/cardinality drift, output ratio, performance e facility divergence. Baseline per source impedisce che un errore di una facility sia nascosto nella media enterprise.

Trigger automatici aprono issue ma non cambiano mapping. Terminology update genera diff prima dell'adozione. Endpoint contract drift può mettere il flow in quarantine o compatibility mode esplicita.

## 11. Emergency change

Usato solo per patient safety, outage o obbligo urgente. Richiede incident/change ID, scope minimo, due approvatori indipendenti, expiry automatica, canary abbreviato ma non assente, rollback pronto e review completa entro tempo definito. Un emergency mapping non diventa permanente per inerzia.

## 12. Reprocessing

La decisione considera severità, periodo, destinatari, retention, impatto privacy e capacità. Modalità: no replay con disclosure; selective replay; full replay; new analytics dataset only. Ogni replay ha autorizzazione, rate limit, run ID, original link e reconciliation. Non riscrive raw, audit o dataset pubblicati.

## 13. Multi-tenant e override

Default di prodotto, tenant, organization, facility e endpoint sono layer separati. Override ha owner, motivazione, expiry e test. Una facility non vede mapping di un'altra. Mapping condiviso tra aziende richiede namespace neutro, licensing compatibile e approval di ogni owner o governance federata.

## 14. Audit e conformità

Audit append-only per create/edit/review/approve/sign/promote/activate/rollback/deprecate/replay/export. Include attore, ruolo, MFA/session, ticket, diff digest, scope, purpose, outcome e timestamp. Accesso auditor è read-only e auditato. Gap o signature failure è security incident.

## 15. Business continuity

Registry Git/artifact store/signing key hanno replica, backup immutabile e cyber-vault. Runtime conserva release attive e predecessor per ≥24 ore offline. RTO Control Plane ≤4 ore/RPO ≤15 minuti; il Data Plane continua con bundle firmato. Recovery drill prova checkout di versione storica, signature validation, test e activation in clean room.

## 16. Casi d'uso europei 2026

### UC-GOV-01 — nuova codifica laboratorio nazionale

Lo steward propone mapping locale→LOINC, clinical reviewer valuta metodo/specimen/unità e il release manager promuove canary. Accettazione: evidence completa, no-map baseline, rollback e licenza LOINC inclusa.

### UC-GOV-02 — concept SNOMED inattivato

Nuovo snapshot segnala inattivazione. Il registry identifica mapping/flow/dataset affetti; lo steward seleziona replacement con historical association e review. Accettazione: nessuna sostituzione automatica non validata, effective date e replay decision registrati.

### UC-GOV-03 — errore in mapping farmaco

Incident M3 arresta la release, rollbacka, determina destinatari e avvia riconciliazione. Accettazione: blast radius dal lineage, notifiche governate e post-incident review.

### UC-GOV-04 — mapping federato multicentrico

Più aziende adottano una common release con override locali limitati. Accettazione: core digest comune, override dichiarati e risultati comparabili per vocabulary/dataset version.

## 17. KPI

- lead time change per rischio;
- % mapping con owner/evidence/expiry;
- no-map e ambiguous rate;
- escaped defect e rollback rate;
- time-to-impact-analysis e time-to-recover;
- mapping/terminology drift age;
- test coverage e semantic diff reviewed;
- facility divergence e override debt.

## 18. Fonti ufficiali verificate

- [HL7 FHIR ConceptMap e relazioni di mapping](https://hl7.org/fhir/R5/conceptmap.html), consultata il 1 settembre 2026.
- [LOINC license](https://loinc.org/license), [versioning](https://loinc.org/kb/versioning) e [release notes](https://loinc.org/kb/loinc-release-notes), consultate il 1 settembre 2026.
- [SNOMED CT Release File Specification](https://docs.snomed.org/snomed-ct-specifications/snomed-ct-release-file-specification/1-introduction), consultata il 1 settembre 2026.
- [OHDSI CommonDataModel](https://github.com/OHDSI/CommonDataModel) e [CDM 5.5](https://ohdsi.github.io/CommonDataModel/cdm55.html), consultati il 1 settembre 2026.

## 19. Collegamenti

- `mapping-engine.md`
- `../semantic/semantic-mapping-registry.md`
- `../semantic/terminology-architecture.md`
- `../semantic/lineage-and-provenance.md`
