# Dipendenze licenze e supply chain

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Product Security e Legal Engineering |
| Approvatori | Architecture, Legal, Security, Release Engineering |

## 1. Obiettivo

HHC usa componenti maturi senza trasferire nel prodotto rischi non governati di sicurezza, disponibilità, licenza o lock-in. Ogni dipendenza deve essere inventariata, pin-nata, verificata, isolata dietro un confine appropriato e sostituibile con un piano realistico.

La conformità tecnica non sostituisce il parere legale: licenze, terminologie cliniche, dataset, modelli AI e componenti commerciali sono valutati per versione, modalità d'uso, distribuzione, territorio e contratto.

## 2. Ambito

La policy copre librerie dirette e transitive, immagini container, base image, plugin, connector, runtime, frontend, package di test, tool CI, firmware o appliance inclusi, modelli e dataset, vocabolari e terminologie, codice generato e servizi SaaS usati nella build o nel prodotto.

Tool solo sviluppatore e dipendenze di test restano inventariati: possono compromettere sorgenti, artifact o pipeline anche se non sono distribuiti.

## 3. Ruoli

| Ruolo | Responsabilità |
|---|---|
| Dependency owner | necessità, aggiornamenti, test, deprecazione, exit plan |
| Architecture | coerenza, boundary, sostituibilità, impatto prestazioni |
| Product Security | rischio, vulnerabilità, provenance, hardening |
| Legal | licenza, notice, copyleft, brevetti, dati e terminologie |
| Release Engineering | lock, mirror, SBOM, firma e riproducibilità |
| SRE | operabilità, capacità, HA, backup e support lifecycle |

## 4. Processo di ammissione

Prima dell'introduzione, la proposta documenta:

1. capability richiesta e ragione per cui non basta il codice esistente;
2. alternative considerate, incluso build e servizio esterno;
3. repository e distributore ufficiali, identità del package e versione;
4. licenza del componente e delle dipendenze transitive;
5. manutenzione, frequenza release, support policy, bus factor e issue security;
6. vulnerabilità note, superficie d'attacco e privilegi;
7. impatto su performance, memoria, startup, rete e storage;
8. comportamento in fault, upgrade, rollback, backup e restore;
9. compatibilità con deployment on-premises, air-gapped e multi-tenant;
10. boundary di astrazione, test di sostituzione e piano d'uscita;
11. dati trasmessi, residenza, subprocessors e condizioni contrattuali;
12. decisione, scadenza della review e owner.

Una dipendenza non è ammessa per sola popolarità o perché suggerita da un agente.

## 5. Regole di acquisizione

- vietati versioni mobili come `latest`, range non controllati e download runtime arbitrari;
- package manager usa lockfile e verifica d'integrità;
- immagini sono referenziate per digest e provengono da registry approvati;
- artifact esterni passano da mirror e quarantena con malware e signature verification;
- namespace, maintainer e coordinate sono verificati contro typosquatting e dependency confusion;
- build di release non accede a repository non dichiarati;
- checksum, signature e provenance sono conservati nell'evidence bundle;
- componenti abbandonati o end-of-life sono bloccati salvo deroga temporanea.

## 6. SBOM e inventario

Ogni artifact distribuibile produce un SBOM machine-readable CycloneDX o SPDX, con versione esatta, hash, supplier, licenza dichiarata e conclusa, relazioni transitive e componenti incorporati. L'SBOM è associato al digest dell'artifact e firmato con la release.

L'inventario deve rispondere rapidamente a: dove è usato un package, quale versione è in ogni installazione, se è raggiungibile, chi è l'owner, quali tenant e facility sono esposti e quale remediation è disponibile.

Un VEX può registrare lo stato di sfruttabilità con motivazione ed evidenza, ma non cancella il finding originario e scade quando cambiano artifact, configurazione o intelligence.

## 7. Classificazione delle licenze

| Classe | Trattamento |
|---|---|
| Permissiva | ammessa dopo verifica notice, attribution, brevetti e transitivi |
| Weak copyleft | review Legal e verifica di linking, modifica e distribuzione |
| Strong o network copyleft | bloccata per default; eccezione architetturale e legale esplicita |
| Proprietaria o commerciale | contratto, metriche di licenza, continuità fornitore e uso offline |
| Public domain o Creative Commons | verificare applicabilità a software, dati, documenti e marchi |
| Sconosciuta o custom | non ammessa finché Legal non conclude la valutazione |

Non esiste una allowlist universale: una stessa licenza può produrre obblighi diversi se il componente è modificato, collegato, offerto come servizio o distribuito nell'appliance.

## 8. Terminologie dati e contenuti sanitari

SNOMED CT, LOINC, ICD, ATC, UCUM, vocabolari OMOP, value set nazionali e cataloghi locali hanno condizioni distinte. Prima di importazione, caching, redistribuzione o embedding si registrano fonte, release, territorio, entitlement, restrizioni e scadenza.

Per SNOMED CT si verifica l'Affiliate License e l'appartenenza del territorio a SNOMED International; per contenuti nazionali si verifica l'autorità competente. Un download disponibile tecnicamente non implica diritto di redistribuzione.

I package terminologici sono firmati, versionati e separati dal binario, così installazioni con diritti diversi possono ricevere soltanto i contenuti autorizzati.

## 9. Baseline dei componenti strategici

Camel, HAPI, Open eHealth IPF, dcm4che, PostgreSQL, broker Kafka-compatible, object storage, Keycloak, OpenTelemetry e componenti OHDSI sono candidati o baseline architetturali, non eccezioni alla policy. Ogni release HHC pinna versioni concrete dopo matrice compatibilità, test, licenze e verifica supporto.

