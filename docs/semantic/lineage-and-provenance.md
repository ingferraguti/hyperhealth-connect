# Lineage e provenance

Stato: specifica baseline 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Target: ricostruibilità end-to-end, auditabilità e impact analysis

## 1. Scopo

Il lineage risponde a due domande: “da quali sorgenti/versioni deriva questo output?” e “dove è stato usato questo input?”. La provenance spiega attività, agenti, asset e decisioni. L'audit prova accessi/cambiamenti; lineage, audit e trace sono correlati ma non intercambiabili.

HHC adotta un modello compatibile concettualmente con W3C PROV: Entity, Activity e Agent, specializzati per integrazione sanitaria. Non richiede RDF nel runtime; esporta PROV-O/JSON dove utile.

## 2. Grafo

### 2.1 Entity

RawEvent, ParsedArtifact, CanonicalEvent, TargetArtifact, Document, DicomObject, MappingRelease, Schema/Profile, TerminologySnapshot, PolicyBundle, SoftwareBuild, DeliveryReceipt, OMOPDatasetVersion, OMOPRowGroup, CohortDefinition, AnalysisResult, EvidencePackage.

### 2.2 Activity

Receive, Parse, Validate, Transform, Translate, Route, Deliver, Reconcile, Replay, ProjectOMOP, QualityCheck, PublishDataset, ExecuteCohort, ExportResult, Delete/Restrict.

### 2.3 Agent

Workload, Connector, RuntimeCell, User, Organization, ApprovalRole, ExternalSystem. Agent clinico o umano è referenziato in forma scoped e minimizzata.

### 2.4 Edges

`wasDerivedFrom`, `wasGeneratedBy`, `used`, `wasAssociatedWith`, `wasAttributedTo`, `hadPrimarySource`, `wasRevisionOf`, `wasInvalidatedBy`, più specializzazioni HHC: `parsedWith`, `validatedAgainst`, `mappedWith`, `translatedWith`, `deliveredAs`, `projectedInto`, `checkedBy`, `authorizedBy`, `replayedFrom`.

## 3. Record minimo

```yaml
lineageEdgeId: uuid
tenantId: opaque
organizationId: opaque
fromEntity: {type, id, version?, checksum?}
relation: wasDerivedFrom
toEntity: {type, id, version?, checksum?}
activity:
  id: run-step-id
  type: Transform
  startedAt: instant
  endedAt: instant
  outcome: success|warning|failure
agent: {type: workload, id: opaque, build: exact}
assets:
  mapping: exact-ref
  terminology: exact-ref
  schema: exact-ref
  policy: exact-ref
sourcePointers: [protected-pointer]
qualityRefs: [finding-id]
auditRefs: [audit-id]
recordedAt: instant
previousHash: value
```

Il contenuto clinico non viene copiato nel grafo. Source pointer può contenere path, non valore; hash di piccoli valori usa HMAC per resistere a dictionary attack.

## 4. Granularità

| Livello | Uso | Default |
|---|---|---|
| Event/artifact | operations, replay | sempre |
| Field | mapping critico, audit semantico | per campi trasformati/materiali |
| Batch/partition | ETL ad alto volume | consentito con manifest di membership |
| OMOP row | investigazione | token/bridge table protetta o row group |
| Aggregate/result | ricerca | input dataset/cohort/query/parameters |

La granularità è fitness-based. Per OMOP, evitare un edge DB per ogni cella se insostenibile: usare deterministic row IDs, load batch, source event bridge e manifest column-level, mantenendo capacità di drill-down autorizzata.

## 5. Lineage operativo

Receive crea RawEvent/Envelope; parse usa parser/profile; transform usa mapping; delivery usa target contract e genera receipt. Fan-out produce edge distinto per destination. `DELIVERED` indica receipt contrattuale, non consumo clinico. Stati unknown e reconciliation sono activity esplicite.

Replay crea nuova activity/run e nuove entity; non riutilizza l'output ID precedente. Correction/replacement è `wasRevisionOf`, non overwrite.

## 6. Lineage semantico

Per ogni mapped concept sono conservati source coding/path, ConceptMap/map entry, relationship, context/dependsOn, terminology snapshot, reviewer/approval reference e target. Unit conversion conserva formula/library/rounding. Loss e ambiguity sono quality entity collegate.

## 7. Lineage OMOP

Ogni DatasetVersion collega:

- source organization/facility e periodi;
- input watermark e event population;
- CSE/schema/mapping/terminology release;
- ETL code/build/config e database dialect;
- DDL CDM 5.5 e vocabulary version;
- row counts/checksum per table/partition;
- DQD/Achilles results e threshold policy;
- publish/approval/permit e predecessor.

Una riga è risolta tramite `hhc_source_event_bridge` in schema protetto o manifest batch. Tabelle custom non sono esposte come estensione del CDM agli strumenti OHDSI senza schema separato. Identificativi source sono tokenizzati.

## 8. Lineage di analisi

Cohort/analysis result collega dataset version, vocabulary, cohort definition, SQLRender/tool version, parameters, code commit/container digest, executor, purpose/permit, start/end, quality status e output disclosure review. Una nuova esecuzione non sovrascrive la precedente.

## 9. Impact analysis

Query autorizzate:

