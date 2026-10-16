# java_learning

Biblioteca personale di studio su Java e sul suo ecosistema, in tono accademico.
Il lettore è l'utente stesso: un ingegnere senior esperto di Java e Angular.
Rispondi in italiano.

## Inizio di ogni sessione

Al primo messaggio di ogni nuova sessione, prima di qualsiasi altra cosa:
1. elenca le cartelle-argomento già presenti alla radice del repository (tutte le
   directory tranne quelle che iniziano con `.` o `_`), indicando per ciascuna il
   titolo leggibile preso dal suo `README.md` e lo stato del design o del piano in
   `_progetto/`, se esistono;
2. chiedi su quale argomento vuole lavorare oggi: uno di quelli esistenti oppure
   uno nuovo.

## Struttura

- Ogni argomento di studio ha una propria cartella alla radice (nome in
  kebab-case) che contiene tutto: `docs/`, i progetti eseguibili (per esempio
  `backend/` e `frontend/`) e `_progetto/` con design e piano.
- `README.md` alla radice è l'indice di tutti gli argomenti: aggiornalo quando ne
  nasce uno nuovo.
- Documenti in Markdown e in italiano; i termini tecnici restano in inglese.
- Versioni: sempre l'ultima release stabile (mai milestone o preview), da
  verificare al momento della costruzione.
- Node: il default di nvm è la 20; i progetti Angular usano `.nvmrc` con la 24.
