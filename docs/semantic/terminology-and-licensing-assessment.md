# Assessment terminologie, standard e licenze

Stato: approvato per la fondazione; entitlement da verificare per installazione  
Ultimo aggiornamento: 7 settembre 2026  
Owner: Clinical Informatics; co-owner: Legal/Open Source Office

## Decisione

Codici, display, value set, mapping e versioni non vengono incorporati indiscriminatamente nell’artifact. Il runtime usa un servizio terminologico governato e registra system URI, version, mapping version e provenance. Licenza e territorio sono metadata di deployment e una configurazione non può essere promossa se l’installazione non dimostra l’entitlement richiesto.

| Asset | Uso previsto | Vincolo operativo |
|---|---|---|
| SNOMED CT | concetti clinici e mapping | licenza/affiliate e Member territory da verificare; nessun bundle universale |
| LOINC | osservazioni e documenti | rispettare licenza e attribution; conservare versione |
| UCUM | unità di misura | usare forma case-sensitive e validatore ufficiale/compatibile |
| ICD-10 / ICD-10-CM e varianti nazionali | classificazione | distinguere titolare/versione nazionale e termini soggetti a licenza |
| ATC | classificazione farmaci | seguire condizioni WHO Collaborating Centre e versione |
| HL7 FHIR e HL7 v2 | struttura/scambio | implementare release/profile dichiarati; marchi e materiali normativi secondo termini HL7 |
| DICOM | imaging | implementare edition e conformance statement; niente copia non governata di dizionari esterni |
| OMOP CDM/vocabularies | analytics | CDM e vocabularies hanno governance/licenze distinte; ATHENA entitlement per sorgente |
| IHE profiles | workflow/conformance | registrare versione Trial/Final e risultato test |

## Processo

Ogni aggiornamento apre una change request con diff concetti, termini ritirati, collisioni, mapping one-to-many, licenza, impatto sui dati storici e rollback. Clinical Informatics approva equivalenza; Legal approva redistribuzione; Security verifica sorgente e digest; Data Engineering prova la migrazione su snapshot sintetico. Le cache sono tenant/territory-aware e non possono servire contenuto non autorizzato.

La build contiene solo librerie con owner in `governance/dependencies.yml`. LGPL build-time e componenti copyleft non distribuiti sono classificati; ogni cambio d’uso riapre la review. SBOM non sostituisce l’analisi del contenuto terminologico.

## Fonti ufficiali verificate

- [SNOMED CT licensing](https://www.snomed.org/licensing), consultata il 7 settembre 2026.
- [LOINC license](https://loinc.org/license/), consultata il 7 settembre 2026.
- [UCUM specification](https://ucum.org/ucum), consultata il 7 settembre 2026.
- [HL7 FHIR license and legal](https://www.hl7.org/fhir/license.html), consultata il 7 settembre 2026.
- [DICOM standard](https://www.dicomstandard.org/current), consultata il 7 settembre 2026.
- [OHDSI Common Data Model](https://github.com/OHDSI/CommonDataModel), repository ufficiale consultato il 7 settembre 2026.
- [OHDSI Vocabulary documentation](https://ohdsi.github.io/TheBookOfOhdsi/StandardizedVocabularies.html), consultata il 7 settembre 2026.

