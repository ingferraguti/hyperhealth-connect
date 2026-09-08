# Politica per dati di test sintetici

Stato: vincolante 1.0  
Ultimo aggiornamento: 7 settembre 2026  
Owner: Quality Engineering e DPO

Nei sorgenti, branch, issue, CI, demo, benchmark ed evidence bundle sono ammessi esclusivamente dati creati artificialmente e marcati `HHC-SYNTHETIC`/`test-mode`. È vietato anonimizzare “a mano” dati reali: un dato derivato da una persona resta fuori dal repository anche se alcuni campi sono rimossi.

Le fixture sono deterministiche, minimali e prive di free text realistico. Date, nomi, identificativi, accession number, indirizzi, immagini e referti usano namespace riservati al test. I casi edge sono generati da regole, non copiati da incidenti. Gli incidenti vengono riprodotti con schema e proprietà astratte.

## Pipeline obbligatoria

1. generazione da seed versionato;
2. validazione schema/profilo e marker sintetico;
3. scansione secret e denylist di identificativi/host produttivi;
4. redaction test prima dei feature test;
5. retention massima di 30 giorni per output CI e cancellazione automatica;
6. review DPO per nuove classi di fixture o allegati binari.

I log di test non stampano body. Il fallimento registra case ID, hash, regola, versione e posizione strutturale. Un corpus per conformance può essere redistribuito solo se licenza e provenance sono registrate.

Il file machine-readable `test-data/policy.yml` è la fonte eseguibile; questa pagina ne spiega l’intento. La violazione è un release blocker e richiede rotazione immediata se coinvolge un segreto, oltre alla procedura privacy se esiste sospetto dato reale.