I connector ufficiali schermano API di terze parti tramite [Connector SDK](../connectors/connector-sdk.md). ATLAS e WebAPI sono integrazioni opzionali secondo [compatibilità OHDSI](../omop/analytics-and-ohdsi-compatibility.md), non dipendenze del core.

## 10. Vulnerability management

Scanner SCA, container, secret e malware girano su pull request, build, registry e scansione continua delle release supportate. I finding sono correlati all'SBOM e arricchiti con exploitability, reachability, exposure e impatto clinico.

La priorità combina severità tecnica, exploit noto, accessibilità, privilegio, PHI, impatto su disponibilità e compensating control. Le finestre massime sono definite nella policy di sicurezza operativa; un finding critico attivamente sfruttato può imporre hotfix, disabilitazione o isolamento immediato.

`Not affected` richiede evidenza ripetibile. L'assenza di exploit pubblico non è chiusura. Le eccezioni hanno owner, scadenza, compensating control e approvazione Product Security.

## 11. Aggiornamenti e compatibilità

- bot o agente può proporre un upgrade, mai approvarlo da solo;
- le versioni avanzano in piccoli incrementi quando possibile;
- upgrade major richiede ADR o design review se cambia boundary o comportamento;
- contract, conformance, performance, chaos e security test sono selezionati dal rischio;
- dipendenze stateful provano migration, backup, restore, failover e rollback o roll-forward;
- N e N-1 sono mantenuti dove previsto dalla compatibility policy;
- la release conserva il vecchio artifact per rollback sicuro, non il repository esterno.

## 12. Provenance e build

La pipeline mira a provenance SLSA, build isolata e ripetibile, identità workload, runner effimeri e privilege minimo. Sorgente, workflow, builder, input e output sono collegati tramite attestazioni. Release, container, bundle e SBOM sono firmati; la verifica avviene prima del deploy e può funzionare offline.

Le chiavi di firma non sono disponibili alle pull request non fidate. Un contributor non può modificare contemporaneamente sorgente, regole di policy e chiave o approvazione della release.

## 13. Componenti di terze parti e Connector Marketplace

Un connector di terze parti è non fidato per default. Il manifest dichiara SDK, capability, protocolli, accesso a rete e filesystem, data class, subprocess e dipendenze. Il runtime applica sandbox o isolation, quota, egress policy e firma publisher. Certificazione HHC indica soltanto la matrice testata, non trasferisce responsabilità clinica del cliente.

Revoca, kill switch governato e lista di versioni vulnerabili devono poter bloccare nuove installazioni senza interrompere automaticamente flow clinici già attivi: l'azione operativa segue analisi d'impatto e runbook.

## 14. Continuità e rischio fornitore

Per componenti critici si valutano replica multi-zone, support lifecycle, export, formati aperti, backup, recovery, forkability o escrow quando appropriato e alternativa tecnica. Un servizio cloud obbligatorio è incompatibile con profili air-gapped salvo modalità locale equivalente o decisione esplicita di prodotto.

Il registro include EOL ed EOS, ultima versione supportata, sostituto, tempo stimato di migrazione e installazioni coinvolte. Alert scattano almeno 12 mesi prima di una scadenza nota quando il fornitore la pubblica.

## 15. Notice e obblighi di distribuzione

Ogni pacchetto contiene notice e license bundle generati dall'inventario revisionato. Ove richiesto, source offer, modified-source notice o attribution sono prodotti in modo verificabile. UI, API e appliance rendono accessibili le informazioni senza esporre dati riservati.

## 16. Casi d'uso di accettazione

### CVE in una libreria HL7

La query SBOM identifica versioni, immagini e facility. Product Security valuta la reachability del parser e l'exposure degli ingressi, applica mitigazione, prova payload regressivi e distribuisce un hotfix a wave con rollback.

### Dipendenza proposta da codice agentico

La CI rileva il nuovo package e blocca la merge perché manca la scheda di ammissione. L'owner dimostra la necessità, pinna la versione, aggiunge test e ottiene review licenza e sicurezza.

### Vocabolario con diritti territoriali

Il package è disponibile solo ai tenant con entitlement valido. Export e backup mantengono metadata di licenza; una nuova facility non eredita automaticamente l'accesso dell'azienda capogruppo.

### Registry esterno indisponibile

Build e restore usano il mirror approvato e i digest già acquisiti. Il deploy non risolve `latest` né scarica componenti durante l'avvio.

### Progetto open source abbandonato

L'owner attiva l'exit plan: adapter compatibile, fixture contract e migrazione incrementale. L'architettura evita una riscrittura simultanea di tutti i flow.

## 17. Gate di release

- SBOM completo, firmato e associato agli artifact;
- zero licenze sconosciute o vietate senza eccezione valida;
- zero dipendenze mobili, non pin-nate o provenienti da fonti non approvate;
- vulnerability findings trattati secondo SLA e rischio;
- notice bundle e obblighi di redistribuzione verificati;
- provenance e signature verification positive;
- lifecycle, owner ed exit plan presenti per ogni componente critico;
- test di restore senza accesso ai registry pubblici superato.

## 18. Fonti ufficiali

Consultate il 5 settembre 2026:

- [SPDX 3.0](https://spdx.github.io/spdx-spec/v3.0/)
- [CycloneDX specification](https://cyclonedx.org/specification/overview/)
- [SLSA specification 1.2](https://slsa.dev/spec/v1.2/)
- [NIST SSDF 1.1](https://csrc.nist.gov/pubs/sp/800/218/final)
- [OpenSSF Scorecard](https://scorecard.dev/)
- [SNOMED CT licensing](https://www.snomed.org/get-snomed)
- [Regolamento UE 2024/2847 Cyber Resilience Act](https://eur-lex.europa.eu/eli/reg/2024/2847/oj)
