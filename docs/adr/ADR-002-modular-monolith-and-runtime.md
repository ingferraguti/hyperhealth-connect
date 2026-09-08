# ADR-002 — Modular monolith e runtime indipendente

Stato: Accepted  
Data: 5 settembre 2026  
Owner: Architecture e Platform Engineering

## Contesto

Un'adozione prematura di microservizi aumenterebbe coordinamento, failure mode e costo operativo, mentre un monolite senza confini renderebbe impossibile scalare componenti critici e isolare il runtime clinico.

## Decisione

Il Control Plane nasce come monolite modulare con bounded context, API interne esplicite, ownership dei dati e regole ArchUnit equivalenti. Il Data Plane Runtime è un deployable separato fin dall'MVP. Connector ad alto rischio o di terze parti possono essere processi isolati o Runtime Cell dedicate.

Un modulo viene estratto solo se almeno uno dei seguenti segnali è misurato e persistente: profilo di scala indipendente, failure isolation necessaria, release cadence incompatibile, requisito di data locality, ownership autonoma matura o limite di risorse non risolvibile modularmente. L'estrazione richiede ADR, contratto versionato, datastore ownership, SLO, runbook e migrazione reversibile.

## Conseguenze

- vietato accesso diretto tra tabelle di bounded context;
- eventi interni non diventano automaticamente contratti pubblici;
- pipeline può costruire/testare moduli separatamente pur rilasciando un'unità;
- semplicità MVP senza sacrificare confini enterprise;
- test architetturali bloccano cicli e dipendenze vietate.

## Alternative respinte

- microservizi per ogni capability: costo e consistenza distribuita prematuri;
- unico monolite Control+Data Plane: failure domain e residency inaccettabili;
- estrazione guidata da dimensione del codice: metrica non correlata al bisogno operativo.

## Collegamenti

- [Componenti](../architecture/components.md)
- [Pratiche di sviluppo](../development/engineering-practices.md)
- [Strategia MVP-enterprise](../roadmap/mvp-to-enterprise.md)
