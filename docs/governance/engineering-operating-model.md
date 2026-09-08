# Operating model di engineering

| Campo | Valore |
|---|---|
| Stato | Baseline Fase 0 1.0 |
| Ultimo aggiornamento | 7 settembre 2026 |
| Owner | Engineering Leadership |
| Approvatori | Product, Architecture, Security, SRE, Quality, Clinical Safety |

## 1. Team e responsabilità

| Team | Accountable per | Non può approvare da solo |
|---|---|---|
| Product | outcome, priorità, acceptance | rischio security o clinico |
| Architecture | confini, ADR, compatibility | propria implementazione critica |
| Platform Engineering | Control Plane, build, developer platform | release senza Quality e Security |
| Runtime Engineering | worker, reliability, connector execution | SLO o patient-safety waiver |
| Integration Engineering | SDK, connector, conformance | mapping clinico senza steward |
| Clinical Informatics | canonical, mapping, terminology | base giuridica o deploy |
| Data Platform | OMOP, quality, analytics | access permit e disclosure |
| Product Security | threat, SSDLC, vulnerability | accettazione business del rischio |
| Privacy | protection design e data rights | interpretazione clinica |
| SRE | SLO, capacity, HA, DR, incident | requisito di prodotto |
| Quality Engineering | strategia, gate ed evidence | definizione dell'oracle da sola |
| Clinical Safety | hazard e safety case | decisione tecnica non safety |

## 2. Cerimonie e decisioni

- design review settimanale per boundary, dati e compatibility;
- risk review quindicinale durante MVP, almeno mensile dopo GA;
- release readiness con Product, Engineering, Quality, Security e SRE;
- clinical-safety review per funzioni che possono alterare significato, destinazione o tempestività;
- architecture decision tramite ADR, mai solo in chat o ticket;
- incidente SEV1 con Incident Commander indipendente dall'autore del change.

## 3. Segregazione dei compiti

Autore, approvatore e release operator sono ruoli distinti per modifiche critiche. Un break-glass è temporaneo, motivato, auditato e revisionato. La stessa identità non modifica sorgente, gate e firma della release senza controllo compensativo formalmente approvato.

## 4. Definition of Ready e Done

Ready richiede outcome, rischio, data class, tenant scope, SLO, acceptance e strategia di test. Done richiede code-owner review, gate verdi, artifact firmato, SBOM, documentazione, rollback, osservabilità ed evidence.

## 5. Escalation

Patient safety, cross-tenant access, perdita o corruzione, audit gap e failure della supply chain bloccano la release. Il rischio residuo è accettato dal business owner competente con durata e trattamento; non viene trasferito implicitamente alla release successiva.

## 6. Fonti

Consultate il 7 settembre 2026:

- [NIST SSDF 1.1](https://csrc.nist.gov/pubs/sp/800/218/final)
- [NIST Cybersecurity Framework 2.0](https://www.nist.gov/cyberframework)
- [SLSA specification 1.2](https://slsa.dev/spec/v1.2/)
