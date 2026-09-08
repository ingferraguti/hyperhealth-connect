# HL7 Version 2 — profilo di integrazione HHC

Stato: baseline di sviluppo 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica delle fonti ufficiali: 1 settembre 2026  
Target: produzione enterprise multi-azienda e multi-facility nella sanità europea

## 1. Scopo e decisione architetturale

Questo documento definisce come HHC riceve, valida, trasforma, inoltra, osserva e riconcilia messaggi HL7 v2.x. HL7 v2 resta un protocollo operativo primario per ADT, ordini, risultati, farmacia e workflow dipartimentali; non è sostituito automaticamente da FHIR e non viene convertito implicitamente a una versione più recente.

La baseline accetta interfacce dichiarate dalla v2.3 alla v2.9 quando esistono profilo, parser e test compatibili. La libreria HAPI HL7v2 verificata espone model package fino alla v2.8.1: il supporto v2.9 richiede modello/profilo dedicato, generazione controllata o parser alternativo, senza dichiarare una capacità non presente nella dipendenza. Versioni precedenti o varianti vendor sono ammesse solo come eccezione inventariata.

Principi vincolanti:

- il raw wire payload è immutabile e resta l'evidenza di ciò che HHC ha ricevuto;
- `MSH-12`, message structure, trigger event e profilo di conformance selezionano il contratto;
- parser tollerante non significa validazione tollerante;
- ACK applicativo e ACK di trasporto hanno semantica esplicita per flow;
- consegna at-least-once, idempotenza e riconciliazione sostituiscono promesse generiche di exactly-once;
- l'ordine è garantito soltanto entro la chiave dichiarata, tipicamente paziente/episodio o ordine;
- nessun errore HL7 finisce in log o metrica con dati sanitari non redatti.

## 2. Ambito supportato

| Dominio | Trigger tipici | Priorità | Regola |
|---|---|---:|---|
| Patient administration | `ADT` A01/A02/A03/A04/A08/A11/A12/A13/A40 | Tier 0/1 | merge A40 soggetto a MPI policy; mai per sola somiglianza |
| Order entry | `ORM`, `OML`, `OMG` | Tier 0/1 | placer/filler ID preservati con assigning authority |
| Results | `ORU`, `OUL` | Tier 0/1 | stato, unità, range e correzioni preservati |
| Scheduling | `SIU` | Tier 1 | profilo locale per risorse e timezone |
| Pharmacy | `RDE`, `RDS`, `RAS` | Tier 0/1 | vietato inferire prodotto o dose |
| Financial/claims | `DFT`, `BAR` | Tier 2 | isolamento dai flow clinici critici |
| Query | `QRY`, `QBP` e risposte | variabile | timeout, pagination e correlazione nel profilo |
| Master files | `MFN`, `MFK` | Tier 1/2 | release controllata e riconciliazione |
| Clinical documents | `MDM` | Tier 1 | payload/reference e metadata governati |

Questa è una capability map, non una dichiarazione che ogni trigger sia implementato. Ogni combinazione versione/evento/struttura deve essere elencata nel `ConformancePack` del tenant.

## 3. Contratto di interfaccia

Ogni endpoint deve avere un contratto immutabile e versionato:

```yaml
contractId: hl7v2/<organization>/<facility>/<interface>
contractVersion: semver
hl7Version: "2.5.1"
messageTypes:
  - messageCode: ADT
    triggerEvents: [A01, A03, A08]
    structures: [ADT_A01, ADT_A03]
transport: MLLP_TLS
charsetPolicy: declared-and-validated
ackMode: ORIGINAL|ENHANCED
ackPoint: DURABLE|VALIDATED|DELIVERED
orderingKey: patient-local-id|visit-id|order-id
duplicateWindow: duration
maxMessageBytes: integer
profileArtifact: immutable-reference
mappingRelease: immutable-reference
serviceTier: TIER_0|TIER_1|TIER_2
timeouts: {connectMs: n, responseMs: n, idleMs: n}
retryPolicy: immutable-reference
retentionPolicy: immutable-reference
owner: team-id
```

Il contratto comprende esempi sintetici positivi e negativi, expected ACK/NACK, charset, escape, timezone, identificativi, code table, segmenti Z, retry, downtime procedure, SLO, capacity e contatti operativi.

## 4. Trasporto, sicurezza e framing

MLLP è un adapter isolato. Start block, payload, end block e carriage return sono validati con limiti di lunghezza e timeout; connessioni incomplete non possono consumare memoria senza limite. TLS/mTLS è obbligatorio dove tecnicamente possibile. Per sistemi legacy non-TLS si usa un gateway di zona protetta con rischio accettato, allowlist, segmentazione, monitoraggio e piano di dismissione.

Controlli minimi:

