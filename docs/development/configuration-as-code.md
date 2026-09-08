# Configuration as Code

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Platform Engineering |
| Approvatori | Architecture, Security, SRE, Clinical Informatics |

## 1. Decisione e obiettivo

La configurazione logica di HHC è codice dichiarativo, immutabile per versione, validabile, firmabile, confrontabile e promuovibile. La UI è un editor governato della stessa rappresentazione; il database operativo non è l'unica copia e non introduce uno stato funzionale impossibile da esportare.

Questa policy applica [ADR-007](../adr/ADR-007-configuration-as-code.md), consente ricostruzione e disaster recovery e impedisce divergenze non rilevate tra aziende, facility e ambienti.

## 2. Perimetro degli artifact

Sono configuration-as-code:

- `IntegrationFlow`, inclusi source, pipeline, route, destination e completion policy;
- `ConnectorDefinition` e binding degli endpoint, esclusi i valori segreti;
- riferimenti versionati a mapping, terminologie, contratti e profili;
- policy di retry, timeout, rate limit, idempotenza, retention e privacy;
- deployment bundle, placement, capacità richiesta e feature flag;
- dashboard, alert e SLO quando supportati dal relativo backend;
- policy di accesso dichiarative e ruoli applicativi, senza duplicare la sorgente autoritativa dell'IdP;
- manifest di replay, dataset OMOP e job analitici.

Le credenziali, le chiavi private e i payload clinici non devono comparire negli artifact.

## 3. Identità e struttura

Ogni artifact contiene almeno:

| Campo | Vincolo |
|---|---|
| `apiVersion` e `kind` | schema e semantica deterministici |
| `metadata.id` | UUID stabile, non riutilizzabile |
| `metadata.name` | nome leggibile, univoco nello scope |
| `tenantId`, `organizationId`, `facilityId` | scope esplicito; nessun default globale implicito |
| `version` | versione immutabile e monotona o SemVer secondo il tipo |
| `spec` | configurazione funzionale tipizzata |
| `references` | ID e versione o digest degli artifact dipendenti |
| `compatibility` | runtime, SDK e schema minimi e massimi supportati |
| `provenance` | autore, revisori, ticket, commit e timestamp |
| `contentDigest` | hash del contenuto canonicalizzato |

Il formato di scambio è YAML o JSON conforme a JSON Schema. Il registry conserva una rappresentazione canonicalizzata per firma e digest; differenze di ordinamento o commenti non cambiano l'identità del contenuto.

Esempio informativo:

```yaml
apiVersion: hhc.io/v1
kind: IntegrationFlow
metadata:
  id: 8e57b6fd-40f5-47a3-b452-4af6f1237d17
  name: lis-results-to-ehr
  tenantId: tenant-a
  organizationId: hospital-group-a
  facilityId: hospital-a
  version: 17
spec:
  sourceRef: endpoint/lis-prod@sha256:...
  contractRef: hl7v2/oru-r01@3
  pipeline:
    - mapRef: mapping/lis-observation@8
    - semanticMapRef: semantic/laboratory@5
  destinations:
    - endpointRef: endpoint/ehr-prod@sha256:...
  reliabilityPolicyRef: policy/tier0-clinical@4
```

## 4. Separazione tra definizione e binding

La definizione descrive intenzione e comportamento. Il binding associa risorse ambientali: DNS, queue, bucket, database, certificati, secret reference e limiti. Gli overlay possono modificare soltanto campi esplicitamente classificati come ambientali.

È vietato copiare e divergere l'intero flow per `dev`, `test` e `prod`. Le differenze funzionali richiedono una nuova versione del flow; le differenze ambientali richiedono un binding. Tenant e facility non sono variabili sostituite tramite stringhe non tipizzate.

## 5. Ciclo di vita

```text
DRAFT → VALIDATED → IN_REVIEW → APPROVED → SIGNED → STAGED
      → DEPLOYING → ACTIVE → SUPERSEDED → RETIRED
```

