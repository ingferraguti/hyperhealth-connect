# Dashboard di avanzamento — Fase 1 R0.2

Stato: ACTIVE
Baseline: 10 settembre 2026
Owner: Program Engineering
Milestone: [R0.2 Technical MVP](https://github.com/ingferraguti/hyperhealth-connect/milestone/1)
Tracker machine-readable: [`governance/phase-1-delivery-tracker.yml`](../../governance/phase-1-delivery-tracker.yml)

## Stato corrente

| Work package | Stato | Gate | Epic issue | Dipendenza immediata |
|---|---|---|---|---|
| WP1-00 Mobilitazione | DONE | G1 PASS | [#3](https://github.com/ingferraguti/hyperhealth-connect/issues/3) | completato l'11 settembre 2026 |
| WP1-01 Tenancy/identity/DB | READY | G2 | [#4](https://github.com/ingferraguti/hyperhealth-connect/issues/4) | G1 |
| WP1-02 Raw/durability | READY | G3 | [#5](https://github.com/ingferraguti/hyperhealth-connect/issues/5) | G1 e scope WP1-01 |
| WP1-03 Reliability kernel | PLANNED | G4 | [#6](https://github.com/ingferraguti/hyperhealth-connect/issues/6) | G3 |
| WP1-04 Connector SDK | PLANNED | G5 | [#7](https://github.com/ingferraguti/hyperhealth-connect/issues/7) | G2–G4 |
| WP1-05 MLLP/REST/FHIR | PLANNED | G6 | [#8](https://github.com/ingferraguti/hyperhealth-connect/issues/8) | G5 |
| WP1-06 Flow/bundle/deploy | PLANNED | G7 | [#9](https://github.com/ingferraguti/hyperhealth-connect/issues/9) | G2, G4–G6 |
| WP1-07 Vertical A | PLANNED | M1 | [#10](https://github.com/ingferraguti/hyperhealth-connect/issues/10) | G3–G7 |
| WP1-08 Vertical B | PLANNED | M2 | [#11](https://github.com/ingferraguti/hyperhealth-connect/issues/11) | M1/G6 |
| WP1-09 UI operativa | PLANNED | M3 | [#12](https://github.com/ingferraguti/hyperhealth-connect/issues/12) | G2/G4/G7 |
| WP1-10 Audit/observability | PLANNED | M4 | [#13](https://github.com/ingferraguti/hyperhealth-connect/issues/13) | G2–G7 |
| WP1-11 Environment/recovery | PLANNED | M5 | [#14](https://github.com/ingferraguti/hyperhealth-connect/issues/14) | G3/G7/M4 |
| WP1-12 Qualification | PLANNED | G8 | [#15](https://github.com/ingferraguti/hyperhealth-connect/issues/15) | M1–M5 |
| WP1-13 Release | PLANNED | G9 | [#16](https://github.com/ingferraguti/hyperhealth-connect/issues/16) | G8 |

## Lettura del dashboard

- `READY` significa scope, owner, acceptance ed evidence target definiti; non significa implementato.
- `VERIFYING` significa artifact completati localmente ma non ancora qualificati dal merge/CI.
- La percentuale non viene stimata da righe di codice. Si contano work package passati dal rispettivo gate.
- Il milestone GitHub è il board operativo disponibile con i permessi correnti. Il token del maintainer non ha scope GitHub Projects; questa limitazione non riduce tracciabilità perché epic, label e stato canonico sono versionati e collegati.

## Label set

`phase:1`, `type:epic`, `priority:P0`, `gate`, `area:platform`, `area:runtime`, `area:connectors`, `area:semantic`, `area:security`, `area:sre`, `area:release`, `status:blocked`.

## Cadenza

Program Engineering aggiorna tracker, issue e questo file nello stesso change che modifica stato o gate. Review settimanale: WIP, blocker, rischio, evidence missing, staffing checkpoint e milestone forecast. Ogni gate fallito apre finding con severity, owner e scadenza; non si modifica l'oracle per rendere verde il risultato.
