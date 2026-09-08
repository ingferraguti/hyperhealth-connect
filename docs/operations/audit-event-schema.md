# Schema comune degli eventi di audit

Stato: baseline vincolante 1.0  
Ultimo aggiornamento: 7 settembre 2026  
Owner: Security Architecture e Observability

## Principio

L’audit dimostra chi o quale workload ha compiuto quale operazione, su quale perimetro, con quale decisione e configurazione. Non è un log diagnostico e non contiene payload clinico. È append-only, time-synchronized, firmabile e inoltrato a un sink indipendente.

## Campi obbligatori

| Campo | Tipo | Regola |
|---|---|---|
| `eventId` | UUID | univoco e non riutilizzato |
| `occurredAt` | UTC RFC 3339 | precisione millisecondi; offset vietati nello storage |
| `recordedAt` | UTC RFC 3339 | permette di misurare ritardo e clock skew |
| `action` | stringa controllata | verbo stabile, es. `route.publish` |
| `outcome` | `success|denied|failure` | mai dedotto dal livello log |
| `reasonCode` | stringa controllata | obbligatorio per denied/failure |
| `actorType` | `human|workload|system` | identità non ambigua |
| `actorId` | pseudonymous ID | nessun nome/email/token |
| `tenantId`,`facilityId` | opaque ID | entrambi obbligatori per dati scoped |
| `correlationId`,`traceId` | opaque ID | correlazione senza contenuto clinico |
| `resourceType`,`resourceIdHash` | stringhe | hash keyed/rotatable, mai ID paziente in chiaro |
| `policyId`,`policyVersion` | stringhe | decisione autorizzativa riproducibile |
| `configDigest`,`artifactDigest` | SHA-256 | software e configurazione eseguiti |
| `source`,`destination` | logical ID | niente hostname sensibile o URL con query |
| `schemaVersion` | semver | evoluzione compatibile |

Campi vietati: payload/body, nome/cognome, data di nascita, indirizzo, identificativi sanitari, accession number, token, password, chiavi, stack trace con input, query complete. La redazione è una difesa aggiuntiva: la strategia primaria è non raccogliere.

## Integrità, accesso e retention

Gli eventi sono bufferizzati localmente con limite e allarme, trasmessi con mTLS, partizionati per tenant e conservati secondo finalità. Hash chaining o firma di batch rende rilevabile la manomissione; il digest e il timestamp sono conservati in un dominio amministrativo separato. Gli operatori leggono solo il proprio perimetro. Export, ricerca cross-tenant e break-glass generano a loro volta audit.

## SLO e allarmi

Il 99,9% degli eventi Tier 0 deve arrivare al sink entro 60 secondi. Perdita, schema invalid, clock skew >30 secondi, crescita buffer, sequenza interrotta e calo anomalo del volume aprono un allarme. In caso di sink indisponibile, operazioni cliniche già autorizzate possono continuare entro il buffer; cambi amministrativi privilegiati sono bloccati quando non possono essere auditati.

## Esempio sintetico

```json
{"schemaVersion":"1.0.0","eventId":"01991d95-0000-7000-8000-000000000001","occurredAt":"2026-09-07T10:00:00.000Z","recordedAt":"2026-09-07T10:00:00.025Z","action":"route.publish","outcome":"success","actorType":"workload","actorId":"wrk_6f2b","tenantId":"tn_HHC_SYNTHETIC_A","facilityId":"fc_HHC_SYNTHETIC_01","correlationId":"cor_HHC_SYNTHETIC_1","traceId":"tr_HHC_SYNTHETIC_1","resourceType":"DiagnosticReport","resourceIdHash":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","policyId":"route-policy","policyVersion":"1.0.0","configDigest":"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","artifactDigest":"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","source":"lis-synthetic","destination":"ehr-synthetic"}
```

Fonti verificate il 7 settembre 2026: [OpenTelemetry Logs data model](https://opentelemetry.io/docs/specs/otel/logs/data-model/) e [IHE Audit Trail and Node Authentication](https://profiles.ihe.net/ITI/TF/Volume1/ch-9.html).

