# DICOM e DICOMweb — profilo enterprise HHC

Stato: baseline di sviluppo 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Target: imaging multi-azienda e multi-facility ad alte prestazioni

## 1. Scopo e confine

HHC integra DICOM DIMSE, DICOMweb e i workflow con RIS/PACS/VNA, ma non è il PACS/VNA autorevole salvo deployment e conformance statement specifici. Il pixel data non viene trasformato per default. Ogni device/AE/API possiede DICOM Conformance Statement, transfer syntax, SOP Class, security e capacity dichiarati.

La pagina DICOM “current” verificata espone l'edizione 2026c; poiché lo standard è aggiornato più volte l'anno, ogni release HHC fissa un'edizione concreta. Il link `current` è solo fonte di monitoraggio e non una dipendenza di build.

## 2. Parti normative rilevanti

| Parte | Uso HHC |
|---|---|
| PS3.2 | conformance e dichiarazioni per actor/device |
| PS3.3 | Information Object Definitions |
| PS3.4 | Service Class Specifications |
| PS3.5 | data structure, encoding e transfer syntax |
| PS3.6 | data dictionary pinned |
| PS3.7/3.8 | DIMSE e network communication |
| PS3.10 | file format/media interchange |
| PS3.15 | security, audit e application configuration |
| PS3.16 | coded terminology/content mapping |
| PS3.18 | DICOMweb REST services |
| PS3.20 | imaging report con CDA, quando applicabile |
| PS3.21 | trasformazioni fra DICOM e altre rappresentazioni |

Supplement e Correction Proposal diventano operativi solo quando inclusi nell'edizione pin-nata o adottati formalmente con test e deviation documentata.

## 3. Capability

### 3.1 DIMSE

Capability candidate: C-ECHO, C-STORE, C-FIND, C-MOVE/C-GET, Storage Commitment, Modality Worklist e MPPS secondo necessità. Ogni association negozia application context, presentation context, SOP Class e transfer syntax. HHC rifiuta ciò che non è nel ConformancePack; non decomprime/transcodifica silenziosamente.

### 3.2 DICOMweb

PS3.18 definisce servizi web. HHC può supportare QIDO-RS, WADO-RS, STOW-RS, UPS-RS o altre transaction solo se dichiarate. Content negotiation, media type, multipart boundary, range/streaming e status report sono testati. WADO-URI legacy è isolato se necessario.

### 3.3 Workflow

Correlation usa Patient ID con issuer, Accession Number con issuer, Study/Series/SOP Instance UID e order identifiers. Nessun singolo campo è considerato identità globale. HL7 order/ADT e DICOM workflow sono riconciliati tramite regole versionate; mismatch non causa patient reassignment automatico.

## 4. ConformancePack per AE/API

```yaml
id: dicom/<organization>/<facility>/<device>
version: semver
dicomEdition: 2026c
role: SCU|SCP|BOTH
aeTitle: configured-secretless-value
sopClasses:
  - uid: "..."
    role: SCU|SCP
    transferSyntaxes: [uid]
dicomweb:
  services: [QIDO-RS, WADO-RS, STOW-RS]
  mediaTypes: [application/dicom, application/dicom+json]
maxObjectBytes: integer
maxAssociationPdu: integer
timeouts: {associationMs: n, dimseMs: n, idleMs: n}
tlsProfile: immutable-reference
deidentificationProfile: optional-reference
serviceTier: TIER_0|TIER_1|TIER_2
```

AE Title non è un'autenticazione. L'identità deriva da certificato/workload/network binding e configurazione approvata.

## 5. Ingest e integrità

Gli oggetti sono ricevuti in streaming verso object storage/spool, con size limit, checksum e encryption context. Prima di un C-STORE success o risposta STOW positiva si raggiunge il durability point del contratto. Il ledger registra SOP Instance UID, checksum, byte, source AE/API, transfer syntax e status, senza usare Patient Name come indice operativo.

Stesso SOP Instance UID e checksum uguale è retry idempotente; checksum diverso è collisione critica e quarantena. Storage Commitment è distinto dal semplice C-STORE success e va implementato secondo il profilo della destinazione.

## 6. Transfer syntax e pixel data

- il passthrough preserva transfer syntax e byte;
- transcodifica è servizio esplicito, asincrono quando possibile, con source/target, codec/versione e quality policy;
- lossy conversion non è mai default e richiede autorizzazione clinica;
- decompression bomb, malformed encapsulation e codec crash sono isolati in sandbox con resource limit;
- burned-in annotation e overlay sono considerati rischio privacy;
- private tag sono preservati nel raw ma esposti/mappati solo tramite dictionary governato;
- rendering diagnostico richiede validation separata e non è implicato dalla sola capacità di retrieve.

## 7. De-identification

La de-identificazione segue un profilo dichiarato basato su PS3.15 e requisiti di studio/autorità. Rimuovere Patient Name non basta: UID, date, private tag, pixel data, SR, waveform e metadata possono identificare. Il job produce manifest delle action per tag, burned-in review, UID remap scope, date shifting, linkage key custody e residual-risk assessment.

Pseudonimizzazione resta trattamento di dati personali. Re-identification key è separata dal dataset e assente dal secure processing environment. Output non viene etichettato “anonimo” senza valutazione documentata.

## 8. Sicurezza

- DICOM TLS/mTLS o reverse proxy/gateway protetto;
- OAuth/OIDC per DICOMweb, audience e scope minimi;
- allowlist di AE/API e association rate limit;
- no SSRF tramite retrieve URL o bulkdata URI;
- media parser sandbox e antivirus dove compatibile;
- encryption at rest e key separation per tenant;
- audit compatibile con DICOM PS3.15/IHE ATNA, più audit HHC;
- accesso pixel/metadata soggetto a purpose, facility e patient context;
- diagnostica/PCAP solo break-glass, TTL breve e audit.

