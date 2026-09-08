# Pratiche di sviluppo

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Engineering |
| Approvatori | Architecture, Product Security, SRE, Quality Engineering |

## 1. Obiettivo

Questa baseline rende il codice HHC mantenibile, verificabile e sicuro per installazioni multi-azienda e multi-facility. Ottimizza il flusso per cambi piccoli e reversibili, preservando tracciabilità e responsabilità umana anche nell'agentic coding.

## 2. Architettura del codice

Il Control Plane parte come monolite modulare; i Runtime Worker sono deployable separati. Connector, canonical e semantic layer e proiezione OMOP restano domini distinti. Dipendenze tra moduli passano da API pubbliche interne, eventi o port e adapter, mai da accesso diretto alle tabelle altrui.

Un servizio viene estratto solo con evidenza di almeno uno tra: scaling indipendente, failure isolation, diverso trust boundary, ownership o lifecycle autonomo, vincolo di deployment. L'estrazione deve ridurre un problema misurato, non anticiparne uno ipotetico.

## 3. Struttura e ownership

Il repository separa almeno platform e control-plane, runtime, connector-sdk e connectors, canonical e semantic, OMOP, API e contracts, UI, deploy, test-kit e docs. Ogni directory rilevante ha owner e reviewer di fallback. Moduli critici richiedono CODEOWNERS o controllo equivalente.

Generated code, schema e migration hanno sorgente identificabile e comando riproducibile. Non si modifica manualmente un output generato.

## 4. Scelta di linguaggi e framework

La baseline fondativa prevede stack JVM per routing e sanità e TypeScript per la UI, ma la versione concreta di linguaggio, LTS e framework è pin-nata dalla release toolchain. Un nuovo linguaggio richiede motivazione operativa, supportabilità, osservabilità, supply-chain review e ADR.

Si preferiscono standard library e primitive già adottate. Il framework non deve trapelare nel modello canonico, nel Connector SDK o nei contratti pubblici.

## 5. Convenzioni di codice

- formatter e lint automatici sono autoritativi e identici in locale e CI;
- compiler e type checker usano modalità strict e warning bloccanti concordati;
- nomi esprimono dominio sanitario e unità di misura; acronimi ambigui sono evitati;
- funzioni mantengono responsabilità coesa; side effect e I/O sono espliciti;
- stato mutabile globale, singleton nascosti e clock o random non iniettati sono vietati;
- errori tipizzati conservano categoria, retryability e correlation senza PHI;
- log non sostituiscono return value, metriche o audit;
- commenti spiegano vincoli e decisioni, non riscrivono il codice;
- dead code e feature flag scaduti vengono rimossi con test.

## 6. Tempo identificativi e determinismo

Tutti i timestamp applicativi hanno timezone o offset oppure sono UTC; il timezone clinico originario e la precisione sono preservati quando semanticamente rilevanti. Clock e scheduler sono controllabili nei test. DST, leap day, fine mese e latenze fuori ordine hanno fixture dedicate.

ID tecnici non incorporano PHI. Correlation ID, business identifier e pseudonymous subject token hanno significato distinto. Random e hash dichiarano algoritmo e versione se influenzano replay o deduplica.

## 7. Concorrenza e affidabilità

Ogni handler dichiara boundary transazionale, idempotency key, ordering scope, timeout e comportamento dopo crash. Nessun retry è infinito. I retry rispettano budget complessivo, backoff con jitter, `Retry-After` e circuit breaker.

L'ACK viene emesso soltanto al durability point contrattuale. Effetti esterni non transazionali usano outbox, inbox, ledger o riconciliazione. Si applica il [modello di affidabilità](../operations/reliability-and-error-model.md).

## 8. Database e migration

- migration forward-only, numerate e immutabili dopo release;
- schema change secondo expand, migrate e contract per compatibilità N e N-1;
- nessuna migration lunga prende lock non misurati su tabelle critiche;
- backfill è resumable, rate-limited, osservabile e idempotente;
- indici e query sono verificati con cardinalità rappresentative;
- rollback applicativo e roll-forward dati sono distinti;
- backup, restore e replica vengono provati per componenti stateful.

