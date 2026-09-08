# Contribuire a HyperHealth Connect

Stato: baseline di engineering 1.0  
Ultimo aggiornamento: 7 settembre 2026

## Regole essenziali

1. Collegare ogni modifica a requisito, rischio, difetto o ADR.
2. Usare branch brevi e pull request verso `main`.
3. Non inserire PHI, PII, credenziali, chiavi o copie di dati di produzione.
4. Aggiungere test prima o insieme al codice; i test di isolamento e redazione precedono i feature test.
5. Non modificare un oracle per far passare un comportamento errato.
6. Dichiarare nuove dipendenze e relativo owner nella dependency inventory.
7. Aggiornare documentazione, compatibilità, migration e runbook nella stessa change.
8. Eseguire `scripts/phase0-gate.ps1` o `scripts/phase0-gate.sh` prima della PR.

## Review obbligatorie

Due approvazioni e CODEOWNERS sono richiesti sul ramo protetto. Security e Privacy revisionano nuovi trust boundary o classi dati; Clinical Informatics revisiona trasformazioni semantiche; SRE revisiona Tier 0, capacità e failure behavior.

## Commit e artifact

I commit di release sono firmati. Gli artifact provengono dalla pipeline, includono SBOM e provenance e sono verificati prima del deploy. File generati non vengono modificati manualmente.

## Uso di agenti

Il contributo agentico è non fidato fino a review e test indipendenti. Prompt e tool non autorizzano accesso a produzione o dati sanitari. La PR registra l'uso materiale dell'agente e le verifiche umane, secondo [policy agentic](docs/testing/agentic-coding-quality-policy.md).

