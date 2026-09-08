# Architettura di sicurezza

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Product Security |
| Approvatori | Architecture, Privacy, SRE, Clinical Safety |

## 1. Obiettivo e modello

HHC adotta zero trust: rete, ownership o collocazione on-premises non conferiscono fiducia implicita. Ogni accesso è autenticato, autorizzato per risorsa/scope/purpose e registrato. I controlli proteggono confidenzialità, integrità, disponibilità, patient safety e tracciabilità.

Il modello usa NIST CSF 2.0 per Govern/Identify/Protect/Detect/Respond/Recover, NIST SSDF 1.1 per sviluppo e OWASP ASVS 5.0.0 come baseline verificabile applicativa. La matrice di applicabilità europea è separata per cliente/paese/ruolo.

## 2. Trust boundary

- browser/operator→Control Plane API;
- partner system→connector ingress;
- Runtime Cell→sistemi clinici e storage;
- Control Plane→Runtime Cell via bundle/status;
- workload→identity/PKI/KMS/vault;
- Runtime→Semantic/Terminology service;
- canonical→OMOP/analytics;
- componenti→telemetry/audit backend;
- support personnel→Message Explorer/support bundle;
- agente/modello→Governed AI Tool Gateway.

Ogni boundary ha protocollo, identity, data class, encryption, rate/size limit, timeout, logging e failure behavior documentati.

## 3. Threat model

Metodo STRIDE o equivalente più hazard clinici. Asset prioritari: raw/eventi, identity link, mapping/terminology, flow/configuration, signing key, audit, backup e dataset OMOP. Minacce principali:

- compromissione partner o connector;
- BOLA/BFLA e confused deputy cross-tenant;
- injection in HL7/XML/JSON/DICOM/free text;
- dependency/supply-chain compromise;
- credential theft, session hijack e workload impersonation;
- data exfiltration via log, trace, metriche, export o AI prompt;
- denial of service/noisy neighbour;
- tampering di flow, mapping, artifact o audit;
- ransomware e backup destruction;
- insider misuse/break-glass;
- prompt injection e excessive agency;
- corruzione semantica clinicamente pericolosa.

Threat model è aggiornato per nuova capability, boundary, protocollo, deployment o incidente.

## 4. Identity umane

- federazione OIDC Authorization Code + PKCE per client moderni; SAML 2.0 dove richiesto;
- MFA/step-up secondo rischio e policy cliente;
- token con issuer, audience, signature, expiry e nonce/state verificati;
- sessione breve per privilege elevato, revocation e inactivity timeout;
- account nominativi; vietati account condivisi;
- lifecycle joiner/mover/leaver sincronizzato e access review;
- break-glass separato, time-bound, motivato, alertato e post-reviewed;
- federazione non importa ruoli esterni senza mapping governato.

## 5. Workload identity e mTLS

Ogni servizio, Runtime Cell e connector ha identity distinta. Credential è short-lived e ruotabile; bootstrap usa trust anchor protetta. mTLS è richiesto tra trust domain e per collegamenti sensibili. Token workload usa audience specifica e non viene riutilizzato verso partner.

Private key resta in vault/HSM/keystore protetto; certificate inventory, expiry, revocation e rotation sono monitorati. La rotazione è provata senza downtime e con overlap limitato.

## 6. Authorization

La decisione combina RBAC, ABAC, tenant/organization/facility, ownership, environment, purpose, data class e stato. È server-side e deny-by-default. Policy decision record contiene subject, action, resource, scope, purpose, policy version, outcome e obligations.

Operazioni rafforzate: raw/PHI view, replay, export, mapping approval, release, secret/key, break-glass, backup/restore e audit access. Separation of duties impedisce all'autore di auto-approvare asset A3.

## 7. Network security

- default-deny ingress/egress e allowlist per workload;
- database/broker/admin API su reti private;
- ingress gateway con TLS, size/rate/connection limit e protocol hardening;
- egress policy per destinazione dichiarata, DNS/proxy inclusi;
- segmentazione tra plane, cell, tenant ad alto rischio e management;
- DDoS protection dove esposto;
- nessuna fiducia basata solo su source IP;
- admin access via canale forte, registrato e just-in-time;
- test periodico di reachability e policy drift.

## 8. Protezione dati e crittografia

Dati in transito e at rest sono cifrati con algoritmi e key length approvati dalla crypto policy corrente. Key hierarchy separa environment e, secondo rischio, tenant/store/purpose. Envelope encryption limita blast radius. Rotation, revocation, backup e recovery delle chiavi sono runbook testati.

Password non sono gestite dal prodotto salvo componenti identity dedicati. Secret sono reference a vault, mai in config/export/log. Hash/checksum di integrità non sostituisce MAC/firma dove è richiesta autenticità.

## 9. Application e API security

- schema validation allowlist e canonicalization controllata;
- parameterized query e output encoding;
- SSRF deny/allowlist per connector e webhook;
- XML parser hardened contro XXE/entity expansion;
- deserialization senza polymorphic gadget non autorizzati;
- upload/archivi con size, depth, content e malware policy;
- BOLA/BFLA/property authorization test;
- idempotency key scoped e anti-replay;
- pagination/query budget e rate limit;
- error response non rivela stack, PHI o esistenza cross-scope;
- CORS/CSRF/cookie/header policy per UI;
- audit di operazioni sensibili nella stessa transazione logica.

