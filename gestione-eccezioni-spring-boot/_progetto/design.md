# Design: Gestione delle eccezioni in Spring Boot

Data: 2026-10-16
Stato: bozza, in attesa di approvazione

---

## 1. Contesto: la biblioteca di studio `java_learning`

`java_learning` è una biblioteca personale di studio su Java e sul suo ecosistema.
Il lettore è uno solo: l'autore stesso, un ingegnere informatico senior con molti
anni di esperienza su Java e Angular. La userà per **studiare, ripassare e scoprire
cose nuove**.

Per questo il tono è **accademico**: spiega il *perché* delle cose (specifiche,
meccanismi interni dei framework, scelte di design e compromessi), non solo il
"come si fa". Non è un tutorial per principianti.

### Regola strutturale (decisa dall'utente)

**Ogni argomento di studio ha una propria cartella alla radice del repository**, che
contiene tutto quello che lo riguarda: documenti, codice di esempio, progetti
eseguibili e documenti di progetto (design e piano).

Ogni argomento offre tre livelli (decisi dall'utente):
1. **Documenti** di approfondimento.
2. **Codice di esempio essenziale** dentro i documenti: frammenti brevi, solo
   quanto basta per chiarire il concetto.
3. **Progetti eseguibili** con cui sperimentare.

Nella radice del repository c'è un `README.md` che fa da indice di tutti gli
argomenti (*scelta mia*).

## 2. L'argomento di oggi

**"Gestione delle eccezioni in Spring-Boot"**, sull'ultima versione stabile.

### Intento (dalle parole dell'utente)

Coprire l'argomento **in modo completo**, con particolare attenzione a:
- come **raccogliere le eccezioni in modo centralizzato**;
- come **inviare una response** che rappresenti l'eccezione catturata;
- come **produrre un messaggio chiaro per l'utente** finale.

Il messaggio viene mostrato da un **frontend Angular**, **in italiano e in inglese**.

### Filo conduttore

Tutta la trattazione segue il percorso di un errore:
**dove nasce → come lo si raccoglie in un punto centrale → come diventa una
risposta HTTP → come diventa un messaggio comprensibile per l'utente, nella sua
lingua.**

## 3. Obiettivi, non-obiettivi, anti-obiettivi

### Obiettivi
- Una trattazione completa e approfondita della gestione delle eccezioni in
  Spring Boot 4.1 / Spring Framework 7 (stack servlet, Spring MVC).
- Un backend eseguibile che dimostra, **con test automatici**, ogni comportamento
  descritto nei documenti.
- Una piccola app Angular che mostra come il client intercetta, interpreta e
  visualizza gli errori in italiano e in inglese.

### Non-obiettivi
- **WebFlux e il mondo reattivo**: escluso esplicitamente dall'utente.
- Un'applicazione "da produzione" completa (deploy, CI, container, persistenza reale).
- Un'interfaccia Angular curata dal punto di vista grafico: deve essere chiara e
  pulita, ma è al servizio della dimostrazione.

### Anti-obiettivi (cosa renderebbe il lavoro un fallimento anche se "funziona")
- Contenuto da tutorial introduttivo, che spiega cose ovvie a un senior.
- Affermazioni senza fondamento: ogni comportamento non ovvio va ricondotto alla
  documentazione ufficiale, al codice sorgente del framework (citando le classi)
  o alla specifica (JLS, RFC).
- API obsolete presentate come attuali. Gli approcci storici (per esempio
  `@ResponseStatus` sulle eccezioni, o la vecchia struttura del JSON di
  `BasicErrorController`) si citano solo come contesto evolutivo, chiaramente
  etichettati come tali.
- Codice d'esempio così ricco da nascondere il concetto che deve illustrare.
- Documenti che descrivono comportamenti che il progetto poi non riproduce, o li
  riproduce in modo diverso.

## 4. Vincoli tecnici

| Elemento | Scelta | Note |
|---|---|---|
| Java | **25 (LTS)** | Già installato (Temurin 25.0.1) |
| Spring Boot | **4.1.x**, ultima patch stabile al momento della costruzione | Verificata 4.1.1 al 2026-10-16; la 4.2 è in milestone e **non** va usata |
| Build backend | **Maven**, con Maven Wrapper | Maven 3.9.15 installato |
| Angular | **22**, ultima patch stabile | |
| Node | **24** | Installata via nvm (v24.22.0) ma **non** è il default (il default è la 20). Aggiungere `.nvmrc` con `24` nella cartella del frontend e indicare `nvm use` nel README |
| Lingua dei documenti | **Italiano**; i termini tecnici restano in inglese (`@ControllerAdvice`, `ProblemDetail`…) | |
| Lingue dell'app | **Italiano e inglese** | |
| Formato documenti | Markdown | Si legge bene nell'IDE e su GitHub |

Sono vietati gli usi di versioni preview o milestone.

## 5. Struttura della cartella dell'argomento

```
java_learning/
├── README.md                                  # indice della biblioteca
└── gestione-eccezioni-spring-boot/
    ├── README.md                              # introduzione, indice dei capitoli, come avviare
    ├── docs/
    │   ├── 01-fondamenti-java.md
    │   ├── 02-meccanismo-spring-mvc.md
    │   ├── 03-gestione-centralizzata.md
    │   ├── 04-problem-detail.md
    │   ├── 05-messaggi-utente-i18n.md
    │   ├── 06-validazione.md
    │   ├── 07-transazioni-rollback.md
    │   ├── 08-casi-di-confine.md
    │   ├── 09-logging-observability.md
    │   └── 10-client-angular.md
    ├── backend/                               # progetto Spring Boot
    ├── frontend/                              # app Angular
    └── _progetto/
        ├── design.md                          # questo documento
        └── piano.md                           # piano di implementazione
```

Il nome della cartella è in kebab-case, senza maiuscole né spazi (*scelta mia*,
per comodità da terminale). Il titolo leggibile "Gestione delle eccezioni in
Spring Boot" compare nel README.

## 6. I capitoli

Ogni capitolo segue la stessa struttura (*scelta mia*):
1. **Il problema**: perché l'argomento conta, in poche righe.
2. **Teoria e meccanismo**: come funziona davvero, con riferimenti alle classi del
   framework e ai punti precisi della specifica.
3. **Frammenti di codice** essenziali.
4. **Insidie e compromessi**: errori comuni, scelte alternative a confronto.
5. **Nel progetto**: dove trovare il concetto nel backend o nel frontend (percorsi
   dei file e nomi dei test) e come provarlo (richiesta `curl` o azione nell'app).
6. **Riferimenti**: link alla documentazione ufficiale, alle RFC, alla JLS e al
   codice sorgente su GitHub.

Contenuti minimi di ciascun capitolo:

**01 – Fondamenti Java**
Gerarchia `Throwable` / `Error` / `Exception` / `RuntimeException`; eccezioni
checked e unchecked e il dibattito sul loro design; perché Spring preferisce le
unchecked; eccezioni concatenate (cause); `try-with-resources` ed eccezioni
soppresse; costo della creazione dello stack trace (costruttore con
`writableStackTrace`); come progettare una gerarchia di eccezioni di dominio.

**02 – Il meccanismo interno di Spring MVC**
Il percorso dell'eccezione dentro `DispatcherServlet` (`processHandlerException`);
la catena di `HandlerExceptionResolver` e il loro ordine
(`ExceptionHandlerExceptionResolver`, `ResponseStatusExceptionResolver`,
`DefaultHandlerExceptionResolver`); cosa succede quando nessuno la gestisce: il
container servlet, la pagina `/error`, `BasicErrorController`, `ErrorAttributes`
e le proprietà `server.error.*`.

**03 – Gestione centralizzata**
`@ExceptionHandler` locale e globale; `@ControllerAdvice` /
`@RestControllerAdvice`; ambito degli advice (`basePackages`, `assignableTypes`,
`annotations`); ordinamento con `@Order` e regole di corrispondenza dell'eccezione
più specifica; `ResponseEntityExceptionHandler` e cosa gestisce già; eccezioni
che portano con sé lo stato HTTP (`ErrorResponseException`,
`ResponseStatusException`).

**04 – La risposta: `ProblemDetail`**
RFC 9457 (che sostituisce la RFC 7807): campi standard (`type`, `title`,
`status`, `detail`, `instance`) e campi di estensione; il media type
`application/problem+json`; `ProblemDetail`, `ErrorResponse`,
`spring.mvc.problemdetails.enabled`; codici d'errore stabili e URI di `type`;
cosa non mettere mai nella risposta (stack trace, messaggi SQL, nomi di classi).

**05 – Il messaggio per l'utente e l'internazionalizzazione**
Separare il messaggio tecnico (per i log) dal messaggio per l'utente.
Confronto tra le due strategie:
(a) il backend traduce il messaggio in base all'header `Accept-Language`;
(b) il backend manda solo un codice stabile e la traduzione la fa il client.
Pro e contro di ciascuna e la scelta del progetto (vedi §8). `MessageSource`,
`LocaleResolver`, i codici di messaggio che `ErrorResponse` usa per `title` e
`detail` (`problemDetail.title.*`, `problemDetail.*`) e i loro argomenti.

**06 – Validazione**
Bean Validation (Jakarta Validation) su `@RequestBody` e sui parametri;
`MethodArgumentNotValidException` e `HandlerMethodValidationException`; la
validazione dei metodi integrata in Spring MVC dalla versione 6.1 in poi;
restituire gli errori per campo in una forma che il client sappia mappare sul
singolo controllo del form; messaggi di validazione localizzati.

**07 – Transazioni e rollback**
Perché per default `@Transactional` fa rollback solo sulle eccezioni unchecked e
sugli `Error`; `rollbackFor` / `noRollbackFor`; eccezioni catturate dentro il
metodo transazionale; `UnexpectedRollbackException`; il punto in cui l'eccezione
attraversa il proxy transazionale prima di arrivare all'advice; la traduzione
delle eccezioni di persistenza (`DataAccessException`,
`PersistenceExceptionTranslator`).

**08 – Casi di confine**
Errori sollevati **fuori** dalla portata degli advice: nei filtri servlet e in
Spring Security (`AuthenticationEntryPoint`, `AccessDeniedHandler`) e come far
restituire anche a loro un `ProblemDetail`; eccezioni nei metodi `@Async` e in
`CompletableFuture`; errori dei client HTTP (`RestClient`: `onStatus`,
`RestClientResponseException`) e come tradurre l'errore di un servizio esterno
senza esporlo; risorse non trovate (404 sugli URL che non esistono).

**09 – Logging e observability**
Cosa registrare e a quale livello (un errore del client non è un ERROR del
server); evitare di registrare due volte la stessa eccezione; correlazione con
`traceId` (Micrometer Tracing) incluso sia nei log sia nel `ProblemDetail`, in
modo che l'utente possa citarlo in una segnalazione.

**10 – Il client Angular** (*capitolo aggiunto da me*, dato che il progetto
comprende un frontend)
Come l'app intercetta gli errori HTTP (`HttpInterceptor` funzionale), li
trasforma in un modello tipizzato del `ProblemDetail`, li visualizza (errori
globali e errori per campo), gestisce i casi senza risposta dal server (rete,
status 0) e invia l'header `Accept-Language` in base alla lingua scelta.
Il ruolo dell'`ErrorHandler` globale di Angular per gli errori lato client.

## 7. Il backend (progetto eseguibile)

### Dominio d'esempio (*scelta mia*)
Una piccola **gestione di prestiti di libri**: libri, utenti, prestiti. È abbastanza
semplice da non distrarre, ma genera in modo naturale tutti i tipi di errore che
servono:
- risorsa non trovata (libro inesistente) → 404;
- conflitto con una regola di dominio (libro già in prestito, limite di prestiti
  superato) → 409 / 422;
- errori di validazione dell'input → 400 con errori per campo;
- errore di persistenza (vincolo di unicità violato) → tradotto e non esposto;
- accesso negato o non autenticato → 401 / 403, generati da Spring Security;
- servizio esterno che fallisce (simulato) → 502 / 503;
- errore inatteso → 500 generico, con `traceId` e senza dettagli interni.

### Componenti
- **Spring Web MVC**, **Validation**, **Data JPA** con database **H2 in memoria**
  (serve per il capitolo 07), **Spring Security** (per il capitolo 08), **Actuator**
  e **Micrometer Tracing** (per il capitolo 09).
- Spring Security protegge **solo** un sottoinsieme di endpoint (per esempio
  `/api/admin/**`), in modo che il resto dell'API sia usabile senza login
  dall'app Angular. Ci sono **due utenti in memoria**: `utente` (ruolo USER) e
  `admin` (ruolo ADMIN). Senza credenziali si ottiene un 401, con `utente` un
  403. Si usa **HTTP Basic**. L'`AuthenticationEntryPoint` personalizzato
  restituisce un `ProblemDetail` e non invia `WWW-Authenticate: Basic`, così il
  browser non apre il suo popup di login. Il capitolo 08 spiega questo
  compromesso (*scelte mie*).
- Una **gerarchia di eccezioni di dominio** coerente con il capitolo 01.
- Un **unico punto di gestione centralizzata** (`@RestControllerAdvice` che
  estende `ResponseEntityExceptionHandler`), più gli adattatori per Security
  che producono lo stesso formato.
- Un endpoint o un componente che simula una chiamata a un servizio esterno
  tramite `RestClient` (capitolo 08). Come simulare il servizio esterno lo decide
  il builder.
- Configurazione **CORS** per l'origine di sviluppo di Angular (oppure un proxy
  dal dev server Angular, a scelta del builder).

### Approccio adottato e alternative (*scelta mia*)
L'API dell'applicazione usa **un solo approccio**: quello raccomandato, descritto
sopra. Le **alternative con un comportamento verificabile** vivono in un package
separato di "laboratorio" (per esempio `...laboratorio.capitoloNN`), con
controller e advice minimi e test dedicati. Queste alternative sono:
- `@ExceptionHandler` locale;
- l'ambito degli advice e `@Order`;
- il fallback su `/error` / `BasicErrorController` quando nessuno gestisce l'eccezione;
- `@Async` e `CompletableFuture`;
- i fondamenti Java del capitolo 01 (eccezioni soppresse, stack trace non
  scrivibile, cause), dimostrati con semplici test JUnit.

Gli approcci **storici** (per esempio `@ResponseStatus`) e il **confronto tra
strategie di traduzione** restano solo nella prosa, con frammenti di codice.
Il vincolo dell'anti-obiettivo vale così: quello che il documento attribuisce al
progetto deve coincidere con quello che il progetto fa.

### Test
Ogni comportamento descritto nei documenti ha almeno un test che lo dimostra.
Il nome o il commento del test cita il capitolo, così il documento può puntare al
test. Preferire `MockMvcTester` (AssertJ) dove è adatto.
`./mvnw test` deve passare.

## 8. Il contratto d'errore e la strategia delle lingue

La strategia (*proposta mia, accettata dall'utente*) **combina le due strade**:
ogni risposta d'errore contiene **sia un codice stabile sia un messaggio già
localizzato**.

Forma della risposta (`application/problem+json`):

```json
{
  "type": "https://java-learning.local/errors/loan-limit-exceeded",
  "title": "Limite di prestiti superato",
  "status": 422,
  "detail": "Hai già 3 libri in prestito: restituiscine uno prima di prenderne un altro.",
  "instance": "/api/loans",
  "code": "LOAN_LIMIT_EXCEEDED",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "timestamp": "2026-10-16T10:15:30Z",
  "errors": [
    { "field": "isbn", "code": "NotBlank", "message": "L'ISBN è obbligatorio" }
  ]
}
```

- `code`: identificatore stabile in MAIUSCOLO_CON_UNDERSCORE, mai tradotto. Il client
  può usarlo per decisioni logiche o per una traduzione propria.
- `title` e `detail`: localizzati dal backend tramite `MessageSource`, in base
  all'header `Accept-Language`.
- `errors`: presente solo per gli errori di validazione.
- `traceId`: presente **in ogni risposta d'errore**, 4xx comprese (*scelta mia*).
  Nei test dove non c'è uno span attivo il campo può mancare; il modello
  TypeScript lo tratta come opzionale.
- Le lingue supportate sono `it` ed `en`. Se la lingua richiesta non è supportata
  si usa l'italiano (*scelta mia*).
- I testi stanno in `messages.properties` (italiano, default) e in
  `messages_en.properties`.

**Chi scrive il testo che l'utente vede (deciso dall'utente: strada A).** Il
backend è la **fonte unica** dei messaggi d'errore. Angular mostra `title`,
`detail` e i `message` di validazione così come arrivano, e usa `code` solo per la
logica (per esempio per decidere dove e come mostrare l'errore). Il dizionario del
client copre solo i testi dell'interfaccia e gli errori senza risposta dal server.
Quando si cambia lingua, gli errori già sullo schermo restano com'erano finché
l'utente non ripete l'azione. Il capitolo 05 discute anche la strada B (traduzione
lato client tramite `code`) come alternativa.

Gli URI di `type` sono identificatori e non è necessario che rispondano
(*scelta mia*). Il capitolo 04 spiega il perché e le alternative.

## 9. Il frontend Angular

Una piccola app Angular 22 (componenti standalone, signals) che fa da **banco di
prova** per gli errori:
- un selettore delle credenziali (**nessuna / utente / admin**) per provocare
  401 e 403;
- un selettore di lingua **IT / EN** che cambia sia i testi dell'interfaccia sia
  l'header `Accept-Language` inviato al backend;
- poche schermate o azioni che provocano ciascun caso d'errore del §7 (per esempio
  un form di prestito con validazione, un pulsante "chiedi un libro inesistente",
  un'azione admin senza credenziali, una chiamata al servizio esterno simulato);
- un `HttpInterceptor` funzionale che converte la risposta in un modello
  `ProblemDetail` tipizzato;
- visualizzazione **degli errori globali** (per esempio un banner o un toast) e
  **degli errori per campo** sotto il controllo corrispondente del form;
- un messaggio sensato anche quando il server non risponde (status 0), localizzato
  lato client;
- il `traceId` mostrato in modo discreto per le segnalazioni.

Per i testi dell'interfaccia basta un **piccolo dizionario IT/EN basato su signal,
senza librerie esterne** (*scelta mia*, per tenere il focus sugli errori e
permettere di cambiare lingua a runtime; l'i18n nativa di Angular è
compile-time e richiede una build per lingua). Il capitolo 10 discute
l'alternativa.

Test: almeno quelli dell'interceptor e della mappatura `ProblemDetail` → modello,
con il test runner predefinito di Angular 22.

## 10. Come si avvia (requisiti per il README)

Il README dell'argomento deve spiegare in pochi passi:
1. avvio del backend (`./mvnw spring-boot:run`) e porta usata;
2. avvio del frontend (`nvm use`, `npm install`, `npm start`) e URL;
3. credenziali dell'utente admin di prova;
4. una manciata di comandi `curl` per vedere ogni tipo di errore senza frontend;
5. come lanciare i test.

## 11. Versionamento

Il repository `java_learning` è sotto **git** (deciso dall'utente). Ogni passo
del piano si chiude con un commit.

## 12. Lasciato al builder

- La suddivisione interna dei package del backend e dei componenti Angular.
- Come simulare il servizio esterno (WireMock, un controller locale, un server
  interno ai test…).
- CORS oppure proxy del dev server Angular.
- La scelta tra le varie implementazioni di Micrometer Tracing (bridge Brave o
  OpenTelemetry), purché senza dover avviare servizi esterni.
- La lunghezza di ciascun capitolo: quanto basta per essere completo,
  senza riempitivi.
- L'ordine di costruzione (documenti e codice possono crescere insieme, capitolo
  per capitolo).

## 13. Decisioni prese da me (da verificare)

- Nome della cartella in kebab-case: `gestione-eccezioni-spring-boot`.
- Documenti di design e piano dentro `_progetto/` nella cartella dell'argomento.
- `README.md` alla radice come indice della biblioteca.
- Struttura comune dei capitoli (§6).
- Capitolo 10 dedicato al client Angular.
- Dominio d'esempio: prestiti di libri.
- Italiano come lingua di fallback.
- Dizionario IT/EN fatto in casa nel frontend, senza librerie.
- Spring Security limitato a `/api/admin/**`.
