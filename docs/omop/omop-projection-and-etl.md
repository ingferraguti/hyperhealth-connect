# Proiezione ed ETL OMOP

Stato: specifica di sviluppo 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Baseline: OMOP CDM 5.5

## 1. Scopo

Definisce pipeline incrementale e full-build da eventi HHC a dataset OMOP 5.5. Il processo è deterministico, idempotente, restartable e loss-aware. Non modifica dati clinici sorgente e non blocca flussi assistenziali.

## 2. Modalità

| Modalità | Uso | Publication |
|---|---|---|
| Micro-batch incremental | freshness operativa analytics | nuova snapshot/versione o partition atomic |
| Scheduled rebuild | correzioni/nuovo mapping/vocabulary | nuova DatasetVersion completa |
| Historical backfill | migrazione | version isolata |
| Selective replay | defect circoscritto | nuova version/delta con impact manifest |
| Federation package | studio multicentrico | esecuzione locale su READY |

Update in-place del dataset `READY` non è default. CDC/eventual updates devono preservare correction, delete/restriction e temporal consistency.

## 3. Pipeline

```text
1 plan/authorize
2 freeze input watermark and versions
3 extract envelope/CSE references
4 stage and validate source completeness
5 resolve person/provider/care-site tokens
6 map domains and standard concepts
7 load clinical/system tables
8 build observation periods and derived eras/episodes
9 enforce referential/convention checks
10 run DQD and characterization
11 reconcile counts/checksums/lineage
12 approve and atomically publish
```

Ogni step ha checkpoint, idempotency key, retry policy, input/output count e quality findings. Rerun dello stesso step con stessi input/versioni produce stessi primary key e checksum.

## 4. Chiavi

Surrogate ID sono deterministici entro dataset tramite key registry o HMAC→collision-resolved integer. La key include tenant/source domain/source record identity/version; collisione è rilevata e stabile. Person linkage avviene prima del mapping e conserva confidence/evidence fuori dal CDM.

`person_id`, `visit_occurrence_id` e altri ID non sono riutilizzati tra DatasetVersion salvo policy deterministica. Source ID non è esposto in chiaro. `hhc_source_event_bridge` protetto collega OMOP row a envelope/CSE/load batch.

## 5. Domain assignment

Target domain deriva dallo standard concept nel vocabulary snapshot, non soltanto dal tipo messaggio. Esempi:

- diagnosi → `CONDITION_OCCURRENCE` se concept domain Condition;
- test/valore → `MEASUREMENT` se Measurement;
- osservazione qualitativa/sociale → `OBSERVATION` se Observation;
- farmaco → `DRUG_EXPOSURE` con ingredient/product mapping appropriato;
- procedura → `PROCEDURE_OCCURRENCE`;
- dispositivo → `DEVICE_EXPOSURE`;
- note/referti → `NOTE`/`NOTE_NLP` solo con governance.

Un source code che mappa a dominio inatteso genera finding e non viene forzato nella tabella desiderata.

## 6. PERSON e identità europea

Una persona per identity cluster autorizzato. `gender_concept_id` segue la semantica OMOP 5.5 verificata (sex at birth); gender identity, se disponibile e legittimo, è rappresentata secondo conventions in `OBSERVATION`, senza inferenza. I campi race/ethnicity hanno semantiche fortemente US-centriche: per dati europei non si inventano valori; si usano concept 0/source fields/conventions e assessment locale.

Data di nascita precisa è minimizzata se non necessaria; year è required secondo CDM. Location/provider/care_site riflettono il dataset e non diventano security boundary.

## 7. VISIT e facility

Encounter CSE produce `VISIT_OCCURRENCE`; trasferimenti/unità possono produrre `VISIT_DETAIL`. Regole documentano inpatient, emergency, outpatient, home, telehealth, pharmacy, laboratory, ambulance e case management. Visit multi-day non si sovrappongono illegalmente; adjacent/combined visits seguono conventions.

Source facility è mappata a `CARE_SITE` con organization hierarchy in metadata HHC. Cambiamenti organizzativi hanno effective period; non si riscrive lo storico senza nuova dataset version.

## 8. Observation period

