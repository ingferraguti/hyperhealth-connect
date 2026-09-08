# Casi d’uso e test design della Fase 0

Stato: approvato; test end-to-end pianificati per Technical MVP  
Ultimo aggiornamento: 7 settembre 2026

## Casi completi prioritari

| ID | Scenario europeo 2026 | Attori e precondizioni | Happy path | Negativi e recovery | Evidenza/gate |
|---|---|---|---|---|---|
| UC-CLIN-01 | risultato laboratorio multi-facility | LIS A, HHC, EHR A; identità workload e mapping approvati | ORU persistito, scoped, validato, canonicalizzato, consegnato una volta e auditato | unità incompatibile→quarantena; retry→dedup; correzione→nuova versione collegata | golden corpus, lineage, latency SLO |
| UC-CLIN-02 | ordine/referto radiologico | CPOE, RIS/PACS, EHR; DICOM conformance statement | ordine correlato, metadata DICOM separati dai pixel, referto finale propagato | accession ambiguo→stop; PACS down→queue; referto amended→update controllato | contract test e replay |
| UC-EU-01 | Patient Summary transfrontaliero | nodo nazionale, adapter MyHealth@EU, consenso/policy applicabili | profilo/versione identificati, validazione, terminologia autorizzata e risposta scoped | profile mismatch→OperationOutcome; terminologia non autorizzata→deny; timeout→nessun fallback semantico | conformance report per versione |
| UC-PHARMA-01 | prescrizione e dispensazione | prescrittore, farmacia, sistema regionale | stato preservato e idempotency impedisce doppia dispensazione | revoca concorrente→ordine causale; duplicato→same receipt | concurrency and lifecycle tests |
| UC-SEC-01 | tentativo cross-tenant | workload Facility A con ID evento B | richiesta negata prima di payload access | header spoof→deny; audit sink down→buffer/privileged fail-closed | `TenantScopeTest`, audit schema |
| UC-OPS-01 | perdita control plane | worker con configurazione firmata valida | Tier 0 continua entro lease, cambi bloccati, telemetria segnala degraded | config scaduta→safe stop; ritorno control plane→reconcile senza doppio replay | chaos test e BIA drill |
| UC-DR-01 | perdita cluster/region | backup immutabile e target predisposto | restore, offset reconcile, hash check, ripartenza canary | backup corrotto→fallback generation; regione non ammessa→no failover | RTO/RPO report |
| UC-AN-01 | proiezione OMOP multi-CDM | canonical event versionato e policy secondary use | pseudonimo, vocabulary version, ETL lineage, DQD gate | mapping mancante→reject set; delete/correction→incremental reconcile | DQD, row-count and provenance |

## Sequenza dei gate

La CI esegue nell’ordine: repository policy → synthetic/secret scan → security-core tenant/redaction → unit/contract → context smoke → SAST/SCA → SBOM → firma/provenance. I test funzionali non possono mascherare un fallimento di isolamento. Performance, chaos, conformance e restore usano ambienti separati e identità distinte.

## Oracle e criteri

Ogni caso definisce input sintetico, stato iniziale, output semantico, audit, side effect, idempotency e comportamento dopo retry. Un test “passa” solo se output e assenza di effetti vietati sono entrambi verificati. Correzione, cancellazione, ordine fuori sequenza, messaggio malformato, allegato grande, clock skew, indisponibilità downstream e doppia consegna sono varianti obbligatorie.

La matrice eseguibile è `governance/traceability.yml`. Gli ID di requirement provengono da `docs/product/requirements.md`; ADR e controlli non possono restare orfani.