## 9. Prestazioni e capacity

Imaging richiede capacity distinta dagli eventi piccoli. Si dimensionano separatamente:

- association/s e concurrent association;
- object/s e byte/s per modality e transfer syntax;
- p95/p99 time-to-durable e time-to-forward;
- throughput di transcodifica per codec/CPU/GPU;
- object store IOPS/bandwidth e multipart behavior;
- WAN bandwidth, compression e scheduled prefetch;
- queue oldest age, storage headroom e restore throughput.

Il pixel payload non passa inline nel broker: il broker porta envelope e reference opaca. Back-pressure è per modality/destination/tenant. Quote riservate proteggono emergenza e modality critiche dai bulk migration.

## 10. HA, business continuity e DR

Listener/API sono distribuiti su Runtime Cell; state e object sono durabili. Per Tier 0 valgono SLO 99,99%, RTO ≤30 minuti, RPO locale 0 dopo success durabile e RPO cross-site ≤5 minuti. La facility mantiene ≥24 ore di autonomia dal Control Plane e store-and-forward locale dimensionato sul worst credible outage.

Il DR prova non solo metadata ma oggetti, checksum, manifest, UID uniqueness, lifecycle e audit. Failover DNS/load balancer, certificati, AE routing e firewall sono inclusi. Il failback riconcilia study/series/instance e non reinvia alla cieca. Cyber recovery usa copie immutabili, clean room e verifica codec/parser prima del ripristino.

## 11. Audit e monitoraggio

Metriche: association accept/reject, presentation context failure, DIMSE status, HTTP status, byte/object rate, p95/p99, collisioni UID, storage commitment pending, QIDO result/latency, WADO/STOW bytes, queue age, codec failure, de-id finding, storage capacity e replication lag. Patient ID/UID ad alta cardinalità non sono label; l'indagine usa lookup autorizzato tramite envelope ID.

Alert critici: impossibilità di rendere durabile, collisione UID con contenuto diverso, backlog oltre clinical deadline, spazio sotto safety margin, replica oltre RPO, checksum mismatch, cross-tenant access, audit gap e certificate expiry.

## 12. dcm4che

dcm4che è toolkit Java candidato. Al controllo il repository richiede Java 17+ e include core, network, audit, de-identification, JSON, image/codec e utility DICOMweb. La release 5.34.3, pubblicata il 23 aprile 2026, dichiara data dictionary DICOM 2026a: ciò non rende automaticamente l'intero stack conforme a 2026c. HHC pin-na release, native codec/architettura, hash, SBOM, licenza MPL 1.1, CVE e test per SOP/transfer syntax.

## 13. Casi d'uso europei 2026

### UC-DICOM-01 — store multi-facility

Modalità di più facility inviano al VNA enterprise. HHC autentica il device, rende durabile, valida UID/transfer syntax e inoltra con quote isolate. Accettazione: nessun cross-tenant, collisione rilevata, receipt riconciliata e perdita zero dopo success.

### UC-DICOM-02 — viewer DICOMweb

Il viewer autorizzato esegue QIDO/WADO. HHC applica scope/purpose, pagination, streaming e audit. Una facility remota lenta produce partial/error esplicito. Accettazione: nessun data oracle, content negotiation corretta e p99 entro SLO dichiarato.

### UC-DICOM-03 — studio incompleto dopo outage

Una rete cade durante una serie. Al ripristino HHC riconcilia SOP Instance UID/checksum e richiede solo gli oggetti mancanti. Accettazione: study completeness misurabile, nessuna duplicazione e backlog recovery rate-limited.

### UC-DICOM-04 — dataset imaging per ricerca

Un permit autorizza immagini selezionate. Pipeline isolata applica de-id, pixel review e UID remap, pubblica in secure environment e conserva lineage. Accettazione: profilo riproducibile, residual risk approvato e chiave fuori dall'ambiente.

## 14. Release gate

- Conformance Statement per actor/AE/API;
- test SOP Class/transfer syntax e negotiation negativa;
- UID collision, retry, lost response e Storage Commitment;
- malformed object, codec sandbox e decompression-bomb;
- DICOMweb content negotiation, multipart, large object e range;
- de-id tag/pixel/private-tag e re-identification controls;
- performance burst/soak, bulk migration e noisy neighbor;
- failover/DR/restore con checksum e completezza study;
- audit, redaction, certificate rotation e dependency outage.

## 15. Fonti ufficiali verificate

- [DICOM Standard — Current Edition](https://www.dicomstandard.org/current/), consultata il 1 settembre 2026; la pagina dichiara aggiornamenti multipli annui.
- [DICOM PS3.18 2026c — Web Services](https://dicom.nema.org/medical/dicom/current/output/chtml/part18/ps3.18.html), consultata il 1 settembre 2026.
- [DICOM PS3.1 2026c — ruolo di PS3.18/DICOMweb](https://dicom.nema.org/medical/dicom/current/output/chtml/part01/sect_6.18.html), consultata il 1 settembre 2026.
- [dcm4che — repository ufficiale](https://github.com/dcm4che/dcm4che) e [release](https://github.com/dcm4che/dcm4che/releases), consultati il 1 settembre 2026; verificata release 5.34.3 e dipendenza dictionary 2026a.

DICOM e DICOMweb sono pubblicazioni NEMA soggette ai relativi termini. Codec, dictionary privati e componenti nativi richiedono license e patent review separata.
