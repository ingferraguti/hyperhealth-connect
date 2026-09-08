# Toolchain baseline della Fase 0

Stato: qualificata 1.0  
Ultimo aggiornamento: 8 settembre 2026  
Owner: Developer Experience e Platform Engineering

## Versioni vincolanti

| Elemento | Baseline | Meccanismo di pin |
|---|---|---|
| Java | 21 LTS | Maven Enforcer `[21,22)`, CI Temurin SemVer `21.0.11+10.0.LTS`, runtime image `21.0.11+10` per digest |
| Maven | 3.9.16 | Wrapper `only-script` e SHA-256 della distribuzione |
| Spring Boot | 4.1.1 | BOM nel parent POM |
| Apache Tomcat Embedded | 11.0.25 | override coordinato di tutti i moduli Tomcat gestiti dalla BOM Spring Boot |
| Apache Camel | 4.22.0 LTS | Camel Spring Boot BOM |
| SpotBugs Maven | 4.10.3.0 | plugin version |
| FindSecBugs | 1.14.0 | plugin dependency version |
| CycloneDX Maven | 2.9.1 | plugin version, schema 1.6 |
| Build container | Maven 3.9.16 + Temurin 21 | manifest digest nel gate PowerShell |
| Runtime container | Temurin 21.0.11+10 Jammy | manifest digest nei Dockerfile |

Il wrapper verifica il checksum della distribuzione. Le action GitHub sono pin-nate a commit SHA, le immagini esterne a digest e le dipendenze critiche hanno owner in `governance/dependencies.yml`. L’aggiornamento avviene via pull request dedicata con release note, SCA, test completi, prova di riproducibilità, build/smoke container e aggiornamento SBOM. Un digest non viene cambiato silenziosamente mantenendo la stessa review.

Spring Boot 4.1.1 gestisce Tomcat 11.0.24 nella propria BOM. La baseline forza in modo coordinato `tomcat-annotations-api`, `tomcat-jdbc`, `tomcat-jsp-api` e i moduli embedded `core`, `el`, `jasper` e `websocket` alla 11.0.25. L'override dell'intera famiglia evita versioni miste e corregge i finding pubblicati il 25 agosto 2026, inclusi CVE-2026-65182, CVE-2026-65905 e CVE-2026-68525. Maven Enforcer verifica upper bound e convergenza; Trivy blocca ogni regressione HIGH o CRITICAL con correzione disponibile.

La build usa timestamp fisso negli archivi e ha prodotto due JAR Integration Envelope identici byte-for-byte. Il gate locale non disabilita TLS: su workstation con ispezione HTTPS importa temporaneamente nel container la CA già fidata dal sistema, memorizzata in un file ignorato da Git.

## Compatibilità

Il codice è compilato con `--release 21`. L’immagine Jammy è multi-arch e ha superato uno smoke test x86-64 con UID 10001, filesystem read-only e readiness probe. L’immagine candidata UBI 10 è stata scartata perché richiedeva x86-64-v3: il requisito non era compatibile con una parte dell’hardware enterprise. Arm64 e altre architetture dichiarate nel manifest richiedono comunque test nativi prima di essere incluse nella support matrix.

## Fonti ufficiali verificate

- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html) e [repository Spring Boot](https://github.com/spring-projects/spring-boot), consultati il 7 settembre 2026.
- [Apache Camel 4.22.0 release](https://camel.apache.org/releases/release-4.22.0/) e [release schedule](https://camel.apache.org/releases/), consultati il 7 settembre 2026.
- [Apache Tomcat 11 security advisories](https://tomcat.apache.org/security-11) e [changelog 11.0.25](https://tomcat.apache.org/tomcat-11.0-doc/changelog.html), consultati l'8 settembre 2026.
- [Apache Maven release history](https://maven.apache.org/docs/history.html) e [Maven Wrapper](https://maven.apache.org/wrapper/), consultati il 7 settembre 2026.
- [SpotBugs Maven plugin releases](https://github.com/spotbugs/spotbugs-maven-plugin/releases) e [FindSecBugs releases](https://github.com/find-sec-bugs/find-sec-bugs/releases), consultati il 7 settembre 2026.
- [CycloneDX Maven plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin) e [CycloneDX specification](https://cyclonedx.org/specification/overview/), consultati il 7 settembre 2026.
- [Eclipse Temurin official image](https://hub.docker.com/_/eclipse-temurin) e [Adoptium container repository](https://github.com/adoptium/containers), manifest verificati il 7 settembre 2026.
