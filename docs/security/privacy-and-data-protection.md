# Privacy e protezione dei dati

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Privacy Engineering e DPO delegate |
| Nota | Baseline tecnica; applicabilità e base giuridica sono valutate per cliente/Paese |

## 1. Finalità

La privacy è proprietà architetturale verificabile, non un banner o un filtro finale. HHC tratta dati sanitari e metadata potenzialmente identificativi; controller/processor/subprocessor, finalità, basi, trasferimenti e tempi sono definiti contrattualmente e nella DPIA applicabile.

Questa baseline non fornisce parere legale. Impone capability tecniche per attuare minimizzazione, limitazione della finalità, sicurezza, accountability e diritti secondo configurazione autorizzata.

## 2. Classificazione dati

| Classe | Esempi | Default |
|---|---|---|
| P0 Public | schema e standard pubblici | accesso aperto secondo licenza |
| P1 Internal | config non sensibile, metriche aggregate | workforce autorizzata |
| P2 Confidential | topology, endpoint, tenant metadata | need-to-know |
| P3 Personal | nominativi operatori, audit subject | cifrato e scoped |
| P4 Health/PHI | payload, patient identity, clinical data | massimo controllo e residency |
| P5 Secret/Key | token, private key, recovery material | vault/HSM; mai in log/export |

La classificazione segue derivati, cache, backup, trace e support bundle.

## 3. Data inventory e record of processing

Per data element: origine, subject, finalità, ruolo, lawful basis registrata dal cliente, recipient, location, retention, encryption, access, transfer, deletion, backup e owner. Nuovo flow/connector/dataset non passa in produzione senza data-flow entry e privacy review proporzionata.

## 4. Minimizzazione

- Control Plane riceve metadata tecnici, non payload, per default;
- Integration Envelope non duplica contenuto clinico;
- mapping e lineage usano riferimenti opachi;
- API restituiscono campi minimi per ruolo/purpose;
- export richiede selezione e preview;
- analytics usa dataset/purpose dedicato;
- log/trace/metriche escludono payload e patient ID;
- prompt e coding agent non ricevono PHI;
- test usa dati sintetici.

## 5. Purpose limitation

Purpose è attributo policy per accesso, dataset, export, replay e AI tool. Un'autorizzazione assistenziale non implica ricerca o supporto. Secondary use crea Dataset Instance e identity/token scope separati. Purpose change richiede nuova valutazione, non aggiornamento silenzioso di un campo.

## 6. Pseudonimizzazione e tokenizzazione

Il servizio opera nel trust domain autorizzato prima dell'analytics. Token è non semantico, keyed e scoped per tenant/dataset/purpose. Vault e key sono separati dal CDM; re-identification è processo privilegiato. Salt/hash semplice di identifier prevedibile è vietato.

Linkage tra purpose usa servizio e approvazione espliciti. Rotation/re-tokenization conserva lineage e impedisce mixed token version.

## 7. Masking e redaction

Display applica field-level masking e obligations. Redaction logging è allowlist strutturale prima dell'export, con scanner canary. Errori non includono payload, query completa, authorization header o stack verso client. Free text è sempre ad alto rischio: niente regex come unico controllo.

Support bundle include manifest dei campi e report di redaction; screenshot e trace UI sono trattati come potenziale P4.

## 8. Date e quasi-identificatori

Date cliniche devono mantenere precisione necessaria alla cura; analytics può applicare generalizzazione/shift coerente per persona secondo protocollo. Age, ZIP, facility rara, diagnosi e timestamp combinati possono re-identificare. K-anonymity o soglie aggregate sono controlli parziali, non garanzia universale.

## 9. Campi liberi, documenti e immagini

Narrative, PDF, CDA, immagini DICOM e burned-in annotation possono contenere identificativi non dichiarati. Pipeline classifica, limita accesso, scansiona dove appropriato e non promette de-identification automatica perfetta. DICOM de-identification usa profilo dichiarato e revisione del caso d'uso; pixel data richiede controllo dedicato.

## 10. Data residency e trasferimenti

Policy specifica region/site per raw, metadata, audit, backup, telemetry e support. Repliche e SaaS di monitoring/AI sono inclusi nel data flow. Nessun trasferimento è implicito perché cifrato. Runtime Cell locale permette elaborazione senza payload nel Control Plane.

## 11. Retention, deletion e legal hold

Periodo è configurato per data class, finalità, paese e contratto; il prodotto non impone una durata legale universale. Lifecycle include active/archive/delete, backup expiry, key lifecycle, legal hold e tombstone minimo. Legal hold è scoped, approvato e revisionato.

Deletion verifica object, replica, index, cache e dataset derivati; backup segue expiry e access restriction. Impossibilità tecnica/legale è registrata con compensazione.

