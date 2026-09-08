# Catalogo dei connector

| Campo | Valore |
|---|---|
| Stato | Catalogo target enterprise 1.0 |
| Ambito | Connector ufficiali, profili e pack HHC |
| Target | Produzione europea multiazienda e multifacility |
| Ultimo aggiornamento | 1 settembre 2026 |
| Responsabili | Connector Platform, Interoperability, Product, Security, SRE |

## 1. Finalità e natura dei claim

Il catalogo definisce capability, confini, failure semantics, requisiti di qualifica e release train dei connector che costituiscono il cuore operativo HHC. Non è una dichiarazione che il codice sia già implementato o certificato.

Una capability è commercialmente dichiarabile solo quando la specifica combinazione di connector, versione, protocollo/profilo, ruolo, opzioni e Runtime release è `QUALIFIED` con Conformance Pack non scaduto. “Supporta FHIR”, “compatibile DICOM” o “conforme IHE” senza questo perimetro è vietato.

Il contratto tecnico comune è [connector-sdk.md](./connector-sdk.md); la qualifica segue [conformance-and-interoperability.md](../testing/conformance-and-interoperability.md).

## 2. Stati del catalogo

| Stato | Significato |
|---|---|
| `DESIGN` | specifica approvata, codice/evidenza non completi |
| `IMPLEMENTED` | artifact disponibile, non qualificato per produzione |
| `QUALIFYING` | test e partner matrix in corso |
| `QUALIFIED` | tutti i gate applicabili superati per perimetro dichiarato |
| `QUALIFIED_WITH_LIMITATIONS` | produzione ammessa entro limiti pubblicati |
| `EXPERIMENTAL` | laboratorio/pilot controllato, nessun claim production |
| `DEPRECATED` | supportato durante migrazione con Sunset |
| `REVOKED` | uso e nuove attivazioni vietati |
| `RETIRED` | fuori supporto |

Lo stato è per `definition version + Runtime range + protocol/profile + options`, non solo per nome.

## 3. Release train coerente con la roadmap

| Treno | Periodo roadmap | Connector scope |
|---|---|---|
| R0.2 Technical MVP | Q1 2027 | HL7 v2/MLLP, generic REST e FHIR client; vertical slice reale |
| R0.4 Production Pilot | H2 2027 | HA dei connector MVP, file/SFTP e messaging prioritario per pilot |
| R0.6 Semantic & OMOP | Q1 2028 | JDBC/batch legacy e terminology integration governata |
| R0.85 Interoperability Pack | H1 2028 | FHIR server/profile, CDA/IHE e SOAP necessari ai partner |
| R0.9 Enterprise RC | Q3 2028 | DICOM/DICOMweb, multi-tenant isolation, full connector assurance |
| R1.0 Enterprise GA | Q4 2028 | catalogo P0 qualificato, LTS, HA/DR e support matrix |
| R1.x Country Packs | 2027–2031 | MyHealth@EU/EHDS/national adapter secondo specifiche applicabili |

L'MVP riusa SDK, envelope, ledger, artifact e test della soluzione finale. Non implementa un secondo motore usa-e-getta.

## 4. Matrice target Enterprise GA

| ID | Famiglia | Direzioni | Target | Criticità tipica |
|---|---|---|---|---|
| `hl7v2-mllp` | HL7 v2 su MLLP | ingress/egress | P0 GA | Tier 0/1 |
| `fhir-rest` | HL7 FHIR REST | server/client | P0 GA | Tier 0/1 |
| `http-rest` | HTTP/REST/Webhook generico | server/client | P0 GA | Tier 0/1 |
| `file-sftp` | file/SFTP managed transfer | source/sink | P0 GA | Tier 1/2 |
| `object-storage` | S3-compatible/object manifest | source/sink | P0 GA | Tier 1/2 |
| `jdbc-extract` | DB read/batch/change watermark | source | P0 GA | Tier 1/2 |
| `messaging` | JMS/AMQP/Kafka profile | source/sink | P0 GA | Tier 0/1 |
| `soap-ws` | SOAP/HTTP e WS security profile | server/client | P1 GA | Tier 1 |
| `ihe-iti` | XDS/XCA/XCPD/PIX/PDQ/MHD pack | actor-specific | P1/pack | Tier 0/1 |
| `cda-document` | CDA validate/render/transform | processor/source/sink | P1/pack | Tier 1 |
| `dicom-dimse` | DICOM DIMSE | SCU/SCP | P1 GA | Tier 0/1 |
| `dicomweb` | QIDO/WADO/STOW | server/client | P1 GA | Tier 0/1 |
| `terminology-fhir` | FHIR terminology service | client/server | P1 GA | Tier 0/1 |
| `siem-audit` | syslog/CEF/JSON/OTLP audit-security | sink | P0 Ops | Evidence |
| `national-adapter` | profili europei/nazionali | profile-specific | Country pack | dipende dal caso |

