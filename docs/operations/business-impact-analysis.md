# Business Impact Analysis preliminare

Stato: approvato per Fase 0; da validare per ogni installazione  
Ultimo aggiornamento: 7 settembre 2026  
Owner: SRE; approvatori: Business Continuity, Clinical Safety, Security

## Classificazione

La BIA distingue il servizio clinico dal servizio amministrativo. I valori sono obiettivi architetturali, non SLA contrattuali. Il cliente può imporre target più severi, mai meno severi senza risk acceptance.

| Tier | Capacità | MTD | RTO | RPO | Degraded mode |
|---|---|---:|---:|---:|---|
| 0 | ingestione, persistenza raw, routing urgente, ACK/NACK | 30 min | 15 min | ≤ 1 min | spool locale cifrato e replay idempotente |
| 1 | trasformazione canonical, FHIR API clinica, consenso/policy | 2 h | 60 min | ≤ 5 min | code durable, read-only dove sicuro |
| 2 | control plane, configurazione, audit search | 8 h | 4 h | ≤ 15 min | ultima configurazione firmata; nessun cambio |
| 3 | OMOP, analytics, report non urgenti | 72 h | 24 h | 24 h | sospensione e catch-up controllato |

## Dipendenze e failure domains

Tier 0 dipende da workload identity, DNS interno, broker durable, raw store e chiavi già disponibili. Non dipende sincronicamente dal control plane né da terminologie remote. Ogni azienda e facility ha quote, partizioni e circuit breaker separati per impedire noisy-neighbour. Availability zone, cluster e regione sono failure domain distinti; backup non equivale a replica.

## Strategia di continuità

Il data plane mantiene configurazioni firmate con scadenza e passa in modalità degradata predeterminata. Non inventa mapping, non riduce controlli e non inoltra se non può garantire lo scope. I messaggi restano in una coda cifrata con backpressure. Il ripristino segue: contenimento, elezione incident commander, protezione evidenze, restore infrastruttura, restore stato, riconciliazione offset/dedup, replay, verifica clinica a campione, riapertura progressiva.

Backup control-plane e audit sono cifrati, immutabili, separati dall’account primario e testati. Il drill trimestrale misura RTO/RPO effettivi, perdita, duplicati, backlog drain time e integrità degli hash. Un drill fallito apre un rischio high e blocca la produzione finché il Tier 0 non è riprovato.

## Scenari obbligatori

- perdita di un worker, nodo, zona e cluster;
- indisponibilità del control plane con data plane attivo;
- corruzione logica di configurazione e rollback a versione firmata;
- perdita/rifiuto del broker con spool e backpressure;
- compromissione credenziali e rotazione d’emergenza;
- ransomware sull’account primario e restore clean-room;
- failover regionale nel perimetro giuridico consentito;
- accumulo di 24 ore e replay senza doppie consegne.

## Evidenze richieste prima della produzione

Runbook nominativi, matrice contatti, diagramma dipendenze, inventario backup, checksum restore, report chaos, prova DNS/certificati, riconciliazione funzionale e firma congiunta SRE/Clinical Safety. Gli RTO/RPO diventano SLA soltanto dopo benchmark sulla topologia reale.

## Fonti verificate

- [ISO 22301 overview](https://www.iso.org/standard/75106.html), consultata il 7 settembre 2026.
- [Regolamento (UE) 2022/2554 — DORA](https://eur-lex.europa.eu/eli/reg/2022/2554/oj), consultato il 7 settembre 2026; applicabilità organizzativa da valutare con Legal.
- [Direttiva (UE) 2022/2555 — NIS2](https://eur-lex.europa.eu/eli/dir/2022/2555/oj), consultata il 7 settembre 2026 e soggetta al recepimento nazionale.

