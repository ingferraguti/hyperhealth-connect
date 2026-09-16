# P1-0104 — OIDC, ruoli minimi e workload identity separata

| Campo | Valore |
|---|---|
| Work package | WP1-01 |
| Stato | IMPLEMENTED |
| Data di completamento | 14 settembre 2026 |
| Ambito | Control Plane, vertical slice `GET /api/v1/endpoints/{endpointId}` |
| Owner | Platform Engineering / Identity & Access |
| Decisioni | ADR-009, ADR-025 |
| Requisiti | FR-TEN-001, FR-TEN-002, FR-TEN-003, NFR-SEC-001 |

## 1. Obiettivo e risultato

P1-0104 introduce il trust boundary che mancava al percorso scoped realizzato da P1-0103. Il Control Plane è ora un OAuth 2.0 Resource Server OIDC-capable che:

1. verifica crittograficamente access token JWT tramite JWKS;
2. accetta esclusivamente `RS256`, issuer HTTPS configurato e lifetime valido;
3. applica audience e client autorizzato distinti a identità umane e workload;
4. usa un vocabolario chiuso di ruoli, separato per classe di principal;
5. crea `VerifiedFacilityScope` soltanto da claim firmati e canonici;
6. sostituisce e non eredita eventuali request attribute o header di scope preesistenti;
7. autorizza l'endpoint inventory tramite capability interna, non tramite nome del gruppo IdP;
8. risponde con Problem Details uniforme e privo di token, claim, chiavi, SQL o dati di risorse negate;
9. resta deny-by-default se OIDC non è abilitato o se la configurazione di trust è incompleta.

L'incremento non rendeva concluso WP1-01 né Gate G2. Le secret reference scoped sono state successivamente consegnate da [P1-0105](phase-1-p1-0105-implementation.md) e la API Endpoint modificabile da [P1-0106](phase-1-p1-0106-implementation.md); audit journal e matrice negativa estesa restano P1-0107 e P1-0108.

## 2. Trust boundary e flusso della richiesta

```text
Authorization: Bearer <access token>
              │
              ▼
estrazione bearer token
              │
              ▼
firma RS256 + JWKS ── failure ──► 401 uniforme
              │
              ▼
issuer + exp + nbf
              │
              ▼
principal type + audience + azp + ruolo + scope
              │
              ▼
AuthenticatedIdentity immutabile
              │
              ├── ruolo→capability ── deny ──► 403 uniforme
              │
              ▼
VerifiedFacilityScope da claim firmati
              │
              ▼
controller → service → query tenant/facility/endpoint scoped
```

Il filtro di propagazione rimuove sempre l'attributo `VerifiedFacilityScope` già presente prima di leggere il `SecurityContext`. Solo un `HhcJwtAuthenticationToken` autenticato può reinstallarlo. `X-Tenant-ID`, `X-Facility-ID`, query parameter, cookie e body non partecipano alla decisione.

La firma e i validator vengono eseguiti prima del converter dei ruoli. Un token non valido non produce principal, authority o scope parziali. Il repository conserva inoltre i predicati SQL di P1-0103: il controllo HTTP non diventa l'unica barriera contro accessi cross-facility.

## 3. Contratto dei token

Sono accettati access token, non ID token. Il contratto minimo è:

| Campo | Tipo | Regola |
|---|---|---|
| header `alg` | stringa | esattamente `RS256`; nessun algoritmo simmetrico o `none` |
| `iss` | URI | uguaglianza esatta con `HHC_OIDC_ISSUER_URI`; HTTPS |
| `sub` | stringa | stabile e non vuota; non viene usato come patient identifier |
| `iat` | NumericDate | obbligatorio; non oltre 60 secondi nel futuro e base per il limite massimo del lifetime |
| `exp` | NumericDate | obbligatorio, successivo a `iat`, non scaduto e dentro il lifetime del profilo |
| `nbf` | NumericDate, se presente | il token non è accettato prima dell'istante valido |
| `aud` | stringa/array standard | contiene l'audience del profilo e non quella dell'altro profilo |
| `azp` | stringa | appartiene all'allowlist client del profilo |
| `hhc_principal_type` | stringa | `HUMAN` oppure `WORKLOAD` |
| `hhc_roles` | array di stringhe | non vuoto, senza duplicati, solo ruoli noti e compatibili col principal |
| `hhc_tenant_id` | stringa | ID canonico `t-<uuid>` non nil |
| `hhc_facility_id` | stringa | ID canonico `f-<uuid>` non nil |