## 12. Diritti dell'interessato

HHC fornisce search/subject index governato, export machine-readable, rectification tramite nuova versione/lineage, restriction flag, deletion workflow e access log evidence. L'esecuzione dipende dal ruolo controller/processor e dalle istruzioni autorizzate; il prodotto non decide autonomamente validità della richiesta.

Correzione non altera raw/audit in-place: aggiunge record e limita l'uso precedente secondo policy.

## 13. Accesso e confidentiality

RBAC/ABAC, scope e purpose; step-up per raw/export; JIT support; break-glass; periodic review. Accesso DB diretto a PHI non è percorso operativo. Query e result analytics hanno disclosure control. Segregazione fra customer support, SRE, analyst e security.

## 14. Privacy by design workflow

1. data-flow e use case;
2. classificazione/ruoli/finalità;
3. minimizzazione e alternative;
4. threat/misuse e DPIA trigger;
5. retention/residency/recipient;
6. controlli e requirement;
7. synthetic privacy test;
8. approval e evidence;
9. monitoring/incident/rights;
10. review su change.

## 15. Testing e continuous control

- cross-tenant leakage e authorization matrix;
- canary PHI in log/trace/metriche/error/support bundle;
- export scope e recipient;
- deletion propagation e backup expiry;
- purpose change/revocation;
- re-identification/membership inference per output analytics/AI;
- free-text e image leakage;
- retention job e legal hold;
- incident notification workflow;
- restore che preserva policy e deletion state.

## 16. Breach e incident

Detection collega evento, data class, scope, subject population, recipient e timeline. Containment preserva evidence senza diffondere payload. Privacy/Security/Legal determinano breach e notifica nei tempi applicabili; il software fornisce conteggi, lineage, access log e export controllato, non prende la decisione legale.

## 17. EHDS readiness 2026

Il Regolamento (UE) 2025/327 è in vigore e si applica dal 26 marzo 2027 con fasi successive. Nel 2026 HHC prepara interoperability/logging component evidence, data access/export, identity, audit e secondary-use controls. Non dichiara conformità EHDS prima delle common specification e della valutazione del ruolo effettivo (middleware generale, EHR system/component o altro).

## 18. Casi d'uso

### PRIV-01 — Supporto su messaggio fallito

Operatore vede metadata e codice errore. Raw richiede case, purpose, step-up e TTL; il support bundle è redatto e non entra in ticket non autorizzato.

### PRIV-02 — Dataset OMOP per ricerca

Identity è tokenizzata nel trust domain, dataset è purpose-scoped e pubblicato dopo DQ/disclosure gate. Analyst non accede al vault.

### PRIV-03 — Rettifica

Una correzione clinica crea nuovo evento/lineage e supersedes il derivato. Raw e audit restano integri; consumer riceve amendment secondo contratto.

### PRIV-04 — Cancellazione autorizzata

Workflow localizza copie e derivati, rispetta legal hold, elimina/limita secondo policy e produce evidence senza conservare PHI nel tombstone.

### PRIV-05 — Imaging

Header DICOM e pixel burned-in sono valutati separatamente. Un profilo che rimuove tag non è dichiarato sufficiente per pixel data.

### PRIV-06 — Agente di coding

Payload reale incollato viene bloccato da DLP; si costruisce fixture sintetica. Transcript non conserva il dato e il tentativo è auditato in forma minimizzata.

## 19. Metriche

PHI leak finding, privileged access, stale grant, raw views/export, deletion SLA, retention job success, backup expiry, unclassified field, DPIA/change coverage, support bundle redaction, privacy incident e expired legal hold.

## 20. Gate

- data inventory e owner completi per P4/P5;
- zero PHI/secret nei canali vietati;
- residency e backup path verificati;
- rights/retention/deletion test superati;
- purpose e re-identification controls applicati;
- privacy incident playbook esercitato;
- nessun claim normativo senza matrice legale corrente.

## 21. Fonti ufficiali

Verificate il **5 settembre 2026**:

- [Regolamento (UE) 2016/679 — GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj);
- [Regolamento (UE) 2025/327 — EHDS](https://eur-lex.europa.eu/eli/reg/2025/327/oj/), applicazione dal 26 marzo 2027 con fasi successive;
- [EDPB Guidelines](https://www.edpb.europa.eu/our-work-tools/general-guidance/guidelines-recommendations-best-practices_en);
- [ENISA — Health sector](https://www.enisa.europa.eu/topics/cybersecurity-of-critical-sectors/health).

## 22. Collegamenti

- [Tenancy](./tenancy-and-authorization.md)
- [ADR-018](../adr/ADR-018-privacy-pseudonymization-boundary.md)
- [Data architecture](../architecture/data-architecture.md)
- [OMOP architecture](../omop/omop-architecture.md)