- `DRAFT`: modificabile, non distribuibile;
- `VALIDATED`: supera le verifiche automatiche ma non implica approvazione;
- `APPROVED`: approvazioni registrate e segregazione dei compiti rispettata;
- `SIGNED`: contenuto e dipendenze bloccati da digest;
- `STAGED`: provato sull'ambiente pre-produzione rappresentativo;
- `ACTIVE`: versione effettiva per uno scope dichiarato;
- `SUPERSEDED`: sostituita ma disponibile per audit e rollback;
- `RETIRED`: non più distribuibile; retention secondo policy.

Una nuova firma è obbligatoria dopo qualunque modifica semantica o variazione delle dipendenze.

## 6. Validazione multilivello

Il comando di validazione e il Control Plane applicano gli stessi controlli:

1. sintassi, schema, tipi, enum, limiti e campi sconosciuti;
2. risoluzione referenziale di ID, versione, digest e scope tenant/facility;
3. compatibilità di Connector SDK, runtime, contract, mapping e canonical model;
4. semantica del grafo: nodi raggiungibili, cicli ammessi, destinazioni e completion policy;
5. sicurezza: secret solo per riferimento, protocolli e cipher consentiti, autorizzazioni e data classification;
6. privacy: minimizzazione, log policy, residenza, retention e scopo del trattamento;
7. affidabilità: timeout complessivo, retry budget, idempotency key, ordering, DLQ e backpressure;
8. capacità: rate, payload massimo, concurrency, memoria, spool e quota;
9. conformance sanitaria e mapping golden test pertinenti;
10. policy enterprise tramite policy-as-code con motivazione leggibile e ID controllo.

Warning e waiver non equivalgono: un controllo bloccante può essere derogato solo tramite eccezione firmata, limitata nel tempo e collegata al rischio.

## 7. Repository registry e immutabilità

Git conserva la storia collaborativa e le review; il Configuration Registry conserva artifact approvati, firme, dipendenze e deployment status. Il bundle distribuito contiene l'intero closure set delle dipendenze e un manifest firmato. Tag Git, versione di registry e digest sono correlati ma non intercambiabili.

Un artifact pubblicato non viene sovrascritto. Le correzioni producono una nuova versione. Il garbage collection rispetta retention, legal hold, release supportate e riferimenti da replay o audit.

## 8. Segreti e materiale crittografico

Gli artifact contengono URI opachi verso vault, KMS o PKI e requisiti di utilizzo, mai il valore. La risoluzione avviene nel Data Plane autorizzato, con identity workload e privilege minimo. Export, diff, log ed errori devono redigere anche valori accidentalmente inseriti.

La rotazione può sostituire la versione del secret senza modificare la logica del flow quando il contratto lo consente. Certificati, trust bundle e scadenze hanno owner, alert e procedura di rollback.

## 9. Review e segregazione dei compiti

Le modifiche passano da pull request o workflow equivalente. Sono obbligatori:

- owner tecnico del dominio;
- reviewer del sistema sanitario interessato per mapping e contratti;
- Security e Privacy per nuovi boundary o nuove classi dati;
- SRE per Tier 0, capacità, HA o comportamento di failure;
- doppia approvazione indipendente per produzione critica e break-glass.

Un agente di coding può proporre il diff e le prove, ma non può approvare la propria modifica, modificare gli oracle per far passare il test o promuovere autonomamente in produzione; si applica la [policy agentic](../testing/agentic-coding-quality-policy.md).

## 10. Promozione canary e rollback

La promozione riutilizza lo stesso digest tra ambienti e cambia soltanto binding approvati. Prima dell'attivazione il worker verifica firma, compatibilità, dipendenze, quota e disponibilità del last-known-good.

Per flow critici si usa shadow, canary o routing percentuale solo se la semantica clinica lo permette. Gli ACK e gli effetti esterni non possono essere duplicati a scopo di canary. Il rollback riattiva un bundle già firmato; non ricostruisce una versione storica da dipendenze mobili.