- autenticazione tramite certificato, workload identity o network binding;
- cipher/protocolli conformi alla crypto baseline aziendale;
- limiti per connessione, IP, endpoint, facility e tenant;
- protezione da slow client, frame oversized e connection storm;
- nessuna fiducia basata soltanto su `MSH-3`/`MSH-4`;
- segreti e chiavi in vault/HSM, mai nella configurazione del flow;
- PCAP disabilitato per default; cattura diagnostica time-bound, cifrata e auditata.

## 5. Parsing e normalizzazione

Il decoder preserva i byte originali e produce una rappresentazione parsed distinta. Deve gestire delimitatori in `MSH`, escaping HL7, repetitions, component/subcomponent, null esplicito, trailing empty field e batch quando previsto.

1. Determinare charset da contratto e `MSH-18`; il fallback è dichiarato, mai silenzioso.
2. Conservare valore raw e decoded quando una trasformazione può perdere informazione.
3. Non correggere automaticamente segment order, date invalide o escape rotti nel raw.
4. Normalizzazioni autorizzate creano un derivato con regola/versione.
5. Segmenti Z sono namespaced per organization/facility/vendor e descritti in schema.
6. Campi sconosciuti non sono scartati senza disposition esplicita.
7. Tempi incompleti conservano precisione e offset noto; la timezone non viene inventata.

## 6. Validazione

| Livello | Esempio | Fallimento predefinito |
|---|---|---|
| Framing | MLLP incompleto, size eccessiva | reject trasporto; nessun ACK positivo |
| Syntax | segmento/campo non parsabile | application reject o quarantine |
| Structure | evento incompatibile con message structure | reject/quarantine |
| Profile | cardinalità, usage, length, table binding | severity del profilo |
| Business | facility, order, patient class, destination | route block o quarantine |
| Semantic | code system, unità, mapping | disposition distinta per clinica e analytics |
| Privacy/security | endpoint, purpose, classification | deny e security audit |

Ogni finding contiene rule ID, versione, posizione ER7, severity, disposition e dettaglio redatto. `WARN` non diventa implicitamente `PASS`; alimenta i quality KPI.

## 7. ACK, commit e stato incerto

HHC supporta original ed enhanced acknowledgment mode quando previsti. `MSA-1`, `MSA-2`, `ERR` e correlation devono riferirsi al messaggio effettivo; il diagnostic è minimizzato.

| `ackPoint` | Condizione | Vincolo |
|---|---|---|
| `DURABLE` | raw, envelope e ledger durabili localmente | default Tier 0/1; replica cross-site può essere pending |
| `VALIDATED` | controlli contrattuali richiesti completati | entro deadline del sender |
| `DELIVERED` | receipt della destinazione obbligatoria | solo bridge sincrono approvato |

Un timeout dopo invio produce `DELIVERY_UNKNOWN`, non `FAILED`. HHC riconcilia con control ID/receipt/read-back prima di ripetere quando un duplicato avrebbe impatto clinico. Un ACK positivo non prova l'uso clinico del dato.

## 8. Duplicati e ordering

Fingerprint primaria:

```text
tenant + sourceEndpoint + MSH-10 + messageType + trigger + payloadChecksum
```

`MSH-10` da solo non è globalmente univoco. Reuse, collisioni e stesso control ID con checksum diverso generano alert e quarantena. La dedup window dipende dal flow; il ledger conserva l'esito abbastanza a lungo da consentire riconciliazione.

L'ordering key è esplicita. Per ADT usa episodio/paziente entro assigning authority; se assente, il contratto definisce la degradazione. Gli eventi fuori ordine sono marcati, bufferizzati entro un limite o inoltrati con warning secondo rischio: mai riordinati illimitatamente.

## 9. Mapping e semantica

Il mapping HL7 v2 → Canonical Semantic Event conserva:

- field path e valore sorgente;
- assigning authority e identifier type;
- code, text, coding system e version;
- status e relazione di correzione;
- unità e precisione numerica;
- effective, authored, received e processing time;
- parser/profile/mapping/terminology release;
- warning, perdita nota e confidence.

Il mapping non deduce diagnosi, unità o farmaco senza elementi sufficienti. Le proiezioni FHIR e OMOP sono derivati indipendenti e non modificano l'esito della consegna HL7 v2.

## 10. Prestazioni, HA e continuità

Per payload fino a 64 KiB, il riferimento è overhead HHC fast-path p95 ≤50 ms e p99 ≤100 ms nelle condizioni dichiarate, esclusa la latenza esterna. Il benchmark di Runtime Cell è ≥2.000 eventi asincroni/s da 8 KiB per 60 minuti, CPU media ≤70%, p99 ingest ≤250 ms e zero mismatch. Ogni flow è misurato con payload, mapping e destinazioni reali.

