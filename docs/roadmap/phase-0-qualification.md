# Qualification report — Fase 0

Stato: PASS  
Data dell'ultima riesecuzione completa: 8 settembre 2026  
Ambiente: Windows host, Docker Engine 29.6.2, container Linux, Java 21, Maven 3.9.16  
Owner: Program Engineering; gate owner: Architecture, Product Security, SRE, Quality Engineering

## Esito

La Engineering Foundation R0.1 soddisfa gli exit criteria repository-level della Fase 0 ed è autorizzata a entrare nella Fase 1 / Technical MVP. Questa autorizzazione non abilita uso clinico o produzione: i gate di pilot, DPIA di installazione, penetration test, restore drill e conformance restano nelle release successive.

| Controllo | Risultato | Evidenza |
|---|---|---|
| inventario e governance | PASS | CODEOWNERS, branch policy, PR template, operating model |
| ADR fondativi | PASS | ADR-001–ADR-010 `Accepted` |
| ownership dipendenze | PASS | 8 dipendenze critiche con lifecycle, security e license owner |
| security-first | PASS | `security-core` prima dei feature module; tenant/redaction test verdi |
| build e convergenza | PASS | reactor 7 moduli; Maven Enforcer verde |
| test | PASS | 10 eseguiti, 0 failure, 0 error, 0 skipped |
| SAST | PASS | SpotBugs + FindSecBugs, 0 finding medium-or-higher |
| SBOM | PASS | CycloneDX 1.6, 122 componenti aggregati |
| firma locale | PASS | JAR copiato, firmato EC e verificato; chiave privata effimera eliminata |
| provenance CI | READY | keyless Cosign e GitHub build attestation sul branch protetto |
| environment | PASS | Compose valido; overlay dev/integration/conformance/performance renderizzati |
| container baseline | PASS | base Jammy multi-arch per digest, UID 10001, filesystem read-only; readiness smoke su entrambe le immagini |
| assurance | PASS | threat model, privacy review, hazard log, BIA, audit schema |
| tracciabilità | PASS | requirement–ADR–control–test–evidence–owner |

## Digest dell’esecuzione

- Control plane JAR SHA-256: `99ef1c6a70b65a47abb697ea399fdc80db9b8d39523b78d4b4ca5e69c545442b`.
- Runtime worker JAR SHA-256: `daaabe104c7907eca123e081f32d596c9716af0ca9e40d7b8f36c18ef4b11fe3`.
- SBOM aggregata SHA-256: `144688e0048777b88382110b76ea5f5db3af8764d28faa8b2b03337ab5bc0b99`.
- Prova riproducibilità Integration Envelope, due build isolate: `0228eedcd37d4556ee910760ed96c140d2a85dbdeb75d5bc97171b7602490c33`, identiche byte-for-byte.

I digest sono riferiti all’esecuzione locale sopra indicata e cambiano a ogni modifica intenzionale. Il report machine-readable effimero è `target/phase0-evidence/qualification-report.json`; viene rigenerato dal gate, non versionato.

## Eccezioni e limiti dichiarati

Non esiste ancora un remote Git configurato: la regola server-side di branch protection non può essere applicata o provata sulla workstation. Il file `.github/branch-protection.yml` è la policy canonica da importare nel forge; fino ad allora la protezione è dichiarativa. Analogamente, firma keyless e attestazione OIDC richiedono l’esecuzione sul branch `main` ospitato. Il gate locale dimostra firma e verifica senza conservare chiavi.

Le warning del validatore CycloneDX riguardano keyword meta-schema annotate dalla libreria di validazione; la BOM 1.6 è stata generata e validata. Le warning Mockito/JDK provengono dal test starter e non indicano failure; verranno eliminate prima del passaggio alla futura policy JDK che impedirà il dynamic attach.

Lo smoke test ha inoltre escluso la prima candidata UBI 10, che richiedeva x86-64-v3 e non era compatibile con l'host di qualification. La baseline definitiva usa Temurin Jammy multi-arch per mantenere compatibilità con hardware enterprise x86-64 precedente; la matrice CPU/architetture resta parte della qualification di ogni release.

## Comandi di riproduzione

Su Windows: `./scripts/phase0-gate.ps1`. Su Linux: `./scripts/phase0-gate.sh`. Per una workstation con TLS inspection, una CA organizzativa può essere esportata localmente in `.mvn/workstation-ca.cer`; il file è ignorato da Git e non entra negli artifact. Non è consentito disabilitare la verifica TLS.
