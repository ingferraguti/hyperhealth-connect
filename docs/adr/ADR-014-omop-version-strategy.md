# ADR-014 — Strategia di versione OMOP

Stato: Accepted  
Data: 5 settembre 2026  
Owner: OMOP Platform

## Contesto

CDM, vocabulary, ETL e tool OHDSI hanno cicli indipendenti. Un dataset senza queste coordinate non è riproducibile.

## Decisione

La baseline di prodotto è OMOP CDM 5.5. Ogni Dataset Version registra CDM version, DDL digest, vocabulary release, ETL/mapping software, input watermark e quality rule pack. Dataset pubblicati sono immutabili.

Upgrade crea nuovo schema/dataset parallelo, esegue rebuild o migration qualificata, DQD e semantic diff, quindi switch atomico dei consumer. Tool OHDSI sono ammessi per combinazioni esplicitamente testate; compatibilità parziale è dichiarata per feature.

## Conseguenze

- coesistenza temporanea aumenta storage;
- rollback è cambio di alias, non downgrade distruttivo;
- query/cohort result registra dataset version;
- vocabulary upgrade richiede impact analysis;
- nessun `latest vocabulary` implicito.

## Alternative respinte

- upgrade in-place del dataset pubblicato: non riproducibile;
- assumere compatibilità per sola versione CDM: ignora tool/vocabulary;
- mantenere indefinitamente tutte le versioni: costo senza governance.

## Collegamenti

- [OMOP architecture](../omop/omop-architecture.md)
- [OHDSI compatibility](../omop/analytics-and-ohdsi-compatibility.md)
- [ADR-019](./ADR-019-ohdsi-compatibility.md)