L'accesso cross-tenant richiede scopo esplicito e test negativo. Query dinamiche sono parametrizzate; migration non contengono dati clinici reali.

## 9. API e contratti

Lo sviluppo è contract-first secondo [API standards](../api/api-standards.md). OpenAPI, AsyncAPI e schema vengono lintati, testati e pubblicati nel registry. Una breaking change richiede nuova versione o migration compatibile; la sola compatibilità sintattica non basta se cambia semantica, default, ordering o timing.

I consumer contract proteggono integrazioni note, senza impedire l'evoluzione di campi opzionali. Errori HTTP seguono Problem Details; protocolli sanitari preservano ACK e NACK e codici standardizzati.

## 10. Sicurezza e privacy nel codice

Input esterni sono non fidati: limiti di dimensione e profondità, parser sicuri, timeout, allowlist e canonicalization precedono l'uso. XML external entity, deserializzazione polimorfica non vincolata, path traversal, SSRF e injection hanno controlli e test dedicati.

L'autorizzazione è server-side per ogni risorsa e scope. I filtri UI non sono controlli. I secret provengono da vault, la PHI è redatta nei log e i dati sintetici sostituiscono copie di produzione. Threat model e privacy review fanno parte del design per nuovi boundary.

## 11. Safety e semantica clinica

Una trasformazione clinica non inventa valori, unità, negazioni o precisione. Missing, unknown, not-applicable e invalid restano distinti. Conversioni di unità usano librerie e regole versionate e round-trip test. Mapping terminologico ad alto impatto richiede revisore clinico o data steward e provenance.

Failure ambigue sono visibili e riconciliabili; il software non converte silenziosamente un errore in successo. Ogni regola che può cambiare destinatario o interpretazione clinica ha golden test leggibile.

## 12. Branch commit e pull request

Si usa trunk-based development con branch brevi e integrazione frequente. Ogni PR:

- ha scopo singolo, ticket o requisito e rischio dichiarato;
- include codice, test, documentazione e migration necessari;
- mostra impatto tenant e facility, compatibilità e rollback;
- contiene output sintetici, mai PHI;
- supera gate automatici prima dell'approvazione;
- riceve review indipendente proporzionata al rischio.

I commit sono firmati dove previsto dalla release policy e non mescolano refactoring non necessario a correzioni urgenti.

## 13. Agentic coding

Il contributo generato da agente è trattato come codice di contributor non fidato finché non verificato. Prompt e contesto non autorizzano accesso a PHI, segreti o produzione. L'agente non modifica test o oracle per nascondere un difetto, non riduce gate e non approva, integra o distribuisce il proprio output.

La PR registra uso materiale dell'agente, modello e toolchain quando richiesto, assunzioni, test eseguiti e parti ad alta criticità riviste manualmente. Si applica integralmente la [Agentic Coding Quality Policy](../testing/agentic-coding-quality-policy.md).

## 14. Strategia di test

La piramide è adattata al rischio: unit e property per logica, contract e conformance per boundary, integration e component per infrastruttura, end-to-end selettivi, performance, soak, chaos e security. Le fixture sanitarie sono sintetiche, versionate e includono errori realisti.

Un bug di produzione genera un test regressivo al livello più basso capace di riprodurlo e, se sistemico, un aggiornamento di policy o runbook. La [strategia di test](../testing/test-strategy.md) è normativa.

## 15. Performance engineering

Ogni hot path ha budget di latenza, allocation, payload e query. Si misurano p50, p95, p99, throughput sostenuto, saturation, queue lag e recovery backlog per tenant e facility. Benchmark senza ambiente, dataset e configurazione riproducibili non supportano una decisione.

Ottimizzazioni preservano correttezza, audit e isolamento; sampling o batching non possono perdere eventi clinici senza contratto esplicito. Regression threshold e capacity envelope sono gate di release.