P0/P1 indica priorità di prodotto, non stato di qualifica.

## 5. Scheda catalogo obbligatoria

Ogni voce pubblicata include:

- connector ID/version e publisher;
- SDK/Runtime range;
- artifact/SBOM/provenance digest;
- execution/isolation mode;
- protocol/standard/profile/version;
- direction, role/actor e options;
- capability SDK e limitation;
- config schema e secret categories;
- ingress ACK/durability contract;
- egress receipt/idempotency/reconciliation;
- max payload/concurrency/throughput envelope;
- HA pattern e failure domain;
- security/threat model;
- observability/audit/runbook;
- Conformance Pack, partner matrix e expiry;
- lifecycle/support/EOL;
- license e third-party dependency BOM.

## 6. HL7 v2 / MLLP

### 6.1 Perimetro

- versioni target HL7 v2.3–2.9 per profilo esatto;
- message/trigger/structure espliciti;
- inbound listener e outbound client;
- original/enhanced acknowledgment secondo profilo;
- MLLP framing, charset, TLS tunnel/mTLS dove adottato;
- segmenti Z e local profile come asset separati.

HAPI HL7v2 è adapter candidato, non fonte del claim. Le strutture predefinite ufficialmente visibili arrivano fino a v2.8.1; v2.9 richiede modelli/profile e test specifici.

### 6.2 Semantica

Ingress default Tier 0: `ACK_ON_DURABLE`; raw+ledger committati prima di AA/CA. AE/AR/CE/CR dipendono dal profilo e dal punto di validazione. `MSH-10`/assigning authority/hash alimentano dedup ma non sono universalmente unici.

Egress distingue socket write, ACK accept, application error e lost ACK. Lost ACK è `UNKNOWN`; query/reconciliation o resend destination-specific.

### 6.3 HA e prestazioni

Listener multi-replica dietro L4 solo se source/reconnect/dedup qualificati; altrimenti active-passive con fencing. Connection affinity non è ownership permanente. Target fast path: payload ≤64 KiB nel budget HHC p95≤50 ms/p99≤100 ms.

### 6.4 Rischi specifici

Framing injection, charset, oversized segment, ACK storm, connection monopolization, merge/unmerge, code ambiguity, duplicate control ID e source spoofing.

## 7. FHIR REST

### 7.1 Versioni e ruoli

R4, R4B e R5 sono connector/profile distinti. Client e server capability non sono inferite l'una dall'altra. Ogni instance pinna core package, IG/dependencies, validator, terminology snapshot e CapabilityStatement atteso.

### 7.2 Operazioni

- read/search/create/update/patch/delete solo se dichiarate;
- conditional interaction, ETag e version concurrency;
- batch/transaction con semantica nativa;
- Bundle document/message;
- operation e subscription solo per Conformance Pack;
- Bulk Data 3.0.0 per casi popolazionali separati;
- SMART App Launch/Backend Services 2.2.0 dove richiesto.

### 7.3 Failure e security

OperationOutcome preservato e normalizzato. 429/503 rispettano Retry-After; timeout su write è ambiguo e richiede conditional/idempotency/read-back. Scope SMART non sostituisce policy tenant/purpose HHC. Reference fetch, search cost, SSRF, BOLA e mass assignment sono controllati.

### 7.4 HA

