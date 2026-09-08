# Topologia di riferimento e benchmark baseline

Stato: baseline Fase 0; numeri di capacità non ancora qualificati  
Ultimo aggiornamento: 7 settembre 2026  
Owner: Platform Engineering e SRE

## Topologia

Il reference environment separa quattro namespace: `hhc-dev`, `hhc-integration`, `hhc-conformance`, `hhc-performance`. Dev privilegia feedback; integration prova contratti e failure; conformance ospita validator/corpus versionati; performance usa nodi e storage dedicati. Nessun database, topic, bucket, identity o chiave è condiviso tra ambienti.

La produzione enterprise prevista usa control plane stateless multi-replica, data plane worker per trust zone/facility, broker durable multi-AZ, PostgreSQL HA per metadati, object storage immutabile/versionato per raw, secret manager/HSM, ingress/egress policy e stack di osservabilità regionale. Tenant e facility sono chiavi di partizione logiche; tenant strategici possono ricevere cella dedicata. Il failover non cambia regione giuridica senza configurazione approvata.

## Benchmark riproducibile

Il benchmark non pubblica capacità inventate. Registra hardware, cloud SKU, zone, CPU/memoria, JVM, GC, storage IOPS/latency, rete, TLS, dimensione messaggi, mix HL7/FHIR/DICOM, mapping/terminology cache, cardinalità tenant, concorrenza, durata e warm-up. Dataset: solo sintetico, seed e digest versionati.

Misure obbligatorie: throughput sostenuto, p50/p95/p99 end-to-end e per stage, error/timeout, queue lag, backpressure onset, CPU, heap/GC, network, storage, retry/dedup, recovery time e drain time dopo 24 ore simulate. I risultati devono includere confidence interval o almeno cinque ripetizioni e deviazione; un singolo picco non è capacity claim.

## Soglie di fondazione

- nessuna perdita silenziosa e nessuna consegna cross-tenant a qualunque carico;
- 100% dei rifiuti con reason code e correlation ID privi di PHI;
- degradazione monotona: backpressure prima di OOM o data loss;
- riavvio worker senza duplicati osservabili nel contratto;
- configurazione e artifact identificabili per digest in ogni misura;
- RTO/RPO misurati secondo la BIA, non desunti dal vendor.

Compose fornisce dipendenze locali; Kustomize descrive gli ambienti. Il gate valida sintassi, isolamento, health probe, resource limit, non-root e immagini per digest dove esterne. La qualifica prestazionale completa avviene nella Fase 4 sulla topologia cliente.