Audience doppia human+workload è rifiutata anche quando contiene l'audience apparentemente corretta. Questa regola evita che un token multi-audience cancelli il confine tra i due profili. `azp` è verificato oltre ad `aud`, impedendo a un altro client dello stesso issuer di usare il Control Plane come confused deputy.

La relazione fra tenant e facility viene verificata nuovamente dal lookup scoped sul Platform DB. Il token dichiara il grant firmato, ma non può creare una relazione di inventory inesistente o spostare una facility.

## 4. Profili separati

### 4.1 Identità umana

| Proprietà | Baseline P1-0104 |
|---|---|
| principal type | `HUMAN` |
| audience default | `hhc-control-plane-human` |
| client autorizzato default | `hhc-control-plane-ui` |
| grant da configurare sull'IdP | Authorization Code con PKCE; MFA/assurance secondo policy enterprise |
| credenziale applicativa | nessun client secret nel browser |
| scope | una facility per token nel vertical slice |

### 4.2 Workload identity

| Proprietà | Baseline P1-0104 |
|---|---|
| principal type | `WORKLOAD` |
| audience default | `hhc-control-plane-workload` |
| client autorizzato default | `hhc-runtime-agent` |
| grant da configurare sull'IdP | Client Credentials o Private Key JWT, senza account umano |
| credenziale | distinta per workload/deployment scope, esterna al repository e ruotabile |
| scope | una facility per token nel vertical slice |

Le allowlist client dei due profili devono essere disgiunte e le audience devono essere differenti; l'applicazione non parte con OIDC abilitato se l'invariante è violato. Un ruolo workload in un token umano, o viceversa, rende invalido l'intero token. Nessuna service identity eredita privilegi dall'utente che ha creato o distribuito il workload.

In qualification e produzione il workload client deve inoltre usare credenziali asimmetriche o mTLS/sender-constrained token secondo il profilo di rischio. P1-0104 implementa la verifica lato Resource Server e la separazione logica. P1-0105 ha successivamente aggiunto reference e binding opachi senza valori; provisioning nel provider, resolver runtime e rotazione automatica end-to-end restano nei work item successivi e non sono dichiarati come già consegnati.

## 5. Ruoli e capability minime

Il mapper non traduce automaticamente `scope`, `groups`, realm role o claim arbitrari in authority Spring. Il claim privato `hhc_roles` deve essere emesso da un mapper IdP governato.

| Ruolo HHC | Principal | `HHC_INVENTORY_READ` | Limite |
|---|---|---:|---|
| `FacilityOperator` | human | sì | sola facility del token |
| `Auditor` | human | sì | lettura metadata; nessun payload o secret |
| `FlowDeveloper` | human | no | autenticato, ma riceve 403 sull'inventory read |
| `RuntimeAgent` | workload | sì | sola facility del token; nessun privilegio umano |

Il ruolo è un input RBAC; la route autorizza una capability interna. Aggiungere un ruolo non concede implicitamente accesso: occorre una modifica esplicita, testata e riesaminata della matrice ruolo→capability. `TenantSecurityAdmin`, super-admin permanenti e accesso PHI non sono introdotti in questo incremento.

Per la futura API completa, grant multi-facility e organization-wide non saranno rappresentati da header o wildcard nel token. Saranno assegnazioni server-side versionate, con token breve che identifica il principal e un policy check per resource/action. Il token a singola facility adottato qui riduce il blast radius e resta compatibile con quel modello.

## 6. Configurazione