HTTP stateless multi-zona, connection pool per endpoint/identity, DNS/certificate rotation, circuit per partner. Subscription/event state è durabile e riconciliabile.

## 8. HTTP REST e webhook generico

Supporta OpenAPI 3.2/3.1 qualificata, JSON/XML/form/multipart entro allowlist. Config definisce method, URL template, auth, schema, status mapping, idempotency, timeout, pagination e rate policy.

Webhook ingress verifica mTLS/OAuth/signature e replay window; response 2xx segue durability contract. Webhook egress firma body+timestamp+destination e usa delivery ID. Redirect è disabilitato o allowlisted; URL dinamici non possono raggiungere metadata/internal network.

Non esiste un generico “200=clinicamente consegnato”: il profile mapping definisce receipt.

## 9. File, SFTP e object storage

### 9.1 File/SFTP

- polling/listener bounded;
- atomic rename, done marker o stability window;
- remote host key pin/rotation, no accept-new in produzione;
- directory/path allowlist e chroot equivalente;
- filename non trusted e non usato come patient ID;
- manifest/checksum/count;
- duplicate name same/different hash;
- archive/quarantine e receipt file opzionale;
- resume solo con protocol/profile verificato.

SFTP non ha un unico standard finale che definisca tutte le varianti operative: client/server/version/algoritmi e comportamento sono qualificati nella partner matrix.

### 9.2 Object storage

- endpoint/bucket/prefix da registry;
- workload identity/short-lived credential;
- object version, ETag non usato come checksum universale;
- checksum HHC esplicito;
- event notification at-least-once e periodic reconciliation;
- multipart, abort incomplete upload e lifecycle;
- object lock/retention quando richiesto;
- presigned URL con TTL/scope minimi.

## 10. JDBC extract

Connector solo read nella baseline:

- query template approvati e parameter binding;
- schema/table/view allowlist;
- credential read-only e network scope;
- snapshot isolation coerente con sorgente;
- watermark composto e tie-breaker stabile;
- CDC solo tramite adapter/prodotto qualificato separato;
- fetch size/streaming e max row/LOB;
- timezone/decimal/charset mapping;
- schema drift detection;
- checkpoint dopo durabilità HHC;
- source query timeout e workload guard.

Non scrive sul database clinico, non crea trigger e non usa `SELECT *` non versionato. JDBC driver/version è pinnato nel BOM e qualificato col DB/vendor reale.

## 11. Messaging

Profili separati per JMS provider, AMQP 1.0 e Kafka-compatible protocol; MQTT 5.0 solo per use case approvato, non come default clinico.

### 11.1 Contratto comune

- broker/product/version e binding;
- destination/topic/queue e tenancy;
- authentication/TLS;
- message schema/content type;
- producer confirmation;
- consumer ack/offset/transaction;
- partition/ordering key;
- retry/DLQ ownership;
- max size/compression/header;
- retention/replay;
- duplicate/redelivery semantics.

Consumer commit avviene dopo durability HHC. Producer success richiede broker confirmation definita, ma non prova consumer business success. Exactly-once del broker non diventa exactly-once verso sistemi esterni.

### 11.2 HA

Broker quorum/replication è dipendenza qualificata. Connector gestisce rebalance, leader change, connection storm e credential rotation. Poison message non blocca partition indefinitamente; quarantena mantiene offset/evidence.

## 12. SOAP e web service legacy

- SOAP 1.1/1.2 e WSDL/XSD versionati;
- document/literal preferito; operation/action esplicita;
- WS-Addressing/Security solo profili necessari e qualificati;
- XML signature/encryption con wrapping defense;
- XXE/XInclude disabilitati;
- MTOM streaming per attachment;
- SOAP Fault normalizzato senza perdere detail richiesto;
- WS-ReliableMessaging non assunto salvo partner test;
- timeout/unknown response e idempotency business definiti.

## 13. CDA e IHE ITI

Connector pack costruito su attori/transazioni, non su etichetta generica. Candidati: XDS.b, XCA, XCPD, PIX/PDQ, PIXm/PDQm, MHD e ATNA secondo bisogno.