Una migrazione dati non reversibile richiede expand/contract, backup verificato e piano di roll-forward: il rollback applicativo non deve fingere di annullarla.

## 11. Autonomia e disaster recovery

Ogni Runtime Cell conserva localmente l'ultima configurazione valida, le firme e le dipendenze necessarie per almeno 24 ore di autonomia del Control Plane. Durante la disconnessione:

- i flow attivi continuano entro limiti e lease previsti;
- nessuna nuova configurazione non verificata viene accettata;
- stato e audit vengono accodati localmente;
- al rientro si riconciliano digest, deployment generation e risultati.

Backup e restore devono ricostruire repository, registry, chiavi e firme secondo separazione dei ruoli e binding. La prova DR verifica che il runtime riparta con la versione attesa, non soltanto che i file esistano.

## 12. Drift ed emergenza

Il reconciler confronta desired, staged e observed state. Drift manuale, risorsa mancante o digest differente genera evento audit e alert; su risorse critiche può bloccare la promozione o ripristinare lo stato desiderato.

Una modifica di emergenza richiede ticket incidente, durata, approvatore, audit e successiva riconciliazione nel repository. Le modifiche dirette non registrate sono incidenti di configurazione.

## 13. Compatibilità ed evoluzione

Il runtime supporta configurazioni N e N-1 secondo [ADR-010](../adr/ADR-010-versioning-and-compatibility.md). Le conversioni di schema sono pure, testate e preservano il documento originale. La rimozione di un campo segue deprecazione, telemetria d'uso, finestra comunicata e migration report.

## 14. Audit ed evidence

Per ogni change e deployment si conservano: richiedente, approvatori, diff semantico, test, waiver, commit, artifact e digest, firma, target, orario, esito per worker, rollout, rollback e correlation ID. L'audit è append-only e separato dalla telemetria ordinaria.

Un evidence bundle di release consente di dimostrare che la configurazione attiva corrisponde a quella revisionata e testata.

## 15. Casi d'uso di accettazione

### Rollout su 18 facility

Un flow laboratoristico unico è approvato; i binding risolvono endpoint e certificati locali. Il canary coinvolge due facility, poi procede per wave. Il Control Plane mostra identico digest funzionale e binding distinti; una facility può essere sospesa senza bloccare le altre.

### Rotazione certificato

La PKI pubblica la nuova versione, il binding accetta una finestra dual-trust e il runtime ricarica senza restart. Il test sintetico verifica handshake e ACK; scadenza e ritiro della vecchia chiave sono auditati.

### Mapping non compatibile

Una nuova regola produce un concept fuori dominio OMOP. Validazione semantica e golden test bloccano la firma; la versione attiva continua senza degrado.

### Control Plane indisponibile

Il Runtime Cell usa il last-known-good, continua i flow e accoda gli status. Un bundle scaduto o non firmato non viene applicato. La riconciliazione successiva non duplica deployment.

### Correzione urgente

Durante un incidente, due approvatori autorizzano un hotfix limitato al flow coinvolto. Il bundle è firmato, canary, osservato e poi riportato nel ramo principale; il waiver scade automaticamente.

## 16. Gate di completamento

- schema validation, semantic validation e policy-as-code verdi;
- nessun secret o dato sanitario nel repository e nei bundle;
- dipendenze pin-nate per versione o digest;
- firma e verifica offline provate;
- diff semantico e rollback provati;
- isolamento multi-tenant e binding multi-facility testati;
- ricostruzione da repository e registry e autonomia del runtime esercitate;
- evidence bundle completo e linkato alla release.

## 17. Fonti ufficiali

Consultate il 5 settembre 2026:

- [JSON Schema 2020-12](https://json-schema.org/draft/2020-12)
- [SLSA specification 1.2](https://slsa.dev/spec/v1.2/)
- [NIST Secure Software Development Framework 1.1](https://csrc.nist.gov/pubs/sp/800/218/final)
- [Kubernetes declarative configuration](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/declarative-config/)