## 16. Osservabilità

Nuovo codice emette metriche bounded-cardinality, trace context e log strutturati conformi alle convenzioni. Tenant e facility sono label solo quando cardinalità e privacy lo consentono. Payload, identificativi paziente e token non entrano nella telemetria.

Ogni operazione amministrativa mutante genera audit separato. Runbook e alert accompagnano una nuova dipendenza critica o failure mode.

## 17. Feature flag

Ogni flag ha owner, motivazione, scope, default sicuro, data di scadenza e piano di rimozione. Autorizzazione, cifratura, audit e conformance non sono disattivabili da flag ordinario. Il targeting cross-tenant è testato e auditato.

## 18. Definition of Ready

Un item è pronto quando ha outcome, criteri d'accettazione, data class, tenant scope, dipendenze, rischio, SLO, compatibilità e strategia di test. Per flussi clinici include esempi sintetici e owner semantico.

## 19. Definition of Done

- criteri e requisiti tracciati a test;
- code review e owner approval completate;
- test, lint, type, SAST, SCA, secret scan e policy verdi;
- contract, migration, rollback e osservabilità aggiornati;
- performance e failure test proporzionati al tier;
- nessun finding bloccante o waiver scaduto;
- documentazione, ADR e runbook coerenti;
- artifact, SBOM, provenance e firme prodotti dalla pipeline;
- canary, rollout e post-deploy verification definiti.

## 20. Release e manutenzione

Le release sono riproducibili, versionate, firmate e accompagnate da compatibility matrix, migration guide, security notes ed evidence. Il supporto N e N-1 e il calendario EOL sono pubblici per i clienti. Le patch critiche seguono un ramo controllato e vengono riportate sul trunk.

Il debito tecnico ha owner, impatto e scadenza; non è un contenitore generico. Le metriche includono lead time, change failure rate, recovery time, flaky test, escaped defect, vulnerability age e toil, lette nel contesto e non usate per valutare individui.

## 21. Casi d'uso di accettazione

### Nuovo connector LIS

Il team implementa l'SDK, parser e validator, health, metriche, synthetic fixture, contract e failure test. Security verifica input e TLS; Clinical Informatics valida i mapping. Il connector è canary in una facility prima della wave aziendale.

### Migration ad alta cardinalità

La nuova colonna viene aggiunta nullable, il backfill è resumable e rate-limited, entrambe le versioni applicative funzionano, poi il vincolo è attivato. Failover durante backfill e restore sono testati.

### Correzione agentica di un ACK

L'agente propone patch e regression test; un reviewer controlla boundary transazionale e standard HL7. Mutation e negative test dimostrano che ACK non viene emesso prima della durability. Merge e rollout restano umani.

### Estrazione di un servizio

Le metriche mostrano che job OMOP saturano il Control Plane. La design review definisce API, ownership, SLO e failure isolation; si migra per strangler con doppia lettura controllata, senza cambiare il modello canonico.

## 22. Gate di qualità

- formatter, lint, type e compiler riproducibili e verdi;
- coverage risk-based, mutation score e contract o conformance entro soglie del test plan;
- zero accessi cross-tenant non autorizzati nei test negativi;
- performance budget e recovery test superati per Tier 0 e Tier 1;
- migration N e N-1, backup, restore e rollout testati;
- ogni artifact ha SBOM, provenance, firma e owner;
- documentazione e audit evidence completi.

## 23. Fonti ufficiali

Consultate il 5 settembre 2026:

- [NIST SSDF 1.1](https://csrc.nist.gov/pubs/sp/800/218/final)
- [SLSA specification 1.2](https://slsa.dev/spec/v1.2/)
- [OWASP ASVS 5.0](https://owasp.org/www-project-application-security-verification-standard/)
- [OpenTelemetry specification](https://opentelemetry.io/docs/specs/otel/)
- [RFC 9457 Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)
