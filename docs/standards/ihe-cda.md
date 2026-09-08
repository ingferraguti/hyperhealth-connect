# IHE e HL7 CDA — document sharing enterprise

Stato: baseline di sviluppo 1.0  
Data di aggiornamento: 1 settembre 2026  
Ultima verifica fonti ufficiali: 1 settembre 2026  
Target: scambio documentale europeo multi-azienda e multi-facility

## 1. Scopo

HHC orchestra pubblicazione, registrazione, ricerca, recupero e lifecycle di documenti clinici CDA e non-CDA secondo profili IHE selezionati. Non sostituisce il repository clinico autorevole, non attribuisce valore legale a un documento e non dichiara conformità IHE senza Integration Statement, test e profilo/versione espliciti.

CDA R2.0 definisce struttura e semantica di documenti persistenti, con stewardship, potenziale autenticazione, contesto, interezza e leggibilità umana. Un XML valido rispetto allo schema CDA non è automaticamente conforme a template/IG nazionale né clinicamente corretto.

## 2. Profili e baseline

| Capability | Profilo | Stato/uso HHC |
|---|---|---|
| Document sharing intra/inter-enterprise | XDS.b | baseline SOAP final-text, per affinity domain |
| Cross-community access | XCA | gateway tra community, policy e timeout dedicati |
| Patient discovery | XCPD | solo con identity governance e reconciliation |
| Patient ID cross-reference | PIX/PIXm | adapter verso MPI, non merge engine implicito |
| Patient demographics query | PDQ/PDQm | query scoped e minimizzata |
| Mobile document sharing | MHD | release esatta: R4 4.2.4 o R5 5.0.0, entrambe Trial Implementation al controllo |
| Security audit | ATNA/BALP | profilo selezionato più audit HHC tamper-evident |
| Time | CT | clock sync monitorato, non prova assoluta del tempo clinico |
| Consent/privacy | BPPC, PCF o profilo nazionale | decisione finale nel PDP/autorità competente |
| Valueset sharing | SVS/SVCM | snapshot e licenza governati |

L'IHE ITI Technical Framework final text verificato è Revision 20.2 del 11 novembre 2025. I supplementi Trial Implementation restano esplicitamente tali. Le pagine CI/comment non sono pin di produzione.

## 3. Affinity domain e federazione

Ogni affinity domain dichiara patient ID domain, assigning authority, document class/type/format, practice setting, confidentiality, facility type, author, repository e homeCommunityId. Mapping dei metadata fra community è versionato; valori sconosciuti non vengono sostituiti con default semanticamente falsi.

La federazione multi-azienda usa gateway separati per trust domain. Tenant e organization non sono derivati da metadata forniti dal chiamante: vengono risolti dall'identità autenticata e dalla configurazione dell'endpoint. Query cross-community applicano fan-out limit, deadline, circuit breaker e risultato parziale esplicito.

## 4. CDA validation pipeline

1. verifica size, media type, encoding e XML hardening;
2. schema CDA R2.0/estensioni SDTC esattamente pin-nate;
3. templateId e template version;
4. Schematron/FHIR StructureDefinition o validator dell'IG;
5. header/document metadata coherence;
6. terminology binding e unità;
7. narrative safety e reference integrity;
8. firma/timestamp quando richiesti;
9. business/privacy rule;
10. derivazione canonical/metadata con lineage.

Il parser XML disabilita DTD, external entity, network resolution e XInclude salvo eccezione controllata. Stylesheet incorporati non sono eseguiti. Narrative XHTML viene sanitizzata per la visualizzazione; l'originale rimane immutabile.

## 5. Integrità e coerenza documento/metadata

Prima della pubblicazione sono confrontati patient ID, uniqueId, repositoryUniqueId, homeCommunityId, classCode, typeCode, formatCode, confidentialityCode, language, serviceStart/StopTime, author e hash/size dove previsti. Disallineamenti critici bloccano la registrazione. Il documento sostituito o ritirato non viene sovrascritto: associazioni e availability status descrivono il lifecycle.

Il CDA raw, i metadata IHE inviati, la receipt e la versione trasformata sono oggetti distinti. `DocumentEntry.uniqueId` non è usato come unico identificatore interno globale senza scope.

## 6. Firma, autenticità e valore probatorio

HHC valida firma, certificate path, revocation policy, algorithm e signing time secondo profilo nazionale/aziendale. Esito `VALID` significa valido secondo trust store e policy alla data della verifica, non attribuzione automatica di validità medico-legale. HHC conserva evidence della verifica, chain reference e policy version; non logga il documento.

Se una trasformazione cambia il CDA, la firma originaria non viene presentata come firma del derivato. Il derivato registra fonte e signature status originario; un'eventuale nuova firma segue ruolo e processo autorizzati.

## 7. Transazioni, idempotenza e reconciliation

Registrazione documento usa identificatori e hash per riconoscere retry. Risposta persa genera stato `UNKNOWN` e query/reconciliation prima della ripetizione. Repository e registry incoerenti attivano finding e workflow; HHC non elimina documenti per correggere automaticamente.

