# Governance della documentazione

| Campo | Valore |
|---|---|
| Stato | Baseline enterprise 1.0 |
| Ultimo aggiornamento | 5 settembre 2026 |
| Owner | Architecture Governance |
| Approvatori | Product, Engineering, Quality, Security, Clinical Safety |

## 1. Scopo e principio

La documentazione HHC è parte controllata del prodotto: guida implementazione, test, esercizio, audit e decisioni per installazioni multi-azienda e multi-facility. Deve descrivere il sistema effettivo, non soltanto l'intenzione. Una modifica che rende falso un documento normativo non è completata finché codice e documento non sono riallineati.

Questa policy governa tutti i Markdown sotto `docs/`, i README di progetto e gli artifact tecnici collegati. Documenti contrattuali, DPIA e manuali cliente possono avere repository e approvazioni aggiuntivi, ma non possono contraddire la baseline senza eccezione o ADR.

## 2. Gerarchia delle fonti interne

In caso di conflitto si applica questa precedenza:

1. legge, contratto e standard normativo applicabile, interpretati dai ruoli competenti;
2. requisito di prodotto approvato e matrice di applicabilità cliente o paese;
3. ADR `Accepted` per le decisioni strutturali;
4. specifiche di architettura, sicurezza, dati e API;
5. contract machine-readable e configuration schema versionati;
6. runbook e guide operative;
7. esempi, tutorial e materiale storico.

Il codice non diventa automaticamente la fonte di verità se devia da una decisione approvata: la deviazione è un defect o richiede un change governance.

## 3. Classi di documento

| Classe | Contenuto | Owner tipico |
|---|---|---|
| Product | visione, scope, requisiti, persona, use case | Product |
| Architecture e ADR | confini, decisioni, qualità, flussi | Architecture |
| Standard e semantic | profili, modelli, mapping, terminologie | Interoperability e Clinical Informatics |
| API e connector | contratti, SDK, catalogo | Platform e Integration Engineering |
| Security e privacy | threat, accesso, protezione dati | Security e DPO/Privacy |
| Deployment e operations | topologie, SLO, HA/DR, runbook | SRE e Operations |
| Testing e development | policy, toolchain, gate, pratiche | Quality e Engineering |
| Roadmap e risk | milestone, dipendenze, rischio | Product e Program |
| Reference | provenance, assessment, glossario | Architecture Governance |

## 4. Metadata obbligatori

Ogni documento normativo contiene stato, data di aggiornamento, owner e approvatori. Quando pertinente include versione, audience, scope, dati trattati, review date e riferimenti al requisito o ADR.

La data indica l'ultima review sostanziale, non una formattazione automatica. L'owner è un ruolo, non una persona, per garantire continuità; il sistema di governance associa il ruolo al responsabile corrente.

## 5. Stati e lifecycle

```text
DRAFT → IN_REVIEW → APPROVED → ACTIVE → SUPERSEDED → RETIRED
```

- `DRAFT`: proposta modificabile, non normativa;
- `IN_REVIEW`: contenuto congelato per review interdisciplinare;
- `APPROVED`: approvazioni ottenute, in attesa della release o efficacia;
- `ACTIVE`: baseline applicabile alla versione dichiarata;
- `SUPERSEDED`: sostituita ma conservata e collegata al successore;
- `RETIRED`: non più applicabile, conservata secondo retention.

Gli ADR usano il lifecycle specificato in [ADR governance](../adr/README.md). Un documento non può definirsi definitivo in senso assoluto: è una baseline versionata ed evolvibile.

## 6. Identificativi versioni e riferimenti

Requisiti usano ID stabili; ADR mantengono il numero; test case e controlli hanno ID collegabili. I link sono relativi per i file del repository e assoluti per le fonti esterne. Si preferisce un link alla pagina ufficiale stabile, con versione o data quando il contenuto può evolvere.

Una modifica breaking della semantica documentale incrementa la baseline e produce migration note. Correzioni editoriali non cambiano la decisione, ma restano nel version control.

## 7. Processo di modifica

1. aprire issue o change con motivazione, scope e documenti impattati;
2. identificare requisiti, ADR, contratti, test, runbook e release coinvolti;
3. aggiornare tutti gli artifact nella stessa change quando possibile;
4. eseguire review tecnica, clinica, security, privacy e SRE secondo rischio;
5. verificare link, struttura, terminologia, fonti e incongruenze numeriche;
6. approvare con segregazione dei compiti;
7. pubblicare con la release o con data di efficacia esplicita;
8. registrare successore e rimuovere riferimenti obsoleti.

Una PR non può risolvere un conflitto eliminando silenziosamente il requisito più severo. Deve motivare il cambiamento e ottenere l'approvazione del relativo owner.

## 8. Tracciabilità

La catena minima è:

```text
Outcome → Requirement → ADR o design → Control o implementation
        → Test case → Evidence → Release → Runbook e SLO
```

Per mapping clinici e OMOP si aggiungono source field, semantic rule, terminology snapshot, approvatore e data-quality result. Per incidenti si collegano timeline, root cause, corrective action, regression test e documento aggiornato.

La tracciabilità può essere automatizzata tramite metadata, ma rimane comprensibile senza uno specifico vendor tool.

## 9. Policy delle fonti

Le affermazioni variabili o normative citano fonti primarie: EUR-Lex, ente standard, specifica o repository ufficiale. Blog, aggregatori e risposte di modelli possono orientare la ricerca ma non sostengono da soli una decisione.

Ogni sezione fonti indica la data di consultazione. Versione, release, commit o URL versionato sono registrati quando disponibili. Per standard a pagamento si cita la pagina catalogo e si verifica il testo licenziato tramite il responsabile competente prima di dichiarare conformità.