| Variabile | Default | Vincolo |
|---|---|---|
| `HHC_OIDC_ENABLED` | `false` | se `false`, health resta disponibile e tutte le altre route sono deny-all |
| `HHC_OIDC_ISSUER_URI` | vuoto | URI HTTPS assoluto, obbligatorio se enabled |
| `HHC_OIDC_JWK_SET_URI` | vuoto | URI HTTPS assoluto, obbligatorio se enabled |
| `HHC_OIDC_JWK_CONNECT_TIMEOUT` | `2s` | tra 100 ms e 10 s |
| `HHC_OIDC_JWK_READ_TIMEOUT` | `2s` | tra 100 ms e 10 s |
| `HHC_OIDC_HUMAN_AUDIENCE` | `hhc-control-plane-human` | diversa dalla workload audience |
| `HHC_OIDC_HUMAN_AUTHORIZED_PARTIES` | `hhc-control-plane-ui` | lista separata da workload |
| `HHC_OIDC_HUMAN_MAX_TOKEN_LIFETIME` | `10m` | tra 1 minuto e 1 ora; rifiuto se `exp-iat` lo supera |
| `HHC_OIDC_WORKLOAD_AUDIENCE` | `hhc-control-plane-workload` | diversa dalla human audience |
| `HHC_OIDC_WORKLOAD_AUTHORIZED_PARTIES` | `hhc-runtime-agent` | lista separata da human |
| `HHC_OIDC_WORKLOAD_MAX_TOKEN_LIFETIME` | `5m` | tra 1 minuto e 1 ora; rifiuto se `exp-iat` lo supera |

Le liste sono configurabili come valori separati da virgola. I deployment manifest non devono contenere client secret o private key; questi arriveranno esclusivamente da secret manager/workload identity binding. `issuer-uri` e `jwk-set-uri` sono separati intenzionalmente: il decoder non effettua discovery OIDC durante lo startup, così un restart del Control Plane con IdP temporaneamente indisponibile non dipende da una chiamata di discovery.

L'ambiente deve fornire una truststore TLS qualificata, DNS resiliente e clock sincronizzato. URI HTTP, allowlist vuote, duplicati, audience condivisa o client presente in entrambi i profili impediscono l'avvio del boundary abilitato.

Il clock skew accettato per `iat` è limitato a 60 secondi. Un token emesso oltre tale finestra futura viene rifiutato anche se `exp-iat` rispetta il TTL; la tolleranza non estende il lifetime configurato.

## 7. Comportamento operativo, HA e continuità

- Il Resource Server è stateless: nessuna sessione HTTP e nessun affinity requirement; può scalare orizzontalmente dietro load balancer.
- La protezione CSRF rimane attiva; il solo endpoint consegnato è safe/read-only. Una futura mutazione API dovrà superare sia bearer authorization sia il profilo anti-forgery esplicitamente qualificato, senza introdurre session affinity.
- Le chiavi JWKS sono recuperate con timeout di connessione e lettura bounded; Spring Security/Nimbus gestisce cache e refresh delle chiavi. La durata effettiva della cache e la rotazione devono essere provate nell'ambiente qualificato.
- Un `jwk-set-uri` esplicito elimina la discovery sincrona allo startup, ma il primo token con `kid` non in cache richiede il JWKS endpoint. Se questo è indisponibile il token fallisce chiuso; non si accetta una firma non verificabile.
- Token già firmati con una chiave in cache possono continuare a essere verificati durante un outage breve dell'IdP; non è una garanzia di disponibilità illimitata e non sostituisce IdP HA, DNS HA e runbook di rotazione.
- Token brevi limitano la finestra di revoca. Revoca client/user, logout globale, event-based revocation o introspection non sono simulati: la strategia concreta va qualificata contro l'IdP enterprise e completata nella matrice G2.
- Il Data Plane continua a usare bundle già autorizzati secondo ADR-001; la disponibilità OIDC del Control Plane non deve diventare una dipendenza sincrona del traffico clinico.
- 401 e 403 impostano `Cache-Control: no-store` e correlation ID casuale; 401 include `WWW-Authenticate: Bearer`. I dettagli interni del validator non attraversano il boundary.

### Runbook minimo per rotazione