Retrieve usa streaming, checksum e limiti di memoria. Cache è opzionale, tenant-scoped, cifrata e conforme alla retention. URL o endpoint interni non sono accettati dal payload senza allowlist.

## 8. Sicurezza e privacy

- mTLS e trust federation per node/application identity;
- authorization su purpose, ruolo, organization, facility e patient context;
- confidentialityCode è input della policy, non unico controllo;
- audit di query/retrieve/publication e break-glass;
- minimizzazione delle query e response filtering solo se il profilo lo consente;
- consent/restriction da fonte autorevole, con stato/versione;
- no cross-tenant cache, index o correlation;
- ATNA/BALP export verso Audit Record Repository e SIEM, più journal HHC.

## 9. Prestazioni, HA e DR

Metadata e contenuto usano percorsi separati. Query ha pagination, deadline e result limit; retrieve è streaming. Documenti grandi non entrano nel broker inline ma usano object reference opaca e cifrata. Registry/repository adapter sono scalabili orizzontalmente; lo stato è nel ledger durabile.

I flow clinici Tier 0 rispettano SLO 99,99%, RTO ≤30 minuti, RPO locale 0 dopo ACK durabile e cross-site ≤5 minuti. La perdita di una community remota produce risultato parziale esplicito. Le Runtime Cell continuano con bundle firmato per ≥24 ore senza Control Plane. Backup/restore verificano document, metadata, association e audit con checksum e test di recupero.

## 10. Osservabilità

Metriche: transaction count/status, p95/p99, registry/repository divergence, query result count, partial response, retrieve bytes, signature outcome, validation finding, retry/unknown, circuit state, oldest age e cross-community latency. Gli identificativi clinici non sono label metriche. Audit e trace sono correlabili tramite ID opachi.

## 11. Open eHealth IPF

IPF è candidato per componenti IHE/HL7/CDA e offre building block XDS, PIX, PDQ, XCPD. È incapsulato dietro adapter HHC. La release è pin-nata con SBOM/hash/licenza, matrice transaction/profile, interoperability test e CVE review. Il supporto di un building block non è prova di conformance dell'implementazione completa.

## 12. Casi d'uso europei 2026

### UC-IHE-01 — lettera di dimissione intra-regione

La facility pubblica un CDA firmato in XDS.b. HHC valida CDA/template, coerenza metadata, identità e firma, rende durabile richiesta/receipt e inoltra. Documento non valido è quarantinato senza registrazione parziale. Accettazione: query/retrieve restituiscono versione corretta, replacement è tracciato e audit completo.

### UC-IHE-02 — patient summary cross-community

Un pronto soccorso interroga XCPD/XCA. HHC applica purpose e deadline, segnala community non raggiungibili e non fonde pazienti ambigui. Accettazione: risultato parziale chiaramente marcato, provenance/homeCommunity preservati e break-glass auditato.

### UC-IHE-03 — accesso mobile MHD

Il client FHIR usa la release MHD fissata. HHC traduce metadata solo con mapping approvato e mantiene equivalenza verificata con XDS. Accettazione: nessuna dipendenza CI/floating, CapabilityStatement effettivo e test Connectathon/conformance conservati come evidence.

### UC-IHE-04 — documento ritirato o sostituito

Un referto viene corretto. Il nuovo documento è pubblicato con association appropriata; il precedente resta secondo repository policy e non è mostrato come corrente. Accettazione: nessun overwrite, chain ricostruibile e consumer notificati secondo contratto.

## 13. Release gate

- schema/template/Schematron e terminology snapshot pin-nati;
- test positivi, negativi, signature e metadata mismatch;
- transaction contract test per ogni actor;
- idempotency, lost response e reconciliation;
- query limit/fan-out/partial-result;
- security, XML attack e cross-tenant isolation;
- throughput, large document, soak e dependency outage;
- active/standby o active/active failover, backup restore e cyber recovery;
- Integration Statement aggiornato solo per capability provate.

## 14. Fonti ufficiali verificate

- [HL7 CDA — portale ufficiale e CDA R2.0 normativo](https://hl7.org/cda/), consultato il 1 settembre 2026.
- [HL7 CDA Core 2.0 — repository e release](https://github.com/HL7/CDA-core-2.0/releases), consultato il 1 settembre 2026; verificata Edition 2024 e release schema SDTC 5 febbraio 2026.
- [IHE ITI Technical Framework Volume 1, Rev. 20.2 Final Text](https://profiles.ihe.net/ITI/TF/Volume1/index.html), consultato il 1 settembre 2026.
- [IHE XDS.b](https://profiles.ihe.net/ITI/TF/Volume1/ch-10.html) e [pubblicazioni ITI](https://profiles.ihe.net/ITI/), consultate il 1 settembre 2026.
- [Open eHealth IPF — repository ufficiale](https://github.com/oehf/ipf), consultato il 1 settembre 2026; Apache-2.0 e capability dichiarate dal progetto verificate.

Il team deve verificare licenze HL7/IHE e condizioni nazionali per template, terminologie, firme e trust list.