Le fonti informative sono distinte dai requisiti contrattuali. La dicitura “conforme” è usata soltanto con scope, versione, profilo e prove definiti.

## 10. Qualità editoriale

- un solo titolo H1 per file e gerarchia senza salti irragionevoli;
- apertura con decisione, scopo e destinatario;
- termini coerenti con il glossario di dominio;
- `tenant`, `organization`, `facility`, `application` ed `endpoint` non sono sinonimi;
- requisiti espressi con soggetto, verbo verificabile e criterio;
- esempi marcati come informativi e privi di PHI;
- numeri accompagnati da unità, scope, percentile e finestra quando pertinenti;
- diagrammi testuali accessibili anche senza rendering;
- link locali validi e codice fenced bilanciato;
- nessun placeholder o promessa di completamento in una baseline attiva.

## 11. Review cadence

| Criticità | Review minima | Trigger immediato |
|---|---|---|
| Tier A: sicurezza, privacy, HA/DR, standard clinici, API pubbliche | trimestrale | legge, CVE sistemica, incidente, nuova major |
| Tier B: architettura, testing, deployment, mapping, OMOP | semestrale | decisione o release che cambia comportamento |
| Tier C: roadmap, guide e reference | annuale | milestone, EOL o feedback materiale |

La review non consiste nel cambiare la data: produce esito `confirmed`, change richiesto o retirement, con reviewer ed evidence.

## 12. Documentazione di release

Ogni release enterprise include almeno:

- release notes e known issue;
- compatibility matrix N e N-1;
- API e schema diff;
- migration, upgrade, rollback e backup o restore guide;
- security advisory e dependency o license notice;
- SBOM, provenance e firme;
- deployment topology e capacity envelope validati;
- runbook e SLO aggiornati;
- test report, conformance ed evidence dei gate;
- lista dei documenti con versione e digest.

## 13. Informazioni sensibili

Documenti e diagrammi non contengono PHI reale, credenziali, chiavi, hostname sensibili o dettagli sfruttabili non necessari. Esempi usano tenant e pazienti sintetici. I security finding non risolti possono stare in repository con accesso limitato, mantenendo nel documento pubblico il controllo e il rischio senza exploit detail.

## 14. Agentic authoring

Un agente può ricercare, proporre e controllare documenti, ma la sua produzione è soggetta agli stessi owner e gate del codice. Deve distinguere fonte da inferenza, non inventare citazioni, versioni o conformità e non usare dati sanitari reali nel prompt.

L'agente non approva il proprio testo, non altera i requisiti per eliminare una contraddizione e non sostituisce Legal, DPO, Clinical Safety o Architecture. La review verifica anche link e claim contro le fonti originali.

## 15. Deprecazione eccezioni e retention

Un documento superato resta raggiungibile con stato, motivo, data e link al successore. Link interni vengono aggiornati nella stessa change. Le eccezioni indicano requisito derogato, rischio, compensating control, owner, approvatore e scadenza; non vengono incorporate come nuova normalità senza decisione.

ADR, evidence di release, audit, conformance e documenti contrattualmente rilevanti seguono la retention legale e del cliente. La cancellazione è autorizzata e auditata; legal hold prevale sulla lifecycle policy.

## 16. Automazione CI

Il pipeline documentale verifica almeno:

- file Markdown decodificabili in UTF-8;
- H1 unico, heading e code fence coerenti;
- link locali e anchor;
- termini vietati come placeholder e stati provvisori nei documenti attivi;
- presenza di metadata e data fonti per le classi normative;
- duplicati o ID requisito e ADR mancanti;
- riferimenti a file rinominati;
- incongruenze note su SLO, RTO, RPO, versioni e cardinalità;
- secret e pattern PHI accidentali.

Il lint automatico segnala, mentre il reviewer decide la correttezza clinica e architetturale.

## 17. Casi d'uso di accettazione

### Aggiornamento FHIR

Una nuova versione dell'Implementation Guide modifica un profilo. L'owner aggiorna standard, connector, contract, fixture e compatibility matrix; la release conserva il profilo precedente per N e N-1. La fonte ufficiale e la data sono registrate.

### Incidente di failover

Il post-incident review scopre che l'ordine di restore nel runbook è errato. La corrective action modifica runbook, architettura DR e test; il drill successivo produce evidence e chiude l'azione.

### Requisito nazionale

Un cliente richiede un flusso nazionale. La matrice paese lo separa dalla baseline UE, identifica l'autorità e non generalizza la regola a tutti i tenant.

### Documento generato da agente

La CI rileva un link ufficiale non valido e un'affermazione senza versione. La merge resta bloccata finché l'owner verifica la fonte e un reviewer indipendente approva.

## 18. Metriche e gate

Metriche: percentuale documenti con owner e review valida, link failure, stale document, requisito senza test, ADR senza implementazione, runbook drillato, finding documentale riaperto e tempo di correzione.

Gate di completezza:

- nessun file attivo con placeholder, stato provvisorio o sezione promessa;
- tutti i link locali e code fence validi;
- fonti primarie e data presenti dove richiesto;
- requisiti critici tracciati a test ed evidence;
- ADR, SLO, RTO, RPO e support matrix coerenti;
- owner e approvazioni definiti;
- documento di release indicizzato e firmato.

## 19. Fonti ufficiali

Consultate il 5 settembre 2026:

- [ISO IEC IEEE 42010:2022 Architecture description](https://www.iso.org/standard/74393.html)
- [NIST Secure Software Development Framework 1.1](https://csrc.nist.gov/pubs/sp/800/218/final)
- [SLSA specification 1.2](https://slsa.dev/spec/v1.2/)
- [RFC 2119 Key words for requirements](https://www.rfc-editor.org/rfc/rfc2119)
