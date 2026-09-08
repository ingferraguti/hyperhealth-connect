# Mappa della documentazione

Stato: indice della baseline documentale 1.0.  
Ultimo aggiornamento: 7 settembre 2026.

Questo indice organizza la documentazione che accompagna sviluppo, qualification e rilascio di HyperHealth Connect. Le baseline già compilate definiscono requisiti verificabili; le decisioni architetturali strutturali vengono confermate o sostituite mediante ADR approvati. Lo stato e la data riportati nel singolo documento prevalgono sullo stato della directory.

## Ordine di lettura

1. `product/`, `roadmap/` e `architecture/` definiscono obiettivi, percorso MVP e forma finale del prodotto.
2. `standards/`, `canonical-model/`, `mapping/`, `semantic/` e `omop/` definiscono il dominio sanitario e dei dati.
3. `api/`, `connectors/`, `security/`, `operations/`, `deployment/`, `development/` e `testing/` definiscono i contratti realizzativi e operativi.
4. `adr/` raccoglie le decisioni strutturali approvate; `governance/` ne governa la documentazione.
5. `reference/` registra provenienza, assunzioni ritirate e rapporto con il materiale sorgente.

Per il quality engineering, iniziare dall'[indice testing](./testing/README.md), quindi leggere strategia, politica agentic coding, toolchain, conformità e performance/chaos.

Per l'avvio dell'implementazione, usare il piano verificabile [Esecuzione della Fase 0](./roadmap/phase-0-execution.md).

La Fase 0 è conclusa con esito `PASS`; risultati, digest e limiti sono nel [report di qualification](./roadmap/phase-0-qualification.md).

La toolchain effettivamente qualificata, con versioni, pin e fonti ufficiali, è descritta nella [toolchain baseline](./development/toolchain-baseline.md).

## Convenzioni documentali

- Ogni baseline indica owner o responsabili, stato, versione/data e riferimenti pertinenti.
- Le configurazioni e i contratti sono trattati come codice, versionati e verificabili.
- Gli esempi e gli artifact di sviluppo non includono payload clinici o dati personali reali.
- Un claim di conformità o produzione è limitato alle capability dimostrate dall'evidence package della release.