Open eHealth IPF può fornire building block su Camel per IHE/HL7/CDA, ma capability e profili sono qualificati sulla release. Final Text e Trial Implementation restano distinti.

Il CDA processor verifica schema/template/Schematron/terminology/narrative/signature/metadata. XDS/MHD preserva document/metadata association, unique ID e submission status. Query federate hanno fan-out limit, deadline e risultato parziale esplicito.

Gateway per aziende/trust domain sono separati; tenant non è derivato da metadata del documento.

## 14. DICOM DIMSE

### 14.1 Ruoli

- Storage SCU/SCP;
- Query/Retrieve SCU/SCP se qualificati;
- Storage Commitment;
- Modality Worklist/MPPS solo pack dedicati;
- SOP Class/transfer syntax/options nella Conformance Statement.

dcm4che è toolkit candidato. La release 5.34.3 consultata incorpora dizionario DICOM 2026a, mentre lo standard corrente è 2026c: la compatibilità non è presunta.

### 14.2 Semantica e HA

Association negotiation, presentation context, DIMSE status, timeout e max PDU/concurrency sono espliciti. C-STORE success non equivale a storage commitment. SOP UID collision same/different hash è trattata. Pixel data è streaming.

Active-active richiede AE title/routing e partner test; active-passive usa fencing. DR verifica AE/DNS/certificato/allowlist, UID dedup e commitment reconciliation.

## 15. DICOMweb

Operazioni target: QIDO-RS, WADO-RS e STOW-RS secondo capability. Supporta multipart, content negotiation, bulk data, range/streaming e limit.

- query filter allowlist e result pagination/limit;
- authorization per study/series/instance e facility;
- STOW response per instance, partial failure esplicito;
- WADO large object streaming e disconnect;
- transfer syntax transcoding solo se dichiarato e qualificato;
- no cache condivisa di pixel/metadata sensibili;
- OAuth/mTLS e audit coerenti con il profilo.

## 16. Terminology FHIR connector

Client/server adapter per `$lookup`, `$validate-code`, `$expand`, `$subsumes`, `$translate` e capability. Pin di system/version/snapshot; cache locale firmata per runtime. Large expansion è job/materialized artifact. License e language/designation policy applicate.

Outage non crea mapping inventati: usa snapshot ammesso, quarantena o errore secondo binding/rischio. Query context può rivelare dati e non entra nei log.

## 17. SIEM e audit connector

Sink per security/audit con profili syslog TLS, CEF/JSON o OTLP secondo ricevente.

- audit source append-only e local durable buffer;
- mTLS e certificate rotation;
- batch/ack/checkpoint;
- ordering/gap/duplicate marker;
- redaction e schema version;
- 24 ore di collector outage entro capacity dichiarata;
- fallback non usa file world-readable;
- SIEM indisponibile non cancella evidence;
- critical local alert path alternativo.

## 18. National e European adapter pack

Country pack contiene:

- paese/programma e governance owner;
- standard/IG/package/version/digest;
- actor/role/transaction/options;
- terminology/license/language;
- identity/trust/eID dependency;
- consent/purpose/residency/retention bindings;
- conformance/accreditation evidence;
- partner endpoints e test environment;
- migration e regulatory watch.

Patient Summary, ePrescription/eDispensation, imaging, laboratory result e discharge report seguono calendario EHDS e specifiche adottate. Nel 2026 sono adapter evolvibili: nessun frozen proprietary core e nessun claim MyHealth@EU/EHDS senza percorso applicabile.

## 19. Isolamento e deployment profile

| Profilo | Uso |
|---|---|
| Shared trusted cell | tenant con rischio/scala compatibili, quote forti |
| Dedicated tenant cell | grande azienda o residency dedicata |
| Dedicated facility cell | edge/offline/latency o dispositivo locale |
| Dedicated connector process | third-party/parser/driver ad alto rischio |
| Cross-community gateway | federazione IHE con trust boundary separato |
| Imaging cell | I/O/storage/network ottimizzati e separati |

La scelta deriva da BIA, data classification, throughput, failure correlation, country e contract. Un cluster condiviso senza quota/credential/namespace/fault test non costituisce isolamento.

