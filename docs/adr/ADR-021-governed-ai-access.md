# ADR-021 — Accesso AI tramite API governate

Stato: Accepted  
Data: 5 settembre 2026  
Owner: AI Governance, Security e Data Governance

## Contesto

Accesso diretto di modelli a SQL, raw o tool amministrativi crea rischio di leakage, prompt injection, azioni non autorizzate e risultati non spiegabili.

## Decisione

Modelli e agenti accedono solo a un Governed AI Tool Gateway con tool tipizzati, schema-validati e allowlisted. Ogni chiamata porta workload identity, tenant/dataset scope, purpose, policy version, query budget e correlation ID. Il gateway applica minimizzazione, row/cell suppression, rate limit, output size e audit.

Baseline vieta SQL libero, accesso raw e mutazioni cliniche. Contenuto recuperato è dato non fidato e non amplia le autorizzazioni. Human approval è richiesta per azioni ad impatto; kill switch revoca modello/tool/versione. Output AI è dichiarato e non sostituisce decisione clinica.

## Conseguenze

- set di tool più ristretto ma verificabile;
- evaluation per leakage, injection, hallucination e misuse;
- prompt/transcript retention minimizzata;
- model/provider change richiede qualification;
- audit non conserva chain-of-thought.

## Alternative respinte

- credenziali DB al modello: privilege e audit inaccettabili;
- proxy generico di prompt: controlli semantici insufficienti;
- fidarsi delle istruzioni nel dataset: prompt injection.

## Collegamenti

- [Politica agentic coding](../testing/agentic-coding-quality-policy.md)
- [Analytics API](../api/analytics-api.md)
- [Security architecture](../security/security-architecture.md)
