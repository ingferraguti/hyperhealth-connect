# Esecuzione della Fase 0

Stato: completata e qualificata  
Avvio: 7 settembre 2026  
Completamento: 7 settembre 2026  
Owner: Program Engineering  
Gate owner: Architecture, Product Security, SRE, Quality Engineering

## 1. Obiettivo

Portare HHC dal solo corpus documentale a una Engineering Foundation costruibile, verificabile e pronta per il Technical MVP. La fase termina esclusivamente quando tutti i deliverable e gli exit criteria della Fase 0 hanno evidenza ripetibile nel repository.

## 2. Sequenza vincolante

| ID | Attività | Output atteso | Stato |
|---|---|---|---|
| P0-01 | Audit di baseline e decisioni | checklist, ADR-001–010 e requisiti senza conflitti | DONE |
| P0-02 | Governance del repository | ownership, CODEOWNERS, branch policy, template PR | DONE |
| P0-03 | Toolchain e build riproducibile | build multi-modulo pin-nata e wrapper | DONE |
| P0-04 | Confini e moduli seed | SDK, envelope, security, runtime e control-plane compilabili | DONE |
| P0-05 | Controlli test-first e synthetic data | tenant isolation e redaction prima dei feature test | DONE |
| P0-06 | CI e software supply chain | test, SAST, SCA, secret scan, SBOM, provenance e firma | DONE |
| P0-07 | Ambienti e Infrastructure as Code | dev, integration, conformance e performance isolati | DONE |
| P0-08 | Assurance clinica, privacy e continuità | threat/privacy review, hazard log, BIA e audit schema | DONE |
| P0-09 | Casi d'uso e tracciabilità | matrice requirement–ADR–test–evidence | DONE |
| P0-10 | Qualification della fondazione | report automatico con tutti i gate verdi | DONE |

Ogni attività dipende dal completamento della precedente. Un controllo non viene marcato `DONE` sulla sola presenza del file: deve avere una verifica automatica o una review criterion esplicita.

## 3. Exit criteria

- nessuna dipendenza critica priva di owner per licenza e sicurezza;
- artifact applicativi generati da toolchain pin-nata;
- SBOM CycloneDX associato agli artifact;
- firma e verifica del digest dimostrate senza chiavi nel repository;
- reference environment ricostruibile da Compose e manifest Kubernetes;
- test di isolamento tenant e redazione eseguiti prima dei test applicativi;
- ADR-001–010 coerenti con i requisiti di prodotto;
- rischi critici con owner, trattamento e gate;
- nessun secret o dato sanitario reale nel repository e negli output di test;
- evidence report riproducibile da un solo comando.

## 4. Evidenza finale

Il comando canonico sarà `./scripts/phase0-gate.sh` su Linux oppure `./scripts/phase0-gate.ps1` su Windows. Gli output effimeri saranno salvati sotto `target/phase0-evidence/` e non versionati; il report di qualification versionato registrerà toolchain, controlli, risultato e digest.

La qualification conclusiva è registrata in [Phase 0 qualification](./phase-0-qualification.md). La policy di branch è pronta nel repository; l'enforcement server-side diventa effettivo quando il repository viene pubblicato sul forge aziendale e non è simulato localmente.
