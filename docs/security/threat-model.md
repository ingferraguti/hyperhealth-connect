# Threat model della Engineering Foundation

Stato: approvato per la Fase 0  
Ultimo aggiornamento: 7 settembre 2026  
Owner: Product Security; approvatori: Architecture, DPO, Clinical Safety, SRE

## Scopo e metodo

Il modello copre control plane, runtime worker, connector SDK, envelope, pipeline di build e ambienti di sviluppo. Applica STRIDE ai trust boundary e collega ogni minaccia a un controllo verificabile. È una baseline viva: va aggiornata quando entra un protocollo, un nuovo confine di fiducia, una nuova categoria di dato o una modifica al modello multi-tenant.

## Asset e confini di fiducia

Gli asset di massima criticità sono payload clinici, identificativi, chiavi di pseudonimizzazione, configurazioni firmate, mapping semantici, audit trail, code di messaggi, credenziali macchina e artifact di rilascio. I confini sono: sistema sorgente–connector; connector–runtime; runtime–broker/storage; data plane–control plane; tenant–tenant; facility–facility; ambiente–ambiente; CI–registry; operatore–API amministrativa; HHC–servizi terminologici e sistemi europei esterni.

Il tenant e la facility sono attributi obbligatori dell’envelope e dell’autorizzazione, mai valori dedotti dal payload. Un worker non ottiene accesso globale: riceve lease e policy limitati a un perimetro. L’indisponibilità del control plane non amplia i privilegi e non arresta i flussi già autorizzati finché lease e configurazione firmata restano validi.

## Minacce prioritarie e trattamento

| ID | Scenario | STRIDE | Impatto | Controlli e verifica |
|---|---|---|---|---|
| TM-01 | spoofing di sistema sorgente o workload | S | dati clinici falsi, accesso illecito | mTLS/OIDC workload identity, audience stretta, rotazione; test negativi in integration |
| TM-02 | sostituzione tenant/facility nell’envelope | T/E | disclosure interaziendale | scope immutabile, confronto constant-policy, deny by default; `TenantScopeTest` |
| TM-03 | modifica di configurazioni o mapping | T/R | trasformazione clinica errata | Git review, firma, digest, four-eyes e lineage; gate di semantic release |
| TM-04 | log o trace contenenti PHI/token | I | violazione GDPR e segreto professionale | allowlist degli attributi, redazione, sampling senza body; `SensitiveValueRedactorTest` |
| TM-05 | replay o duplicazione di evento | T/D | doppia prestazione o doppia notifica | event ID, idempotency key, dedup store, offset e audit della decisione |
| TM-06 | input HL7/DICOM/FHIR ostile | T/D/E | parser crash, SSRF, RCE o esaurimento risorse | limiti dimensionali, parser hardened, schema/profile validation, sandbox connector, fuzzing |
| TM-07 | dependency confusion o action compromessa | T/E | artifact malevolo | coordinate allowlist, lock/pin, SHA delle action, SCA, SBOM, provenance e firma |
| TM-08 | abuso dell’API amministrativa | S/E/R | modifica massiva o cancellazione | MFA federata, RBAC/ABAC, separation of duties, immutable audit, step-up per azioni critiche |
| TM-09 | esfiltrazione via metriche o errori | I | leakage a sistemi di osservabilità | cardinalità controllata, codici errore stabili, nessun payload, sink regionali |
| TM-10 | perdita di regione o corruzione backup | D/T | fermo clinico, perdita configurazione | replica, backup immutabile, restore drill, quorum e runbook dichiarato |
| TM-11 | breakout di un connector | E/I | compromissione laterale | container non-root, filesystem read-only, seccomp, egress policy, secret per connector |
| TM-12 | reidentificazione nel percorso OMOP | I | danno agli interessati | pseudonimizzazione separata, purpose binding, accesso analitico governato, small-cell policy |

## Misuse case sanitari 2026

1. Un tecnico della Facility A tenta di rigiocare un evento della Facility B cambiando l’header. Il runtime confronta identity, route scope ed envelope, rifiuta prima del parsing e genera un audit event senza dati clinici.
2. Un messaggio ORU contiene un PDF base64 sovradimensionato. Il connector applica il limite prima della decodifica, mette in quarantena l’hash e conserva soltanto metadati minimali.
3. Un mapping non approvato converte un codice laboratorio con unità diversa. La promozione è bloccata perché manca l’approvazione clinica e il golden corpus mostra uno scostamento.
4. Un account amministrativo valido tenta un export cross-company. La policy richiede ruolo, finalità, tenant esplicito e step-up; l’azione viene negata e correlata nel SIEM.

## Criteri di accettazione

Nessuna minaccia `critical` può essere accettata dal solo team di sviluppo. Deve avere owner, controllo preventivo, detection, evidenza e rischio residuo approvato da Product Security, DPO e Clinical Safety quando pertinente. Test di isolamento e redazione precedono i test funzionali. Un finding critico SAST/SCA/secret scan blocca il merge; le eccezioni sono temporanee, motivate e con scadenza.

## Fonti verificate

- [NIST SP 800-218, Secure Software Development Framework 1.1](https://csrc.nist.gov/pubs/sp/800/218/final), consultato il 7 settembre 2026.
- [OWASP Application Security Verification Standard](https://github.com/OWASP/ASVS), repository ufficiale consultato il 7 settembre 2026.
- [ENISA Threat Landscape](https://www.enisa.europa.eu/publications/enisa-threat-landscape-2025), consultato il 7 settembre 2026.