Per EHR, assenza di record non prova assenza clinica. `OBSERVATION_PERIOD` deriva da regola esplicita per source capture e viene documentato nel manifest. Periodi overlap/back-to-back sono risolti secondo conventions. Analisi incidence/prevalence usa soltanto periodi con capture assumptions idonee.

## 9. Condition, procedure e device

Status, verification, primary diagnosis e provenance sono mappati a concept/type/status field disponibili; informazioni non rappresentabili restano nel lineage. Condition history/family history non diventa occurrence corrente. Procedure cancellation non è eseguita come procedure. Device UDI/source è minimizzato e mappato solo con vocabulary support.

## 10. Measurement e observation

Valore originale, unità, range, operator e source concept sono preservati. Conversioni UCUM sono derivate e riproducibili. Valore quantitativo incompatibile non viene caricato come numero standard. Result correction genera stato/row strategy documentata; duplicate message retry non crea doppia occurrence.

I record fuori observation period possono esistere secondo source/conventions ma sono caratterizzati. Valori critici restano tema del flusso clinico, non funzione OMOP.

## 11. Drug exposure

Prescription, dispense e administration sono eventi distinti e type concept appropriati. Product→ingredient/clinical drug mapping usa vocabulary snapshot e route/strength/form. Dose calcola quantity, days_supply, sig e dose unit solo se source sufficiente. ATC/DDD è usato per utilization analysis; DDD non è dose prescritta.

Combination product e unit pack richiedono regole specifiche. Nessun ingrediente è inferito da label libera senza mapping approvato.

## 12. Documenti, note e NLP

Testo libero entra in `NOTE` solo se purpose, minimizzazione, access control e search policy lo consentono. Il CDA/DICOM report raw resta nello store autorevole; NOTE può avere testo redatto o reference strategy compatibile con CDM. NLP è pipeline separata, con model/version, confidence, offset, negation/temporality e human validation; output AI non è fatto clinico automaticamente.

## 13. Derived tables

DRUG_ERA, DOSE_ERA, CONDITION_ERA, EPISODE ed EPISODE_EVENT sono costruite dopo eventi base con algoritmo/versione. Non sono source facts. Gli input e parametri sono nel lineage; rebuild necessario dopo mapping/vocabulary change. Tool feature support 5.5 va verificato prima dell'uso di nuovi elementi.

## 14. Delete, correction e restriction

La sorgente può correggere/annullare; ETL interpreta lifecycle e produce nuova DatasetVersion. Cancellazione GDPR/EHDS o restriction non è confusa con entered-in-error clinico. Il privacy workflow identifica righe/derivati via lineage e applica decisione autorizzata, preservando evidence minimizzata e rispettando legal hold.

## 15. Vocabulary mapping

Per ogni coded field:

1. risolvere source system/version/code;
2. trovare source concept se presente;
3. applicare mapping approvato/`Maps to` nel snapshot;
4. verificare standard concept, domain e validity al source event time/policy;
5. popolare standard concept, source concept/value;
6. registrare map entry/release e no-map reason.

Concept 0 è monitorato per domain/source/facility. Threshold non è universale: deriva dalla fitness policy. Custom concept segue range/conventions OHDSI e non collide tra tenant.

## 16. Quality gates

### 16.1 Reconciliation

- input count per source/domain/status;
- accepted/rejected/quarantined/deduplicated;
- output row per table;
- source amount/value totals ove sensati;
- checksum/manifest e watermark completeness;
- orphan/duplicate key e referential integrity;
- lineage coverage.

### 16.2 DQD

DataQualityDashboard esegue migliaia di controlli parametrizzati su conformance, completeness e plausibility. Threshold default non sostituisce knowledge locale. Gate separa `fatal`, convention e characterization; ogni failure ha owner, waiver/evidence ed expiry. Poiché feature support 5.5 è ancora indicato incompleto dal sito OHDSI, HHC qualifica la versione DQD e aggiunge controlli 5.5 mancanti senza spacciarli per official DQD.

### 16.3 Characterization

Achilles/ARES o equivalente caratterizzano distribuzioni e anomalie. Drift rispetto al predecessor è analizzato per facility, domain, concept, time e source. Un cambiamento reale di attività clinica non va “corretto” come ETL defect.

## 17. Publication

