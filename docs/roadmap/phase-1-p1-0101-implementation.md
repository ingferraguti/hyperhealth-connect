# P1-0101 — Identificativi immutabili e gerarchia inventory

| Campo | Valore |
|---|---|
| Stato | IMPLEMENTED |
| Work package | WP1-01 |
| Requisito primario | FR-TEN-001 |
| Decisione architetturale | [ADR-009](../adr/ADR-009-identity-and-tenancy.md) |
| Data di implementazione | 11 settembre 2026 |
| Ambito dati | esclusivamente sintetico |

## Risultato

Il Control Plane dispone del modello di dominio iniziale per l'inventory enterprise. La gerarchia normativa è rappresentata senza livelli opzionali:

```text
Tenant → Organization → Facility → Application → Endpoint
```

Ogni nodo contiene un identificativo fortemente tipizzato e un riferimento immutabile al proprio parent. Il path di un Endpoint contiene quindi sempre Tenant, Organization, Facility e Application. Un oggetto con un livello intermedio mancante o `null` non può essere costruito.

La Runtime Cell è modellata separatamente come failure/scaling domain. Non è una figlia artificiale di Facility o Endpoint: possiede un ID immutabile e un insieme non vuoto, difensivamente copiato, di scope autorizzati. L'assegnazione a un ancestor include i relativi descendant; sibling e tenant non assegnati restano esclusi. Le installazioni multiazienda sono rappresentabili soltanto mediante assegnazioni cross-tenant esplicite, così che le successive policy possano validarle e auditarle.

## Contratto degli identificativi

Gli ID sono value object Java `record`, basati su UUID a 128 bit generati senza dati clinici, nomi o chiavi locali. Il formato esterno canonico include il tipo:

| Risorsa | Prefisso |
|---|---|
| Tenant | `t-` |
| Organization | `o-` |
| Facility | `f-` |
| Application | `a-` |
| Endpoint | `ep-` |
| Runtime Cell | `rc-` |

I prefissi coincidono con il contratto `hhc-envelope/v1` definito in [Integration Envelope](../canonical-model/integration-envelope.md). Il parser rifiuta prefisso errato, UUID nil, UUID malformato e rappresentazione UUID non canonica. L'immutabilità è strutturale: non esistono setter o operazioni di sostituzione dell'ID e tutti i campi delle entità sono finali tramite `record`. I nomi visualizzati e gli identificativi sanitari locali non partecipano all'identità canonica.

Il divieto persistente di riciclo dopo dismissione richiede tombstone/history e constraint del Platform DB: sarà applicato da P1-0102. P1-0101 non introduce una registry volatile che darebbe una garanzia falsa dopo restart o failover.

## Invarianti implementati

1. ogni ID appartiene a un solo tipo a compile time;
2. la sua forma esterna porta il tipo e consente round-trip deterministico;
3. il valore nil e input non canonici sono vietati;
4. ogni resource gerarchica ha parent obbligatorio, eccetto Tenant;
5. un path conserva l'ancestry completa e immutabile;
6. `contains` verifica uguaglianza o relazione ancestor/descendant per ID, senza affidarsi a display name;
7. Runtime Cell richiede almeno uno scope esplicito;
8. gli scope della Runtime Cell non sono modificabili dal chiamante dopo la costruzione;
9. un'assegnazione Facility non autorizza sibling, ancestor o facility di altro tenant;
10. più tenant/facility sono supportati senza allargamento implicito degli scope.

## Artifact

- package `io.hyperhealth.connect.controlplane.inventory` nel modulo `platform/control-plane`;
- `InventoryId`: sei ID tipizzati, generazione, parsing e forma esterna;
- `InventoryPath`: path gerarchici sealed e controllo ancestor/descendant;
- `Tenant`, `Organization`, `Facility`, `Application`, `Endpoint`: nodi immutabili;
- `RuntimeCell`: associazioni tecniche esplicite e verifica di autorizzazione dello scope;
- test `InventoryIdTest`, `InventoryHierarchyTest` e `RuntimeCellTest`.

## Verifica locale

Comando:

```powershell
.\mvnw.cmd -pl platform/control-plane -am clean verify
```

Esito dell'11 settembre 2026: `BUILD SUCCESS`; 13 test del Control Plane superati, 0 failure, 0 errori, 0 skipped; SpotBugs/FindSecBugs con 0 finding. La qualification definitiva resta subordinata ai required check della pull request.

## Confine deliberato

Sono esclusi da questa modifica schema e migration SQL, repository e cache scoped, OIDC/RBAC, secret reference, API inventory, audit amministrativo, matrice negativa end-to-end, localizzazione e seed. Sono attività P1-0102…P1-0110 e non vengono anticipate per evitare dipendenze o garanzie parziali non qualificate.
