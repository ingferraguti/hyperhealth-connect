# Supporto e runbook

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Service Operations |
| Target | Supporto enterprise 24×7 per Tier 0 |

## 1. Modello operativo

Ogni servizio/connector ha owner, tier, SLO, dashboard, alert, runbook, on-call, dependency, backup class, support matrix e escalation. Tier 0 richiede copertura 24×7 e handover. Il cliente mantiene responsabilità concordate per rete, sistemi partner e decisioni cliniche.

## 2. Ruoli

- L1 Service Desk: intake, verifica impatto, comunicazione e runbook safe;
- L2 Operations/SRE: diagnosi, mitigation, failover/replay approvato;
- L3 Engineering: defect, hotfix e deep analysis;
- Security/SOC: cyber incident e evidence;
- Privacy/DPO: data incident e rights;
- Clinical Safety/Interoperability: impatto clinico/semantico;
- Incident Commander: autorità e coordinamento;
- Communications Lead: stakeholder/status;
- Customer Facility Lead: contesto locale e workaround.

## 3. Severity

| Sev | Criterio | Target risposta iniziale |
|---|---|---|
| SEV1 | patient safety, perdita/corruzione, cross-tenant, Tier 0 multi-facility, security critical | immediata/entro SLA contrattuale |
| SEV2 | Tier 0 facility o recovery risk elevato | rapido 24×7 |
| SEV3 | Tier 1/2 degradato con workaround | business/on-call secondo contratto |
| SEV4 | richiesta, cosmetic, problema non urgente | coda pianificata |

Severity non dipende solo dal numero di record; considera safety, privacy, scope, durata e detectability.

## 4. Incident lifecycle

1. detect/intake e case ID;
2. validate signal e dichiarare severity;
3. nominare Incident Commander;
4. contenere e proteggere Tier 0/evidence;
5. comunicare facts, impatto, workaround e prossimo update;
6. diagnosticare con telemetry/ledger/audit;
7. mitigare/failover/rollback secondo autorità;
8. riconciliare dati e side effect;
9. recovery e monitoring rafforzato;
10. close con stakeholder acceptance;
11. post-incident review e remediation.

## 5. Regole di sicurezza

- niente PHI/secret in ticket, chat, email o screen share non autorizzati;
- support bundle redatto per default;
- accesso JIT, scoped, step-up e auditato;
- comandi distruttivi richiedono procedura/approval;
- copia evidence read-only e chain of custody;
- non disabilitare audit/alert per risolvere un incidente;
- agenti di coding non accedono a produzione o payload;
- credenziali condivise vietate.

## 6. Template runbook

Ogni runbook contiene:

- ID/version/owner/last drill;
- symptom e alert;
- safety/privacy preconditions;
- scope e dependency;
- permission necessarie;
- decision tree e command verificati;
- expected output e abort condition;
- mitigation, recovery e rollback;
- reconciliation/integrity check;
- escalation e comunicazione;
- evidence da allegare;
- known limitations.

Comandi usano parametri espliciti e read-only first. Screenshot non sostituisce output strutturato.

## 7. Runbook obbligatori

### RB-01 Queue/DLQ growth

Verificare input rate, destination, circuit, oldest age, spool/disk e tenant. Proteggere live Tier 0, ridurre replay/analytics, isolare destination. Non purge. Dopo recovery, re-drive governato e reconciliation.

### RB-02 Connector failure

Confermare protocol/native error, certificate, DNS/network e partner status. Isolare connector, non riavviare in loop. Validare last-known-good/config, canary e receipt prima di riaprire.

### RB-03 Certificate expiry/rotation

Inventario, chain, SAN, trust store, expiry e revocation. Ruotare con overlap controllato e synthetic handshake; non distribuire private key via ticket. Rollback conserva vecchia key solo entro finestra.

### RB-04 Control Plane unavailable

Confermare che Runtime Cell usi bundle valido, monitorare 24h spool/audit/certificate. Bloccare change; usare emergency channel solo se autorizzato. Al ritorno, riconciliare desired/observed.

### RB-05 Database failover

Valutare quorum e replica lag; fence old primary, promote, verificare schema/ledger/audit, canary e pool reconnect. Riconciliare ACK concorrenti e pianificare failback.

### RB-06 Object storage unavailable/corrupt

Fermare ACK se durability non garantita, verificare replica/checksum/key, proteggere evidence e attivare recovery. Nessun fallback su storage non cifrato.

### RB-07 Cross-tenant/security incident

