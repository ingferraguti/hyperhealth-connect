# ADR-001 — Separazione Control Plane / Data Plane

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Architecture  
Decisione collegata: FR-TEN-003, NFR-BC-001, NFR-SCL-001

## Contesto

HHC deve governare molte aziende e facility senza trasferire necessariamente payload clinici a un servizio centrale. Una dipendenza sincrona dal piano di controllo allargherebbe il failure domain, violerebbe esigenze di residency e impedirebbe continuità locale.

## Decisione

Control Plane e Data Plane sono deployable e trust boundary distinti. Il Control Plane possiede tenant registry, asset, policy, approval e desired state. Ogni Runtime Cell nel Data Plane esegue flow, connector, durabilità, retry, audit buffer e observed state. La distribuzione avviene mediante bundle immutabili, firmati, versionati e validati; il runtime usa pull autenticato e mantiene l'ultima configurazione valida.

Il Control Plane non riceve payload clinici per default. Le API tra piani trasportano metadata minimizzati, health, evidence e riferimenti opachi. Una Runtime Cell continua i flow già autorizzati per almeno 24 ore durante isolamento del Control Plane, entro scadenza delle policy e capacità locale.

## Conseguenze

- rollout, identity e osservabilità devono funzionare attraverso failure di rete;
- policy nuove non sono applicabili finché la cella non le verifica e attiva atomicamente;
- revoche urgenti richiedono canale e procedura operativa dedicati;
- la disponibilità del fast path non coincide con quella del Control Plane;
- test obbligatori: isolamento 24 ore, bundle tamper, stale policy, reconnect, rollback e reconciliation.

## Alternative respinte

- runtime dipendente da API centrali per ogni evento: latenza e failure domain non accettabili;
- piattaforma completamente decentralizzata: governance, audit e fleet management incoerenti;
- payload nel Control Plane per comodità: incompatibile con minimizzazione e residency di molte installazioni.

## Collegamenti

- [System context](../architecture/system-context.md)
- [Scalabilità e resilienza](../architecture/scalability-and-resilience.md)
- [Alta disponibilità e DR](../deployment/high-availability-and-dr.md)