1. pubblicare la nuova chiave nel JWKS mantenendo la precedente;
2. attendere propagazione e validare token firmati con entrambe le chiavi su tutte le repliche;
3. cambiare la chiave di firma dell'issuer;
4. attendere il massimo TTL dei token e la finestra di clock skew approvata;
5. rimuovere la vecchia chiave;
6. verificare che un token vecchio sia negato e che i deny siano osservabili senza PHI.

La rimozione anticipata può causare outage; una sovrapposizione indefinita prolunga il rischio della chiave precedente. Tempi e approvazioni sono parametri della runbook dell'IdP qualificato.

## 8. Matrice di verifica consegnata

| Caso | Esito atteso | Livello |
|---|---|---|
| firma RSA valida, issuer/lifetime/claim validi | token decodificato | decoder reale |
| firma con chiave non trusted | 401 / `JwtException` | decoder reale + HTTP |
| issuer diverso | 401 / `JwtException` | decoder reale |
| token scaduto | 401 / `JwtException` | decoder reale + HTTP |
| `iat`/`exp` assenti, invertiti o lifetime eccessivo | deny | validator |
| `iat` oltre 60 secondi nel futuro | deny | validator con clock deterministico |
| audience errata o del profilo opposto | 401 | validator + HTTP |
| audience human e workload insieme | deny | validator |
| `azp` non allowlisted o dell'altro profilo | deny | validator |
| ruolo workload su principal umano | 401 | validator + HTTP |
| ruolo umano su principal workload | deny | validator |
| ruolo sconosciuto, duplicato o claim non-array | deny | validator |
| tenant/facility non canonici | deny prima del controller | validator |
| `FacilityOperator` human valido | 200 e scope firmato | HTTP integration |
| `RuntimeAgent` workload valido | 200 e scope firmato | HTTP integration |
| `FlowDeveloper` autenticato | 403 | HTTP integration |
| nessun bearer token | 401 uniforme | HTTP integration |
| header/request attribute di scope spoofato | ignorato e sovrascritto | HTTP integration |
| OIDC disabilitato | API deny-all | application context + HTTP |

I test usano identità, chiavi e ID sintetici generati a runtime. Non contattano un IdP pubblico e non contengono credenziali persistenti. La suite con Keycloak di riferimento, revoca, rollover JWKS remoto, outage, multi-replica e matrice cross-layer completa rimane un deliverable di qualification P1-0108/G2.

## 9. Casi d'uso sanitari europei 2026

### UC-ID-01 — Operatore di una facility ospedaliera

Un operatore autenticato dall'IdP aziendale usa il client human e riceve `FacilityOperator` per la facility A. Legge l'endpoint LIS della facility A. Se sostituisce gli header con tenant/facility B, il servizio rimuove i valori spoofati, usa i claim firmati A e la query scoped non legge B. Un endpoint B, anche se l'ID è noto, rimane indistinguibile da una risorsa assente.

### UC-ID-02 — Runtime Agent di una cella on-premise

La cella della facility A usa una service identity dedicata, audience workload e ruolo `RuntimeAgent`. Non usa credenziali personali né il client browser. Un token rubato e presentato al profilo human fallisce per audience, `azp` e namespace di ruolo; un token della cella A non cambia facility tramite header.

### UC-ID-03 — Auditor multi-azienda a privilegi ridotti

L'auditor riceve un token breve per una facility e può leggere soltanto metadata inventory non sensibili. Il ruolo non abilita raw payload, secret o export. L'estensione organization-wide richiederà assegnazioni e policy server-side nei work item successivi, evitando un token wildcard ad alto blast radius.

### UC-ID-04 — Flow developer senza privilegio operativo

Un `FlowDeveloper` autenticato tenta di leggere l'inventory. Firma, issuer e scope sono validi, ma il mapper non assegna `HHC_INVENTORY_READ`; la route restituisce 403 senza invocare il controller. L'autenticazione non equivale ad autorizzazione.

### UC-ID-05 — IdP indisponibile durante attività clinica

