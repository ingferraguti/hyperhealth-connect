# ADR-019 — Architettura di compatibilità OHDSI

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Analytics Platform

## Contesto

L'ecosistema OHDSI offre strumenti utili, ma versioni, runtime e assunzioni non devono determinare il core operativo HHC né ampliare accesso ai dati.

## Decisione

HHC integra OHDSI tramite adapter e job isolati. OMOP CDM e vocabulary sono contratti dati; WebAPI, Atlas, Achilles, DataQualityDashboard e librerie sono capability opzionali con matrice versione/feature qualificata. Nessun componente OHDSI è dipendenza del fast path o del Control Plane core.

Tool eseguono in work schema/compute pool con identity read-only o privilegi minimi. SQL e output sono registrati; risultati vengono pubblicati solo dopo quality/disclosure gate. Vulnerabilità o incompatibilità possono disabilitare un tool senza rendere indisponibile HHC.

## Conseguenze

- maggiore sostituibilità e sicurezza;
- adapter da mantenere e testare per versione;
- claim “OHDSI-compatible” limitato a feature provate;
- upgrade indipendente da release runtime;
- query costose soggette a budget.

## Alternative respinte

- incorporare l'intero stack come core: accoppiamento e superficie;
- fork permanente dei tool: manutenzione insostenibile;
- accesso diretto senza adapter: audit/purpose insufficienti.

## Collegamenti

- [Analytics e compatibilità OHDSI](../omop/analytics-and-ohdsi-compatibility.md)
- [OMOP architecture](../omop/omop-architecture.md)
- [ADR-014](./ADR-014-omop-version-strategy.md)