- mapping/version → flow, facility, event, delivery, dataset, result;
- terminology concept/snapshot → maps/datasets/cohorts;
- software build/CVE → attività/output;
- raw event → tutti i derivati;
- dataset/result → tutte le fonti;
- policy/permit → accessi/export.

La query downstream è bounded, paginata e asincrona per grafi grandi. Access policy del nodo più restrittivo si propaga alla risposta; il grafo non diventa canale laterale cross-tenant.

## 10. Audit e tamper evidence

Lineage è append-only. Edge sono hash-chained per partition e firmati in batch; checkpoint sono copiati in trust domain separato. Audit registra chi interroga/esporta il lineage, change di retention, replay e override. Gap, impossible cycle, missing entity o invalid signature generano incident.

Audit journal resta distinto: un mapping edge non prova che un utente fosse autorizzato; l'audit decision record sì. Trace sampling non è accettato come unica evidence.

## 11. Privacy e diritti

Lineage contiene metadata personali/correlabili. Accesso è purpose/role/scoped. Retention segue l'artifact e gli obblighi di evidence, ma non giustifica copia eterna di PHI. Quando dati vengono cancellati/restritti, il grafo conserva tombstone/evidence minimizzata senza mantenere il valore eliminato. Legal hold ha owner, base e expiry.

Una richiesta GDPR/EHDS usa lineage per localizzare copie e derivati, ma decisione su cancellazione, restrizione o eccezione appartiene al titolare/autorità. Dataset anonimo richiede assessment: il solo distacco dal patient ID non basta.

## 12. Storage e API

Write path append su durable log/relational store; indice graph/search è ricostruibile. Partition per tenant/time/domain e archiviazione hot/warm/cold. API: upstream, downstream, path explain, impact, evidence export e integrity verify. Query cliniche full-text non appartengono al lineage.

Outbox collega commit del processing ledger e edge creation; reconciliation rileva attività senza edge o edge senza entity. SLO di completezza: 100% degli step che generano artifact durabile deve avere lineage entro 5 minuti; Tier 0 critical edges entro 60 secondi.

## 13. Prestazioni e resilienza

Lineage write non blocca delivery oltre il commit minimale locale: edge essenziale entra nello stesso durability/outbox boundary; arricchimento avviene asincrono. Back-pressure dell'indice non blocca flow. Query massive sono isolate dall'ingest.

SLO service 99,95%; RTO ≤2 ore, RPO ≤15 minuti per indice/servizio, ma edge essenziali seguono il RPO del ledger. Runtime offline bufferizza edge ≥24 ore. Backup/cyber-vault e restore test verificano hash chain, checkpoint, referential integrity e query upstream/downstream.

## 14. Monitoraggio

Metriche: edge rate, lag, orphan entity/edge, gap, signature failure, query latency/size, archive lag, outbox backlog, impact job age, cross-scope deny e storage growth. Alert critici su lineage gap Tier 0, hash/signature invalidi, inability to reconstruct e restore failure.

## 15. Casi d'uso europei 2026

### UC-LIN-01 — audit di risultato corretto

Auditor parte dal risultato EHR e risale a ORU raw, mapping, terminology e delivery receipt. Accettazione: chain completa, source access separatamente autorizzato, evidence firmata.

### UC-LIN-02 — concept inattivato

Impact query identifica mapping, dataset e studi che usano un concept. Accettazione: historical result resta riproducibile, nuova release è valutata e il replay è selettivo.

### UC-LIN-03 — incidente cyber su build

Una CVE colpisce un parser. Lineage determina eventi/processi eseguiti dalla build. Accettazione: blast radius per tenant/facility, nessuna esposizione cross-scope e reprocessing governato.

### UC-LIN-04 — diritto/restrizione

Il sistema privacy individua raw, CSE, indici e dataset collegati. Accettazione: decisione applicata secondo policy, tombstone minimizzata e prova senza conservare il dato cancellato.

### UC-LIN-05 — studio multicentrico

Un risultato federato risale a definizione coorte, dataset locali, vocabulary e quality status. Accettazione: riproducibilità senza centralizzare patient-level data.

## 16. Test e gate

- completezza edge per ogni pipeline e fan-out;
- upstream/downstream/path explain;
- crash/outbox/reconciliation e duplicate edge;
- hash-chain/signature/tamper/gap;
- authorization/cross-tenant inference;
- billion-edge scale, pagination e archive;
- deletion/restriction/legal hold;
- backup/restore/cyber-recovery e historical artifact;
- OMOP row/batch drill-down e analysis reproducibility.

## 17. Fonti ufficiali verificate

- [W3C PROV Overview](https://www.w3.org/TR/prov-overview/) e [PROV-O Recommendation](https://www.w3.org/TR/2013/REC-prov-o-20130430/), consultate il 1 settembre 2026.
- [W3C Trace Context](https://www.w3.org/TR/trace-context/), consultata il 1 settembre 2026; il trace è correlazione operativa, non audit completo.
- [OHDSI OMOP CDM 5.5](https://ohdsi.github.io/CommonDataModel/cdm55.html), consultato il 1 settembre 2026.

## 18. Collegamenti

- `../canonical-model/integration-envelope.md`
- `../architecture/data-architecture.md`
- `../mapping/mapping-governance.md`
- `../omop/omop-projection-and-etl.md`