Il Control Plane nega i token per cui non può ottenere una chiave mancante e blocca le nuove operazioni amministrative. Le Runtime Cell continuano i flow clinici già approvati usando bundle locali firmati. Il recupero dell'IdP non richiede riavvio del Data Plane; il Control Plane riprende dopo disponibilità JWKS e verifica delle chiavi.

### UC-ID-06 — Rotazione chiave coordinata su più aziende

L'identity team pubblica una nuova chiave mantenendo overlap, valida token sulle repliche e solo dopo il TTL rimuove la vecchia chiave. Un errore di rollout non autorizza downgrade a `alg=none`, HS256 o skip della firma. Facility e tenant restano nei claim verificati durante l'intera rotazione.

## 10. Evidenze e file

Implementazione:

- `platform/control-plane/src/main/java/io/hyperhealth/connect/controlplane/security/`;
- `platform/control-plane/src/main/resources/application.yml`;
- `platform/control-plane/pom.xml`.

Test:

- `OidcSecurityPropertiesTest`: invarianti di configurazione e fail-closed;
- `HhcOidcTokenValidatorTest`: claim, profili, ruoli e capability;
- `OidcJwtDecoderTest`: firma reale, issuer, lifetime e confused deputy;
- `OidcSecurityWebIntegrationTest`: boundary HTTP end-to-end;
- `OidcDisabledSecurityWebIntegrationTest`: deny-all di default.

## 11. Rischi residui e non-obiettivi

| Tema | Stato dopo P1-0104 | Owner/work item |
|---|---|---|
| secret/private key workload | reference scoped/binding opaco consegnati da P1-0105; valore e provisioning provider restano esterni | runtime/deployment qualification |
| inventory Endpoint CRUD e grant | successivamente consegnati | P1-0106 |
| audit append-only di allow/deny | non ancora consegnato; correlation disponibile | P1-0107 |
| matrix completa ogni endpoint/layer | subset OIDC critico consegnato | P1-0108 / G2 |
| revoca near-real-time | dipende dal profilo IdP e TTL | Identity & Access / G2 |
| sender constraint mTLS/DPoP | richiesto secondo rischio, non simulato | Security Architecture / qualification |
| IdP/JWKS HA e DR | contratto e failure mode definiti, topologia da qualificare | SRE / G2, M5 |
| audit indipendente | errori sicuri, ma journal rinviato | P1-0107, WP1-10 |

Non sono stati introdotti SAML nel Resource Server, provisioning SCIM, login UI, sessioni browser, break-glass, policy engine generale, PHI access o amministrazione Keycloak. L'IdP di produzione può essere diverso da Keycloak purché emetta il contratto OIDC e superi gli stessi test.

## 12. Fonti ufficiali

Fonti verificate il **14 settembre 2026**:

- [Spring Security 7.1.1 — JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html): dipendenze resource-server/JOSE, validazione issuer e timestamp, audience validator, rotazione JWKS e configurazione `jwk-set-uri`;
- [Spring Security — OAuth 2.0 Resource Server multi-tenancy](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/multitenancy.html): pattern per trust e tenant resolution; HHC mantiene un issuer qualificato per deployment e scope firmato, senza resolver scelto da header client;
- [Spring Security 7.1.1 release](https://github.com/spring-projects/spring-security/releases/tag/7.1.1): versione gestita dalla baseline Spring Boot 4.1.1;
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0-final.html): issuer, subject, audience e authorized party;
- [RFC 9700 — Best Current Practice for OAuth 2.0 Security](https://www.rfc-editor.org/rfc/rfc9700.html): binding degli access token alle risorse, client authentication e mitigazioni correnti;
- [RFC 8725 — JSON Web Token Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725.html): allowlist algoritmi, validazione issuer/audience e regole di token typing;
- [Keycloak Server Administration Guide](https://www.keycloak.org/docs/latest/server_admin/): service accounts/client credentials, client role scope e audience mapper;
- [Keycloak 26.7.3 release](https://github.com/keycloak/keycloak/releases/tag/26.7.3): versione del provider di test/conformance registrata in ADR-025.

Le fonti definiscono protocollo e comportamento dei componenti. Claim privati, ruoli, capability, scope e policy fail-closed sono il contratto HHC qui specificato.