## 20. Performance envelope e capacity

Ogni scheda riporta sustained/burst, payload distribution, latency, max in-flight, connection, resource, queue recovery e partner assumptions. Capacity sicura considera N+1, hot tenant e live+replay.

Baseline HHC:

- HL7 v2 ≤64 KiB, overhead p95≤50 ms/p99≤100 ms;
- Reference Runtime Cell ≥2.000 eventi/s da 8 KiB per 60 minuti;
- CPU media≤70%, p99 ingest≤250 ms, zero mismatch;
- factor di capacity minimo 1,5;
- DICOM/file grandi misurati per byte/s, object latency e concurrent stream, non EPS soltanto.

## 21. Business continuity e disaster recovery

Ogni connector dichiara:

- state ownership e checkpoint;
- active-active/passive e fencing;
- source reconnect/offset behavior;
- duplicate window e reconciliation;
- secret/certificate/DNS recovery;
- spool/backlog capacity;
- degraded mode e local autonomy;
- DR synthetic test.

Tier 0: 99,99%, RTO≤30 min, RPO cross-site≤5 min, locale 0 dopo ACK durevole. Tier 1: 99,95%, RTO≤2 h/RPO≤15 min. Runtime operative ≥24 ore senza Control Plane. Nessun connector può ridurre silenziosamente questi target.

## 22. Audit e monitoraggio continuo

Catalogo e runtime espongono:

- version/trust/qualification/expiry;
- active instances e config digest;
- connection/session/association;
- message/byte/latency/error;
- ACK/receipt/unknown/reconciliation;
- queue age/backlog/retry/DLQ;
- resource saturation e leak;
- secret/certificate expiry;
- protocol/profile validation;
- partner health e circuit;
- cross-scope deny/security finding.

Ogni connector ha dashboard, SLO, alert, runbook, owner 24×7 per Tier 0 e synthetic probe. Audit registra install, configure, activate, upgrade, rollback, test, revoke e privileged diagnostic.

## 23. Qualification matrix

Prima di `QUALIFIED`:

1. SDK contract e permission test;
2. protocol/profile conformance;
3. partner/product matrix;
4. semantic/golden test;
5. security/fuzz/penetration;
6. performance/load/soak;
7. backpressure/slow partner;
8. crash/lost response/duplicate/reorder;
9. HA/failover/restore/DR;
10. observability/audit/PHI leakage;
11. upgrade/rollback N/N-1;
12. documentation/runbook/support readiness.

Claim e limitazioni sono pubblicati nel Conformance Pack. Connectathon o vendor test è evidenza aggiuntiva, non certificazione dell'intero prodotto.

## 24. Casi d'uso sanitari europei

### CAT-UC-01 — ADT regionale multiazienda

HL7 v2 MLLP riceve ADT da ospedali con namespace paziente sovrapposti.

**Verifiche:** endpoint-derived tenant; assigning authority; ACK durabile; merge/unmerge; hot tenant isolation; replay e audit.

### CAT-UC-02 — Laboratorio HL7→FHIR

ORU viene trasformato in Observation/DiagnosticReport per EHR FHIR.

**Verifiche:** profile, LOINC/UCUM, critical flag, amendment, FHIR conditional/idempotency, OperationOutcome e lineage.

### CAT-UC-03 — Patient Summary transfrontaliero

Country pack pubblica/riceve Patient Summary.

**Verifiche:** IG/version, identity/trust, narrative, terminology/language, consent/purpose, evidence di qualifica e nessun claim oltre scope.

### CAT-UC-04 — Imaging hub multifacility

Modalità DICOM inviano a VNA e viewer usa DICOMweb.

**Verifiche:** AE/SOP/transfer syntax, commitment, streaming, study authorization, UID collision, site failure e cross-facility audit.

### CAT-UC-05 — Scambio documentale IHE

XDS/MHD pubblica CDA e XCA interroga una community remota degradata.

**Verifiche:** actor/transaction/metadata, signature, partial result, circuit, reconciliation e trust domain isolation.

### CAT-UC-06 — Batch SFTP laboratorio esterno