Solo `VALIDATING` immutabile entra nel gate. Approval include ETL owner, data steward, terminology, privacy/security quando applicabile e dataset owner. Publication crea catalog entry/alias atomico; consumer in corso mantiene la vecchia connection/version. `SUPERSEDED` resta read-only entro retention.

## 18. Prestazioni

Partition incremental per event time/load batch, bulk copy e set-based SQL. No row-by-row remote terminology. Stage e target hanno resource group separati. Capacity test misura rows/s, scan throughput, temp usage, DB log, index build, DQD duration, publication downtime (target zero logico) e recovery drain.

Autoscaling non sostituisce query plan/statistics. Data skew per large facility è gestito. Backfill usa quota e schedule per non competere con incremental freshness.

## 19. HA e DR

Checkpoint/manifest sono durabili e replicati. Failed worker riparte dallo step idempotente. Build zone persa può ricostruire da raw/CSE e version artifacts; DatasetVersion READY usa backup/replica. Il default Tier 2 è SLO 99,9%, RTO ≤8 ore e RPO ≤1 ora; requisiti più stringenti richiedono un profilo esplicito. Restore gate ripete schema check, counts/checksum, representative DQD e lineage.

## 20. Audit e monitoraggio

Metriche: watermark lag, input/output/reject, throughput, step duration, no-map/concept0, DQD severity, drift, lineage coverage, storage/temp, retry/failure, publication e tool compatibility. Audit include plan, versions, run, manual override, waiver, publish e export.

## 21. Casi d'uso europei 2026

### UC-ETL-01 — risultati laboratorio multi-facility

ORU/FHIR confluiscono in MEASUREMENT. Accettazione: source/result status, LOINC/unit, correction/dedup, facility quality e lineage row→raw.

### UC-ETL-02 — farmaci ospedale/territorio

Prescription, dispense e administration alimentano DRUG_EXPOSURE senza confonderli. Accettazione: type concept, product/ingredient, route/dose evidence e DDD solo analytics.

### UC-ETL-03 — refresh terminologico

Nuovo vocabulary produce DatasetVersion parallela. Accettazione: concept/domain diff, DQD/characterization, cohorts impact e rollback.

### UC-ETL-04 — richiesta di restrizione

Lineage identifica righe e dataset; nuova version applica la decisione. Accettazione: permit/policy, no in-place silent mutation, evidence e propagazione ai risultati consentita/necessaria.

### UC-ETL-05 — backfill decennale

Carico storico è partizionato e rate-limited. Accettazione: checkpoint/restart, reconcile per periodo/facility, capacity senza impatto Tier 0 e publication unica.

## 22. Test e release gate

- synthetic fixtures per tutte le tabelle/domain;
- deterministic IDs e collision tests;
- correction/delete/duplicate/out-of-order;
- terminology inactive/no-map/domain mismatch;
- DQD fatal/convention/characterization;
- source→row lineage e reverse impact;
- scale/full rebuild/incremental/noisy neighbor;
- crash/restart/checkpoint e partial commit;
- vocabulary/tool upgrade compatibility;
- backup/restore/DR e atomic publication;
- privacy/tokenization/export controls.

## 23. Fonti ufficiali verificate

- [OMOP CDM 5.5 specification](https://ohdsi.github.io/CommonDataModel/cdm55.html), consultata il 1 settembre 2026.
- [Changes 5.4→5.5 e current support](https://ohdsi.github.io/CommonDataModel/), consultati il 1 settembre 2026.
- [OHDSI CommonDataModel repository](https://github.com/OHDSI/CommonDataModel), consultato il 1 settembre 2026.
- [DataQualityDashboard documentation](https://ohdsi.github.io/DataQualityDashboard/articles/checkIndex.html) e [threshold guidance](https://ohdsi.github.io/DataQualityDashboard/articles/Thresholds.html), consultate il 1 settembre 2026.
- [WHO ATC/DDD methodology](https://www.who.int/tools/atc-ddd-toolkit/methodology), consultata il 1 settembre 2026.

## 24. Collegamenti

- `omop-architecture.md`
- `analytics-and-ohdsi-compatibility.md`
- `../semantic/terminology-architecture.md`
- `../semantic/lineage-and-provenance.md`