La Runtime Cell è failure/scaling domain. I listener sono ridondati, ma una sessione MLLP interrotta richiede riconnessione del sender; la deduplicazione gestisce il reinvio. Per Tier 0: SLO 99,99%, RTO ≤30 minuti, RPO locale 0 dopo ACK durabile, RPO cross-site ≤5 minuti. La cella opera almeno 24 ore con ultima configurazione firmata durante perdita del Control Plane.

Back-pressure è per destinazione e tenant. Un LIS lento non blocca ADT di un'altra facility. Queue depth, oldest age, retry, NACK, collisioni e percentili ACK sono capacity signal obbligatori.

## 11. Audit e monitoraggio continuo

Metriche senza PHI:

- ingress/egress rate per flow e stato;
- p50/p95/p99 receive→durable, validation e delivery;
- ACK AA/CA/AE/CE/AR/CR per contratto;
- finding per rule ID;
- duplicate/collision/out-of-order;
- queue depth, oldest age, DLQ e replay;
- connessioni, TLS, timeout e circuit breaker;
- divergenza fra input, output e receipt.

Trace e log usano message ID opaco. Accesso al raw, replay, override, modifica mapping e diagnostica entrano nell'audit append-only/tamper-evident e nel SIEM. Dashboard locali restano disponibili durante isolamento del sito.

## 12. Casi d'uso europei 2026

### UC-HL7-01 — ADT multi-facility

Un HIS di Facility A emette A01/A08/A03 verso EHR, LIS e RIS. HHC autentica endpoint, assegna scope, rende durabile prima dell'AA, preserva ordering per episodio e isola i consumer. A40 senza autorità coerenti passa a revisione MPI. Accettazione: zero eventi persi, nessun cross-tenant, receipt per destinazione, replay idempotente e failover entro SLO.

### UC-HL7-02 — ORU con correzione e valore critico

Il LIS invia risultato preliminare, finale e corretto. HHC preserva `OBR`/`OBX` status, collega la correzione, inoltra il percorso clinico prioritario e proietta asincronamente. Un'unità non mappata non viene inventata. Accettazione: stato corretto, lineage completo e analytics mai nel percorso ACK.

### UC-HL7-03 — destinazione indisponibile

Il ricevente è indisponibile per quattro ore. Circuit breaker e store-and-forward mantengono gli eventi, oldest age genera escalation e il recovery è rate-limited. Accettazione: nessuna tempesta di retry, nessuna perdita, quantità inviate/accettate riconciliate.

### UC-HL7-04 — perdita WAN/Control Plane

La facility continua con bundle firmato e spool locale HA. Al ritorno replica raw/ledger e riprende delivery verificando dedup. Accettazione: autonomia ≥24 ore, RPO locale 0 dopo ACK, nessuna configurazione non approvata.

## 13. Test e release gate

- golden message per versione/trigger/profile, inclusi escape, charset e segmenti Z;
- negative suite per framing, malformed, oversized e code table;
- ACK contract test e fault injection in ogni commit point;
- duplicate, collision, reorder e lost-response;
- interoperability test e conformance profile;
- performance soak/burst e noisy-neighbor multi-tenant;
- failover listener/broker/storage, restore e reconciliation;
- TLS, identity spoofing, log redaction e break-glass;
- regression diff byte/field/semantic con approvazione clinico-informatica.

Il rilascio è bloccato da perdita non dichiarata, mapping ambiguo critico, ACK prima del durability point, PHI in telemetria, assenza di rollback o test DR fallito.

## 14. Dipendenze

HAPI HL7v2 è dipendenza candidata, non contratto architetturale. La versione è fissata nel manifest con hash/SBOM, CVE e license review, test e matrice strutture. Parser/model package sono dietro SPI HHC per consentire sostituzione e profili custom.

## 15. Fonti ufficiali verificate

- [HL7 Europe — HL7 v2.9, ANSI/HL7 V2.9-2019](https://hl7.eu/HL7v2x/v29/std29/hl7.html), consultata il 1 settembre 2026.
- [HL7 Europe — indice HL7 v2.x](https://hl7.eu/HL7v2x/hl7contents.htm), consultato il 1 settembre 2026; i ballot non sono trattati come release finali.
- [HAPI HL7v2 — repository ufficiale](https://github.com/hapifhir/hapi-hl7v2), consultato il 1 settembre 2026; verificati model package dalla v2.1 alla v2.8.1.
- [HAPI HL7v2 — release](https://github.com/hapifhir/hapi-hl7v2/releases), consultate il 1 settembre 2026; la versione applicativa va fissata nel manifest.

Le specifiche HL7 sono soggette ai relativi termini. Il team verifica la licenza degli artifact distribuiti e non incorpora materiale normativo protetto senza autorizzazione.
