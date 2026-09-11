# ADR-025 — Baseline tecnologica della Fase 1

Stato: Accepted
Data: 10 settembre 2026
Owner: Platform Architecture
Reviewer: Product Security, SRE, Data Architecture, Legal/Open Source Office
Decision class: A3 — persistenza, identità e osservabilità

## Contesto

La Fase 1 deve produrre un Technical MVP ripetibile senza trasformare strumenti di laboratorio in accoppiamenti irreversibili del prodotto enterprise. Servono implementazioni concrete per Platform DB, raw object store, migration, OIDC e telemetria, ma installazioni multiazienda e multifacility devono poter usare servizi gestiti o on-premise equivalenti mantenendo gli stessi contratti, requisiti di residenza e controlli.

## Decisione

### Platform DB

PostgreSQL 18.6 è la baseline R0.2. È il system of record del Control Plane e conserva metadata operativi, stato dei deployment, policy reference e ledger relazionali. Il payload raw non viene archiviato in colonne del Platform DB. In Fase 1 si usa un'istanza singola soltanto negli ambienti developer; qualification e produzione richiedono topologia HA, backup point-in-time, fencing del writer, replica monitorata e restore provato.

Il codice applicativo usa SQL PostgreSQL esplicito e repository scoped; non assume un servizio cloud particolare. La compatibilità dichiarata è PostgreSQL 18.x. Un'altra distribuzione o servizio compatibile deve superare migration, failover, isolamento, performance e restore suite prima di essere supportato.

### Orchestratore di riferimento

Kubernetes 1.36.4 è la baseline del profilo qualification R0.2 perché è una patch supportata e con lifecycle oltre la data target della fase. Il prodotto non dipende da API alpha/beta non dichiarate. Compose resta developer-only; distribuzioni Kubernetes gestite o on-premise devono rispettare version skew, CNI/storage class, topology spread, Pod Security, network policy, backup e upgrade contract HHC.

### Raw object store

Il contratto è S3-compatible con adapter HHC e capability negotiation. Le capability minime sono conditional create, checksum verificabile, encryption at rest, TLS, versioning/immutability o retention lock equivalente, lifecycle, audit degli accessi e replica/backup qualificabile. Il core non usa API proprietarie del fornitore.

SeaweedFS 4.46 è l'implementazione di riferimento per sviluppo e test R0.2, non una certificazione preventiva per produzione enterprise. È scelto perché offre API S3, scaling orizzontale, replica ed erasure coding mantenendo licenza Apache-2.0. L'adozione in produzione resta subordinata a qualification della topologia concreta, support model, restore, object-lock semantics e security review. Servizi S3 gestiti o altri object store possono essere qualificati tramite lo stesso contract test kit.

### Migration tool

Flyway Community 13.5.0 gestisce le migration SQL versionate. Le migration sono forward-only in produzione; rollback applicativo usa expand/migrate/contract e compatibilità N/N-1, non script distruttivi automatici. Funzioni commerciali o moduli con licenza diversa non entrano nella distribuzione senza record separato. Ogni migration è checksumata, testata su database vuoto e su snapshot N-1, e verificata dopo restore.

### Identity provider

Keycloak 26.7.3 è il provider OIDC di riferimento per test e conformance. HHC dipende da OIDC/OAuth 2.0 e dai claim contrattuali, non dalle API amministrative Keycloak nel runtime. L'enterprise può federare un IdP qualificato diverso. Human identity e workload identity restano client, audience, grant e policy separati. Gli ambienti non developer richiedono TLS, secret esterni, rotazione chiavi, cache JWKS bounded, revoca, clock monitoring e test di indisponibilità IdP.

### Telemetry pipeline e backend

OpenTelemetry Collector Contrib 0.160.0 è il boundary vendor-neutral. Prometheus 3.14.0 conserva metriche e alimenta alert; Jaeger 2.20.0 riceve e interroga trace in ambiente R0.2. Il backend dei log non è imposto in WP1-00: gli eventi strutturati sono esportati via OTLP verso un backend qualificato e il test kit verifica redazione e loss accounting.