## 10. Connector e parser isolation

Input partner è sempre non fidato. Parser impone dimensione, profondità, charset, timeout e resource budget. Connector di terze parti opera out-of-process o in cella dedicata, senza filesystem/rete/segreti generici. Capability manifest e destination allowlist sono enforceable. Crash o saturazione non coinvolge altri flow.

## 11. Platform/container security

- image minimal, non-root, read-only filesystem e capability minime;
- image digest, firma, SBOM e provenance verificati in admission;
- Pod Security restricted o eccezione esplicita;
- resource limit e seccomp/LSM equivalente;
- hostPath/privileged/host network vietati per default;
- etcd/control plane protetti e auditati;
- node hardening, patching e vulnerability SLA;
- IaC/policy-as-code scan e drift detection;
- namespace non considerato unico tenant boundary.

## 12. Supply chain e secure SDLC

Dipendenze approvate, lockfile, mirror interno, SCA/licenza, SBOM CycloneDX/SPDX, artifact signature e SLSA provenance proporzionata. Build runner effimeri, least privilege e senza secret di produzione. Code review, SAST, DAST, fuzz, mutation e pen test seguono la strategia testing. Agentic coding segue ACM, sandbox e oracle indipendente.

## 13. Logging, detection e audit

Telemetry PHI-free per default; audit critico append-only/tamper-evident. Detection copre auth failure, cross-tenant deny, anomalie export/replay, signature failure, config drift, secret access, connector abuse, data egress e backup access. Alert arriva a SOC/NOC con runbook, owner e test sintetico.

Correlation non usa patient ID come label. Clock sync, gap detection, buffer 24 ore e export SIEM sono monitorati.

## 14. Vulnerability management

Inventario lega CVE a SBOM, artifact, deployment e tenant impattati. Triage considera exploitability, exposure, patient safety e compensazioni. SLA è definito dalla policy di rischio; critical exploited può richiedere revoca immediata. Patch passa regression/rollback. False positive e risk acceptance sono umani, motivati e a scadenza.

Il processo supporta coordinated vulnerability disclosure, security advisory, customer communication e obblighi CRA/NIS2 applicabili senza codificare una valutazione legale universale.

## 15. Incident response

Fasi: prepare, detect/analyse, contain, eradicate, recover, post-incident. Playbook per credential compromise, ransomware, cross-tenant access, data exfiltration, artifact compromise, audit gap e semantic corruption. Evidence mantiene chain of custody; recovery segue RTO/RPO. Security e Clinical Safety coordinano impatti sulla cura.

## 16. Casi d'uso

### SEC-01 — Token di facility A su risorsa B

API nega senza rivelare esistenza, registra decisione e genera alert se pattern anomalo. Cache e search non restituiscono metadata B.

### SEC-02 — HL7/XML malevolo

Payload oversize/entity expansion viene rifiutato o quarantinato entro budget; raw è gestito secondo policy e worker rimane disponibile.

### SEC-03 — Connector compromesso

Egress verso host non dichiarato e secret non autorizzato sono negati. Il processo è isolato, revocato e gli altri tenant continuano.

### SEC-04 — Artifact manomesso

Digest/firma/admission falliscono prima del deploy; evento security collega publisher, build e cella senza eseguire l'immagine.

### SEC-05 — Break-glass

Operatore esegue step-up, seleziona paziente/scopo/motivo e riceve accesso breve. Accesso è alertato e revisionato; download massivo resta vietato.

### SEC-06 — Prompt injection in documento clinico

Governed AI Gateway tratta il testo come dato, non amplia tool/scope, minimizza output e registra la richiesta senza PHI nel transcript non autorizzato.

## 17. Gate

- zero bypass authn/authz e zero leakage cross-tenant;
- zero secret/PHI nei canali vietati;
- threat model e ASVS control matrix aggiornati;
- pen test/retest RC completati;
- SBOM, firma e provenance verificabili;
- restore cyber e key recovery esercitati;
- alert critici end-to-end;
- nessuna vulnerabilità oltre soglia senza decisione valida.

## 18. Fonti ufficiali

Verificate il **5 settembre 2026**:

- [NIST CSF 2.0](https://www.nist.gov/publications/nist-cybersecurity-framework-csf-20);
- [NIST SP 800-207 — Zero Trust Architecture](https://csrc.nist.gov/pubs/sp/800/207/final);
- [NIST SP 800-218 — SSDF 1.1](https://csrc.nist.gov/pubs/sp/800/218/final);
- [OWASP ASVS 5.0.0](https://owasp.org/www-project-application-security-verification-standard/);
- [OWASP API Security Top 10 2023](https://owasp.org/API-Security/);
- [ENISA Health](https://www.enisa.europa.eu/topics/cybersecurity-of-critical-sectors/health), incluse linee guida procurement del 22 luglio 2026;
- [Direttiva (UE) 2022/2555 — NIS2](https://eur-lex.europa.eu/eli/dir/2022/2555/oj);
- [Regolamento (UE) 2024/2847 — CRA](https://eur-lex.europa.eu/eli/reg/2024/2847/oj).

## 19. Collegamenti

- [Tenancy e autorizzazione](./tenancy-and-authorization.md)
- [Privacy](./privacy-and-data-protection.md)
- [Testing](../testing/test-strategy.md)
- [Topologie](../deployment/deployment-topologies.md)