File giornaliero contiene risultati e correzioni.

**Verifiche:** done marker, manifest/hash, schema, duplicate filename, correction semantics, quarantine, receipt e retention.

### CAT-UC-07 — Estrazione legacy per OMOP

JDBC estrae viste autorizzate senza impattare il database clinico.

**Verifiche:** read-only, snapshot/watermark, fetch/timeout, schema drift, checkpoint, source reconciliation e isolamento Tier 2.

### CAT-UC-08 — Broker ospedaliero indisponibile

Il broker target perde leader durante picco.

**Verifiche:** producer confirmation, unknown, circuit, durable backlog, leader recovery, idempotency e nessuna propagazione ad altri endpoint.

### CAT-UC-09 — SIEM offline 24 ore

Collector centrale non raggiungibile.

**Verifiche:** buffer locale tamper-evident, capacity warning, alert alternativo, ordered recovery, gap detection e nessun blocco improprio del Tier 0.

### CAT-UC-10 — Revoca supply-chain

Una CVE critica o firma invalida colpisce una release connector.

**Verifiche:** inventory/SBOM impact, blocco nuove installazioni, quarantine/revoke, compensazione, patch/canary, rollback e customer evidence.

### CAT-UC-11 — Facility offline

Una struttura rurale perde WAN/Control Plane.

**Verifiche:** connector locali, terminology/bundle cache, spool 24h+, degraded alert locale, reconnect rate e reconciliation.

### CAT-UC-12 — Upgrade massivo senza downtime

Una major release connector viene distribuita su centinaia di facility.

**Verifiche:** compatibility/config migration, cohort canary, drain/fencing, N/N-1, SLO, automated rollback e evidence per target.

## 25. Anti-pattern vietati

- catalogare una libreria come connector qualificato;
- claim senza role/profile/options/version;
- un connector “universale” con permessi illimitati;
- tenant da message field;
- SFTP host key auto-accept;
- JDBC write sul clinico;
- object ETag assunto sempre checksum;
- broker exactly-once esteso al partner legacy;
- C-STORE equiparato a storage commitment;
- FHIR core validation equiparata a IG/workflow conformance;
- country logic nel core;
- `latest` in artifact/BOM;
- endpoint lento che esaurisce pool globale.

## 26. Fonti ufficiali e data di verifica

Fonti verificate il **1 settembre 2026**:

- HL7 v2 standard index: <https://hl7.eu/HL7v2x/hl7contents.htm>
- HAPI HL7v2 repository: <https://github.com/hapifhir/hapi-hl7v2>
- HL7 FHIR R5: <https://hl7.org/fhir/R5/>
- SMART App Launch 2.2.0: <https://hl7.org/fhir/smart-app-launch/>
- FHIR Bulk Data 3.0.0: <https://hl7.org/fhir/uv/bulkdata/>
- IHE ITI Technical Framework: <https://profiles.ihe.net/ITI/>
- Open eHealth IPF: <https://github.com/oehf/ipf>
- DICOM PS3.2 2026c: <https://dicom.nema.org/medical/dicom/current/output/html/part02.html>
- dcm4che releases; 5.34.3 e dizionario 2026a: <https://github.com/dcm4che/dcm4che/releases>
- Apache Camel releases; 4.22.0 latest LTS: <https://camel.apache.org/releases/>
- OASIS AMQP 1.0, Part 0 Overview e indice delle parti normative: <https://docs.oasis-open.org/amqp/core/v1.0/amqp-core-overview-v1.0.html>
- OASIS MQTT 5.0: <https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html>
- W3C SOAP 1.2 Part 1: <https://www.w3.org/TR/soap12-part1/>
- Regolamento (UE) 2025/327 EHDS: <https://eur-lex.europa.eu/eli/reg/2025/327/oj/>
- Commissione europea, EHDS: <https://health.ec.europa.eu/ehealth-digital-health-and-care/european-health-data-space-regulation-ehds_en>

Versioni e stato normativo vengono riverificati prima di ogni release. Il BOM e il Conformance Pack, non questo elenco descrittivo, determinano ciò che è installabile e supportato.