In developer mode sono ammesse istanze singole e retention breve. Qualification usa collector agent/gateway ridondanti, coda persistente dove necessaria, backend separato dal failure domain applicativo e alert out-of-band. La telemetria non è il registro audit: indisponibilità del backend non autorizza perdita silenziosa né inserimento di payload nei fallback.

## Confini enterprise

- Ogni scelta è incapsulata da un contract testato e da configuration schema versionato.
- Digest OCI e versioni sono registrati in `governance/dependencies.yml`; tag mobili come `latest` sono vietati.
- I componenti stateful non condividono database, bucket, credential o encryption scope fra tenant se il deployment profile richiede isolamento dedicato.
- La topologia developer non costituisce prova di HA, BC o DR.
- Un upgrade segue canary, backup, preflight, compatibility matrix e rollback documentato.
- Nessun backend di osservabilità riceve PHI o secret; tenant e facility sono identificatori interni pseudonimi a cardinalità controllata.

## Alternative considerate

- Database embedded: respinto perché non offre il modello HA, auditing e recovery richiesto.
- Vincolo a un object store cloud: respinto per portabilità, residenza e installazioni on-premise.
- MinIO Community: non adottato nella baseline 2026 per rischio di manutenzione e licenza; una futura rivalutazione richiede nuovo record.
- Grafana/Loki nella baseline core: rinviati per evitare di imporre componenti AGPL e perché Prometheus/Jaeger bastano al gate R0.2; possono essere servizi esterni qualificati.
- Identity proprietaria nel core: respinta; il confine rimane OIDC/OAuth standard.
- Telemetria diretta verso ogni vendor: respinta perché moltiplica coupling, gestione secret e percorsi di leakage.

## Conseguenze e rischi residui

La baseline rende avviabile l'implementazione e limita il lock-in. Restano da dimostrare la semantica S3 richiesta su SeaweedFS, la topologia HA del Platform DB, l'assenza di regressioni delle migration e il comportamento della pipeline di telemetria in saturazione. Questi rischi bloccano rispettivamente G3, M4, M5 e G8 se non producono evidence positiva.

## Evidenze richieste

- contract test S3 con checksum, conditional create, immutabilità, retention e restore;
- migration test fresh/N-1/failed/resume e backup-restore;
- OIDC negative test per issuer, audience, scope, expiry, revoca, JWKS rotation e outage;
- telemetry loss accounting, cardinality budget, canary-PHI leakage scan e outage recovery;
- SBOM, vulnerability scan, license disposition e firma per ogni immagine.

## Fonti ufficiali

Fonti verificate il 10 settembre 2026:

- [PostgreSQL 18.6 documentation](https://www.postgresql.org/docs/18/)
- [Kubernetes 1.36 lifecycle e patch release](https://kubernetes.io/releases/1.36/)
- [SeaweedFS repository e capability S3/replica](https://github.com/seaweedfs/seaweedfs)
- [Flyway 13.5.0 release](https://github.com/flyway/flyway/releases/tag/flyway-13.5.0)
- [Keycloak 26.7.3 release](https://github.com/keycloak/keycloak/releases/tag/26.7.3)
- [OpenTelemetry Collector deployment patterns](https://opentelemetry.io/docs/collector/deploy/)
- [OpenTelemetry Collector 0.160.0 release](https://github.com/open-telemetry/opentelemetry-collector-releases/releases/tag/v0.160.0)
- [Prometheus 3.14.0 release](https://github.com/prometheus/prometheus/releases/tag/v3.14.0)
- [Jaeger 2.20 deployment](https://www.jaegertracing.io/docs/2.20/deployment/)

## Collegamenti

- [ADR-001](./ADR-001-control-plane-data-plane.md)
- [ADR-004](./ADR-004-immutable-raw-events.md)
- [ADR-008](./ADR-008-storage-and-phi-logging.md)
- [ADR-009](./ADR-009-identity-and-tenancy.md)
- [Performance manifest](../testing/phase-1-performance-test-manifest.yml)
- [Environment manifest](../testing/phase-1-reference-environment.yml)