SEV1, contenere identity/artifact, preservare audit, determinare scope via ledger, coinvolgere Security/Privacy/Legal. Non esplorare payload oltre necessità.

### RB-08 Semantic corruption

Bloccare rollout/mapping, identificare versioni e eventi via lineage, preservare raw, coinvolgere Clinical Safety. Correzione genera nuova derivazione e controlled replay.

### RB-09 Backup restore/site DR

Seguire ordine nel piano DR, clean-room, fencing, integrity, canary e reconciliation; misurare RTO/RPO.

### RB-10 Telemetry/SIEM outage

Verificare buffer e capacity, attivare alternate alert, non loggare PHI localmente come workaround; al recovery controllare gap/order.

## 8. Handover 24×7

Handover contiene incident attivi, degraded mode, risky change, capacity, certificate, vendor issue e next action/owner/time. È scritto e confermato; nessun passaggio dipende da memoria personale. Timezone UTC più locale.

## 9. Customer communication

Template evita speculazione: stato, inizio, capability/facility impattate, data/safety assessment corrente, workaround, prossima comunicazione. PHI e dettagli di attacco non necessari esclusi. RCA preliminare/finale distinti.

## 10. Maintenance

Change ticket, risk, scope, backup/rollback, health gate, canary, window e communication. PDB/capacity verificati prima del drain. Maintenance non viene automaticamente esclusa dallo SLO. Emergency change ha review successiva ed expiry.

## 11. Diagnostics

Support bundle include build/config digest, health, dependency, bounded metrics, redacted logs, thread/heap summary policy-safe e recent change. Esclude raw, secret, token e patient ID. Bundle ha encryption, recipient, TTL, checksum e access audit.

## 12. Problem management

Post-incident entro policy: timeline, impact, detection gap, contributing factors, recovery, data reconciliation, what worked, root/system causes e actions. Blameless non significa senza accountability. Action ha owner, priority, due date, verification e link al risk register/test.

## 13. Knowledge lifecycle

Runbook review trimestrale Tier 0, semestrale altri; immediata dopo incident/change. Drill e on-call feedback aggiornano contenuto. Runbook scaduto o mai esercitato blocca production readiness.

## 14. Casi d'uso

### SUP-01 — Risultati critici non consegnati

SEV1/2 secondo scope. Facility applica workaround clinico; SRE isola destination, protegge ingest, misura queue age e riconcilia ogni risultato dopo recovery.

### SUP-02 — Certificato partner scaduto

Nessun bypass TLS. Rotazione/partner escalation; eventi restano durable. Canary handshake e delivery precedono re-drive.

### SUP-03 — Mapping errato multi-facility

Rollout fermato, mapping revocato, impact list da lineage, review clinica e reprocessing controllato. Comunicazione distingue dato sorgente da derivato.

### SUP-04 — Ransomware nel sito primario

Security contiene; DR clean-room ripristina copie immutabili con credenziali separate. Nessun failback prima di eradicate/validation.

### SUP-05 — Spool all'80%

Alert anticipato; si stima time-to-full, sospende analytics/replay, coordina partner e applica backpressure prima del falso ACK.

## 15. Metriche

MTTD, acknowledge/engage/mitigate/resolve, SLO burn, reopen, recurrence, runbook success, escalation delay, handover gap, data reconciliation time, customer update timeliness e overdue remediation. MTTR non premia chiusure senza riconciliazione.

## 16. Gate operativo

- owner/on-call/escalation testati;
- runbook Tier 0 esercitati;
- accesso JIT e support bundle redaction verificati;
- alert routing e handover provati;
- DR/restore entro target;
- customer communication template disponibile;
- nessuna action post-incident critica scaduta senza escalation.

## 17. Collegamenti

- [Observability](./observability.md)
- [Reliability](./reliability-and-error-model.md)
- [HA e DR](../deployment/high-availability-and-dr.md)
- [Replay](./replay-and-reprocessing.md)
- [Risk register](../roadmap/risk-register.md)

## 18. Fonti ufficiali

Consultate o riconfermate il 5 settembre 2026:

- [NIST SP 800-61 Rev. 3 Incident Response](https://csrc.nist.gov/pubs/sp/800/61/r3/final)
- [NIST Cybersecurity Framework 2.0](https://www.nist.gov/cyberframework)
- [ENISA procurement guidelines for the cybersecurity of hospitals and healthcare providers, 22 luglio 2026](https://www.enisa.europa.eu/publications/procurement-guidelines-for-the-cybersecurity-of-hospitals-and-healthcare-providers)
