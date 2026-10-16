# Gestione delle eccezioni in Spring Boot — Piano di implementazione

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Costruire l'argomento di studio "Gestione delle eccezioni in Spring Boot": 10 capitoli in Markdown, un backend Spring Boot che dimostra con test ogni comportamento descritto, e una piccola app Angular che visualizza gli errori in IT/EN.

**Architecture:** Il backend è una API di prestiti di libri con un'unica gestione centralizzata (`@RestControllerAdvice` che estende `ResponseEntityExceptionHandler`) che produce `ProblemDetail` arricchiti (`code`, `traceId`, `timestamp`, `errors`) e localizzati via `MessageSource`. Le alternative didattiche vivono in un package `laboratorio` isolato, fuori dall'ambito dell'advice principale. Il frontend usa un interceptor funzionale che converte gli errori HTTP in un modello `ApiProblem` tipizzato e mostra i testi del backend così come arrivano.

**Tech Stack:** Java 25, Spring Boot 4.1.x (ultima patch stabile), Maven Wrapper, Spring MVC, Validation, Data JPA + H2, Security, Actuator, Micrometer Tracing, JUnit 5 + AssertJ + `MockMvcTester`; Angular 22 (standalone, signals), Node 24, test runner predefinito di Angular 22.

**Spec:** `gestione-eccezioni-spring-boot/_progetto/design.md`

## Global Constraints

- Java **25**; Spring Boot **4.1.x** ultima patch stabile (verificata 4.1.1 al 2026-10-16); **vietate** milestone/RC/preview (niente 4.2).
- Angular **22** ultima patch stabile; Node **24** tramite `frontend/.nvmrc` contenente `24` (il default di nvm della macchina è la 20: ogni comando npm/ng va lanciato dopo `nvm use`).
- Build backend: Maven Wrapper (`./mvnw`).
- Documenti in **italiano**, termini tecnici in inglese; struttura comune dei capitoli: *Il problema → Teoria e meccanismo → Codice → Insidie e compromessi → Nel progetto → Riferimenti*.
- Ogni affermazione non ovvia nei documenti va ricondotta a documentazione ufficiale, sorgente del framework (classe citata) o specifica (JLS, RFC 9457).
- Ciò che un documento attribuisce al progetto deve coincidere con ciò che il progetto fa; i test citano il capitolo nel nome della classe o in un commento.
- Risposte d'errore in `application/problem+json`; `code` in `MAIUSCOLO_CON_UNDERSCORE` mai tradotto; `title`/`detail` localizzati; lingue `it` (default e fallback) ed `en`.
- Mai esporre in una risposta: stack trace, messaggi SQL, nomi di classi, messaggio dell'eccezione originale per i 5xx.
- URI di `type`: `https://java-learning.local/errors/<code-in-kebab-case>`.
- Il frontend mostra `title`/`detail`/`errors[].message` del backend così come arrivano (strada A); il dizionario client copre solo l'interfaccia e gli errori senza risposta (status 0).
- Ogni task si chiude con un commit; messaggi di commit in italiano che terminano con la riga `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. `Accept-Language` complesso o non supportato (`en-US,en;q=0.9` → inglese; `fr-FR` o header assente → italiano) — test in Task 5.
2. Corpo JSON malformato o con tipi sbagliati (`{"bookId":"abc"}`) → 400 `MALFORMED_REQUEST`, mai 500 — test in Task 4.
3. Path variable non numerica (`/api/books/abc`) → 400 `TYPE_MISMATCH`, mai 500 — test in Task 4.
4. URL inesistente sotto `/api` (`/api/nonexistent`) → 404 `ProblemDetail` con `code` `RESOURCE_NOT_FOUND`, non la whitelabel page — test in Task 4.
5. Fuga di dettagli interni: il 500 e il 409 da vincolo DB non contengono il messaggio dell'eccezione né testo SQL — test in Task 4 (500) e Task 7 (409).

---

## Mappa dei file

Radice backend: `gestione-eccezioni-spring-boot/backend/`; package base `it.javalearning.eccezioni` (sotto abbreviato `…`).

| Package / file | Responsabilità |
|---|---|
| `…errore.ErrorCode` | enum dei codici stabili con `HttpStatus` e chiave messaggio |
| `…errore.ApplicationException` (+ sottoclassi) | gerarchia sealed delle eccezioni applicative |
| `…web.errore.ProblemDetailFactory` | costruisce e arricchisce i `ProblemDetail` (unico punto) |
| `…web.errore.GlobalExceptionHandler` | `@RestControllerAdvice(basePackages = "it.javalearning.eccezioni.api")` |
| `…web.errore.TraceIdProvider` | restituisce il traceId corrente o `null` |
| `…web.sicurezza.*` | `SecurityConfig`, entry point e access denied handler in `ProblemDetail` |
| `…api.*` | controller, DTO (record) dell'API pubblica |
| `…dominio.*` | entità JPA, repository, servizi (`BookService`, `LoanService`) |
| `…integrazione.*` | `CatalogClient` (RestClient) e `SimulatedCatalogController` |
| `…laboratorio.capitoloNN.*` | alternative didattiche, fuori dall'ambito dell'advice |
| `src/main/resources/messages*.properties` | testi IT (default) ed EN |
| `src/main/resources/data.sql` | dati di prova |

Radice frontend: `gestione-eccezioni-spring-boot/frontend/src/app/`.

| File | Responsabilità |
|---|---|
| `core/i18n/i18n.service.ts`, `it.ts`, `en.ts` | lingua corrente (signal) e dizionario dell'interfaccia |
| `core/auth/credentials.service.ts` | credenziali scelte (signal) |
| `core/errors/api-problem.ts` | modello e mapper `toApiProblem` |
| `core/errors/problem.interceptor.ts` | header + conversione errori |
| `core/errors/error-notifier.service.ts`, `global-error-handler.ts` | errore globale corrente |
| `ui/error-banner/*`, `ui/loan-form/*`, `ui/scenarios/*`, `ui/toolbar/*` | componenti |

---

### Task 1: Ossatura dell'argomento e del backend

**Files:**
- Create: `README.md` (radice, indice della biblioteca)
- Create: `gestione-eccezioni-spring-boot/README.md` (titolo, intento, indice dei 10 capitoli con link a `docs/NN-*.md`, sezione "Come si avvia" da completare in Task 11)
- Create: `gestione-eccezioni-spring-boot/backend/` generato da Spring Initializr
- Test: `backend/src/test/java/it/javalearning/eccezioni/ApplicationContextTest.java`

**Interfaces:**
- Produces: progetto Maven `it.javalearning:eccezioni`, classe `it.javalearning.eccezioni.EccezioniApplication`, porta 8080.

- [ ] **Step 1: Verificare l'ultima patch 4.1.x** su `https://start.spring.io/metadata/client` (campo `bootVersion.values`), scegliendo la più alta senza suffisso `-M`/`-RC`/`-SNAPSHOT`.
- [ ] **Step 2: Generare il backend** con `curl https://start.spring.io/starter.zip` e parametri `type=maven-project`, `javaVersion=25`, `bootVersion=<da Step 1>`, `groupId=it.javalearning`, `artifactId=eccezioni`, `packageName=it.javalearning.eccezioni`, `name=eccezioni`, dipendenze: web, validation, data-jpa, h2, security, actuator, distributed-tracing (bridge a scelta, nessun exporter). Gli id esatti delle dipendenze vanno presi dallo stesso metadata: i nomi degli starter in Boot 4 sono modulari, non indovinarli. Estrarre in `backend/`.
- [ ] **Step 3: Test di contesto** `ApplicationContextTest.contextLoads()` con `@SpringBootTest`.
- [ ] **Step 4: Eseguire** `cd gestione-eccezioni-spring-boot/backend && ./mvnw -q test` → Expected: BUILD SUCCESS.
- [ ] **Step 5: Scrivere i due README** (contenuti nei Files sopra).
- [ ] **Step 6: Commit** `git add -A && git commit` — "Ossatura dell'argomento eccezioni e del backend".

---

### Task 2: Capitolo 01 — Fondamenti Java e gerarchia di eccezioni

**Files:**
- Create: `…/errore/ErrorCode.java`, `ApplicationException.java`, `DomainException.java`, `IntegrationException.java`, `ResourceNotFoundException.java`, `BusinessRuleException.java`
- Create: `docs/01-fondamenti-java.md`
- Test: `…/laboratorio/capitolo01/FondamentiJavaTest.java`, `…/errore/ErrorCodeTest.java`

**Interfaces:**
- Produces:
  - `enum ErrorCode { BOOK_NOT_FOUND(404), USER_NOT_FOUND(404), BOOK_ALREADY_ON_LOAN(409), LOAN_LIMIT_EXCEEDED(422), DUPLICATE_ISBN(409), VALIDATION_FAILED(400), MALFORMED_REQUEST(400), TYPE_MISMATCH(400), RESOURCE_NOT_FOUND(404), METHOD_NOT_ALLOWED(405), UNAUTHENTICATED(401), ACCESS_DENIED(403), EXTERNAL_SERVICE_ERROR(502), EXTERNAL_SERVICE_UNAVAILABLE(503), INTERNAL_ERROR(500) }` con `HttpStatus status()`, `String messageKey()` (= `"error." + kebab`, es. `error.loan-limit-exceeded`), `URI type()` (= `https://java-learning.local/errors/` + kebab).
  - `public abstract sealed class ApplicationException extends RuntimeException permits DomainException, IntegrationException` con costruttori `(ErrorCode code, Object... args)` e `(ErrorCode code, Throwable cause, Object... args)`, metodi `ErrorCode code()`, `Object[] args()`. `getMessage()` è un testo tecnico in inglese per i log (es. `"LOAN_LIMIT_EXCEEDED args=[2, 3]"`), mai mostrato all'utente.
  - `public abstract non-sealed class DomainException`; `public final class ResourceNotFoundException extends DomainException`; `public final class BusinessRuleException extends DomainException`; `public final class IntegrationException extends ApplicationException`.

- [ ] **Step 1: Test che falliscono.** `ErrorCodeTest`: `LOAN_LIMIT_EXCEEDED.status() == UNPROCESSABLE_CONTENT` (422), `messageKey() == "error.loan-limit-exceeded"`, `type().toString() == "https://java-learning.local/errors/loan-limit-exceeded"`. `FondamentiJavaTest` (commento "Capitolo 01"): `soppresseInTryWithResources` (risorsa la cui `close()` lancia → l'eccezione del blocco ha 1 soppressa); `catenaDelleCause` (`getCause()` conservata attraverso il wrapping); `stackTraceNonScrivibile` (sottoclasse con `writableStackTrace=false` → `getStackTrace().length == 0`); `switchSuGerarchiaSealed` (`switch` con pattern su `ApplicationException` esaustivo senza `default`, compila e distingue `DomainException`/`IntegrationException`).
- [ ] **Step 2: Eseguire** `./mvnw -q test -Dtest='ErrorCodeTest,FondamentiJavaTest'` → Expected: errori di compilazione / FAIL.
- [ ] **Step 3: Implementare** le classi in `…/errore/` come da Interfaces.
- [ ] **Step 4: Eseguire** lo stesso comando → Expected: PASS.
- [ ] **Step 5: Scrivere `docs/01-fondamenti-java.md`** coprendo i contenuti del §6/01 del design; sezione "Nel progetto" che punta a `ApplicationException` e a `FondamentiJavaTest`; discutere il compromesso di avere `HttpStatus` dentro `ErrorCode` (comodità contro purezza del dominio).
- [ ] **Step 6: Commit** — "Capitolo 01: fondamenti Java e gerarchia di eccezioni".

---

### Task 3: Dominio, API e Capitolo 02 — Il meccanismo interno di Spring MVC

**Files:**
- Create: `…/dominio/Book.java`, `User.java` (tabella `app_user`), `Loan.java`, `BookRepository.java`, `UserRepository.java`, `LoanRepository.java`, `BookService.java`, `LoanService.java`
- Create: `…/api/BookController.java`, `LoanController.java`, `DiagnosticsController.java`, `BookRequest.java`, `BookResponse.java`, `LoanRequest.java`, `LoanResponse.java`
- Create: `src/main/resources/data.sql`; Modify: `application.properties`
- Create: `…/web/sicurezza/SecurityConfig.java` (provvisorio: `permitAll` su tutto, CSRF disabilitato; sostituito in Task 8). Senza di esso lo starter Security blocca ogni richiesta con 401.
- Create: `…/laboratorio/capitolo02/UnhandledController.java`
- Create: `docs/02-meccanismo-spring-mvc.md`
- Test: `…/dominio/LoanServiceTest.java`, `…/laboratorio/capitolo02/FallbackErrorControllerTest.java`

**Interfaces:**
- Produces:
  - `Book(Long id, String isbn [unique, not null], String title, String author)`; `User(Long id, String name)`; `Loan(Long id, Book book, User user, Instant loanedAt, Instant returnedAt)`.
  - `BookService.findById(long id): Book` → `ResourceNotFoundException(BOOK_NOT_FOUND, id)`; `BookService.create(BookRequest r): Book` (usa `saveAndFlush`).
  - `LoanService.borrow(long userId, long bookId): Loan` `@Transactional`; costante `MAX_ACTIVE_LOANS = 3`; errori: `USER_NOT_FOUND(userId)`, `BOOK_NOT_FOUND(bookId)`, `BusinessRuleException(BOOK_ALREADY_ON_LOAN, bookId)`, `BusinessRuleException(LOAN_LIMIT_EXCEEDED, activeLoans, 3)`.
  - `record BookRequest(String isbn, String title, String author)`, `record LoanRequest(Long userId, Long bookId)` (vincoli aggiunti in Task 6), `record BookResponse(Long id, String isbn, String title, String author)`, `record LoanResponse(Long id, Long bookId, Long userId, Instant loanedAt)`.
  - Endpoint: `GET /api/books/{id}`, `POST /api/books` → 201, `POST /api/loans` → 201, `GET /api/diagnostics/unexpected-error` → lancia `IllegalStateException("dettaglio interno segreto")`.
  - `data.sql`: utenti 1 "Ada" (0 prestiti) e 2 "Linus" (3 prestiti attivi, sui libri 4, 5 e 6); libri 1–6 con ISBN di 13 cifre distinti. Proprietà: `spring.jpa.defer-datasource-initialization=true`, `spring.jpa.open-in-view=false`.

- [ ] **Step 1: Test che falliscono.** `LoanServiceTest` (`@DataJpaTest` + `@Import(LoanService.class, BookService.class)`): `prestitoRiuscito` (utente 1, libro 1 → Loan con `returnedAt == null`); `libroInesistente` (bookId 999 → `ResourceNotFoundException` con `code() == BOOK_NOT_FOUND`); `libroGiaInPrestito` (utente 1, libro 5 → `BOOK_ALREADY_ON_LOAN`); `limiteSuperato` (utente 2, libro 1 → `LOAN_LIMIT_EXCEEDED`, `args()` = `[3, 3]`). `FallbackErrorControllerTest` (commento "Capitolo 02"; `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestClient`/`TestRestTemplate`): `GET /laboratorio/capitolo02/unhandled` → 500 con il JSON di `BasicErrorController` (campi `timestamp`, `status`, `error`, `path`; nessun `trace` e nessun `message`, con le proprietà `server.error.*` di default).
- [ ] **Step 2: Eseguire** `./mvnw -q test -Dtest='LoanServiceTest,FallbackErrorControllerTest'` → Expected: FAIL.
- [ ] **Step 3: Implementare** entità, repository, servizi, controller, `data.sql` e `UnhandledController` (`GET /laboratorio/capitolo02/unhandled` lancia `IllegalStateException`; il package è fuori dall'ambito dell'advice, per costruzione).
- [ ] **Step 4: Eseguire** lo stesso comando → Expected: PASS.
- [ ] **Step 5: Scrivere `docs/02-meccanismo-spring-mvc.md`** (§6/02 del design). Deve spiegare perché il test usa un server reale: con MockMvc non c'è il forward del container verso `/error`.
- [ ] **Step 6: Commit** — "Dominio, API e capitolo 02 sul meccanismo di Spring MVC".

---

### Task 4: Capitoli 03 e 04 — Gestione centralizzata e `ProblemDetail`

**Files:**
- Create: `…/web/errore/ProblemDetailFactory.java`, `GlobalExceptionHandler.java`, `TraceIdProvider.java`
- Create: `…/laboratorio/capitolo03/LocalHandlerController.java`, `PrimoAdvice.java`, `SecondoAdvice.java`, `ScopedController.java`
- Create: `docs/03-gestione-centralizzata.md`, `docs/04-problem-detail.md`
- Modify: `application.properties` (`spring.mvc.problemdetails.enabled` resta `false`: l'advice è nostro, e il doc spiega il perché)
- Test: `…/web/errore/ProblemDetailContractTest.java`, `…/laboratorio/capitolo03/AmbitoEOrdineTest.java`

**Interfaces:**
- Consumes: `ErrorCode`, `ApplicationException` (Task 2); endpoint (Task 3).
- Produces:
  - `TraceIdProvider.currentTraceId(): String` (nullable); in questo task restituisce `null`, collegato al tracing in Task 9.
  - `ProblemDetailFactory.create(ErrorCode code, Object[] args, HttpServletRequest req): ProblemDetail` → status, `type`, `title`/`detail` (in questo task presi dalla chiave messaggio con un testo di ripiego; la localizzazione vera arriva in Task 5), `instance` = request URI, proprietà `code`, `timestamp` (ISO-8601 UTC), `traceId` se non null.
  - `ProblemDetailFactory.enrich(ProblemDetail pd, ErrorCode code, HttpServletRequest req): ProblemDetail` → aggiunge `code`/`timestamp`/`traceId`/`instance` a un `ProblemDetail` prodotto da Spring.
  - `GlobalExceptionHandler`: `@ExceptionHandler(ApplicationException.class)`; `@ExceptionHandler(Exception.class)` → `INTERNAL_ERROR` con detail generico, log ERROR con stack trace; override di `handleExceptionInternal` che mappa le eccezioni standard: `HttpMessageNotReadableException`→`MALFORMED_REQUEST`, `MethodArgumentTypeMismatchException`→`TYPE_MISMATCH`, `NoResourceFoundException`→`RESOURCE_NOT_FOUND`, `HttpRequestMethodNotSupportedException`→`METHOD_NOT_ALLOWED`, altrimenti `"HTTP_" + status`.

- [ ] **Step 1: Test che falliscono.** `ProblemDetailContractTest` (commento "Capitoli 03-04"; `@SpringBootTest` + `@AutoConfigureMockMvc` + `MockMvcTester`; Security per ora permette tutto, vedi Task 3 e Task 8):
  - `libroInesistente`: `GET /api/books/999` → 404, `Content-Type: application/problem+json`, `$.code == "BOOK_NOT_FOUND"`, `$.type == "https://java-learning.local/errors/book-not-found"`, `$.instance == "/api/books/999"`, `$.timestamp` presente.
  - `limiteSuperato`: `POST /api/loans {"userId":2,"bookId":1}` → 422, `$.code == "LOAN_LIMIT_EXCEEDED"`.
  - `libroGiaInPrestito`: `{"userId":1,"bookId":5}` → 409 `BOOK_ALREADY_ON_LOAN`.
  - `erroreInatteso`: `GET /api/diagnostics/unexpected-error` → 500 `INTERNAL_ERROR`; il corpo **non** contiene `"dettaglio interno segreto"` né `"IllegalStateException"` (Review Focus 5).
  - `jsonMalformato`: `POST /api/loans` con `{"userId":` → 400 `MALFORMED_REQUEST`; `tipoSbagliato`: `{"userId":1,"bookId":"abc"}` → 400 `MALFORMED_REQUEST` (Review Focus 2).
  - `pathVariableNonNumerica`: `GET /api/books/abc` → 400 `TYPE_MISMATCH` (Review Focus 3).
  - `urlInesistente`: `GET /api/nonexistent` → 404 `RESOURCE_NOT_FOUND`, problem+json (Review Focus 4).
  - `metodoNonSupportato`: `DELETE /api/books/1` → 405 `METHOD_NOT_ALLOWED`.

  `AmbitoEOrdineTest` (commento "Capitolo 03"): `handlerLocaleVinceSulGlobale` (`GET /laboratorio/capitolo03/local` → corpo `"gestito localmente"`); `ordineDegliAdvice` (`PrimoAdvice` `@Order(1)` e `SecondoAdvice` `@Order(2)`, entrambi con `assignableTypes = ScopedController.class`, gestiscono la stessa eccezione: risponde `PrimoAdvice`); `eccezionePiuSpecificaVince` (dentro uno stesso advice, handler per `RuntimeException` e per `IllegalArgumentException`: vince il secondo).
- [ ] **Step 2: Eseguire** `./mvnw -q test -Dtest='ProblemDetailContractTest,AmbitoEOrdineTest'` → Expected: FAIL.
- [ ] **Step 3: Implementare** factory, handler, provider e le classi del laboratorio. Per far arrivare `NoResourceFoundException` all'advice in Boot 4.1, verificare il comportamento predefinito (da Spring 6.1 la si lancia per default) e documentarlo nel capitolo 03.
- [ ] **Step 4: Eseguire** lo stesso comando → Expected: PASS.
- [ ] **Step 5: Scrivere `docs/03-gestione-centralizzata.md` e `docs/04-problem-detail.md`** (§6/03 e §6/04 del design). Il 04 include l'esempio JSON del §8 del design e spiega perché `type` è un identificatore e non deve necessariamente rispondere.
- [ ] **Step 6: Commit** — "Capitoli 03-04: gestione centralizzata e ProblemDetail".

---

### Task 5: Capitolo 05 — Messaggi per l'utente e internazionalizzazione

**Files:**
- Create: `src/main/resources/messages.properties` (italiano), `messages_en.properties`
- Create: `…/web/LocaleConfig.java`
- Modify: `ProblemDetailFactory.java` (usa `MessageSource` + `LocaleContextHolder.getLocale()`)
- Create: `docs/05-messaggi-utente-i18n.md`
- Test: `…/web/errore/LocalizzazioneTest.java`

**Interfaces:**
- Produces: `LocaleConfig.localeResolver(): LocaleResolver` → `AcceptHeaderLocaleResolver` con lingue supportate `[it, en]` e default `it`. Chiavi messaggio `error.<kebab>.title` e `error.<kebab>.detail` per ogni `ErrorCode`, con argomenti posizionali `{0}`, `{1}`; chiavi `problemDetail.title.*` / `problemDetail.*` per le eccezioni standard di Spring usate da `ResponseEntityExceptionHandler`.
- Testi esatti richiesti (gli altri a discrezione, stesso tono):
  - `error.loan-limit-exceeded.title` = `Limite di prestiti superato` / `Loan limit exceeded`
  - `error.loan-limit-exceeded.detail` = `Hai già {0} libri in prestito: restituiscine uno prima di prenderne un altro.` / `You already have {0} books on loan: return one before borrowing another.`

- [ ] **Step 1: Test che falliscono.** `LocalizzazioneTest` (commento "Capitolo 05"), tutti su `POST /api/loans {"userId":2,"bookId":1}`:
  - `italianoDefault` (nessun header) → `$.title == "Limite di prestiti superato"`, `$.detail` contiene `"Hai già 3 libri"`.
  - `inglese` (`Accept-Language: en`) → `$.title == "Loan limit exceeded"`.
  - `ingleseConRegioneEQualita` (`en-US,en;q=0.9`) → inglese (Review Focus 1).
  - `linguaNonSupportata` (`fr-FR`) → italiano (Review Focus 1).
  - `codeNonTradotto`: in entrambe le lingue `$.code == "LOAN_LIMIT_EXCEEDED"`.
  - `eccezioneStandardLocalizzata`: `GET /api/books/abc` con `en` → `$.title` in inglese, preso da `messages_en.properties`.
- [ ] **Step 2: Eseguire** `./mvnw -q test -Dtest=LocalizzazioneTest` → Expected: FAIL.
- [ ] **Step 3: Implementare** i file di messaggi, il `LocaleConfig` e la localizzazione nella factory. Ogni `ErrorCode` deve avere title e detail in entrambe le lingue.
- [ ] **Step 4: Aggiungere `MessaggiCompletiTest.ogniErrorCodeHaTitleEDetailInEntrambeLeLingue`**: scorre `ErrorCode.values()` e verifica che le chiavi esistano in `it` ed `en`. Eseguire tutta la suite: `./mvnw -q test` → Expected: PASS (`ProblemDetailContractTest` compreso).
- [ ] **Step 5: Scrivere `docs/05-messaggi-utente-i18n.md`**: confronto tra strada A e strada B, la scelta del progetto (A, con `code` stabile), `MessageSource`, `LocaleResolver`, i codici di messaggio di `ErrorResponse`.
- [ ] **Step 6: Commit** — "Capitolo 05: messaggi localizzati per l'utente".

---

### Task 6: Capitolo 06 — Validazione

**Files:**
- Modify: `BookRequest.java`, `LoanRequest.java` (vincoli), `BookController.java`, `LoanController.java` (`@Valid`; `@Positive` sulla path variable `id`)
- Modify: `GlobalExceptionHandler.java` (override di `handleMethodArgumentNotValid` e `handleHandlerMethodValidationException`)
- Modify: `messages*.properties` (messaggi di validazione localizzati)
- Create: `docs/06-validazione.md`
- Test: `…/api/ValidazioneTest.java`

**Interfaces:**
- Produces: proprietà `errors` del `ProblemDetail`: lista di `record FieldErrorItem(String field, String code, String message)` (`code` = nome del vincolo, es. `NotBlank`), con `code` del problema = `VALIDATION_FAILED`.
- Vincoli: `BookRequest.isbn` `@NotBlank @Pattern(regexp = "\\d{13}")`, `title` `@NotBlank @Size(max = 200)`, `author` `@NotBlank`; `LoanRequest.userId` e `bookId` `@NotNull @Positive`.

- [ ] **Step 1: Test che falliscono.** `ValidazioneTest` (commento "Capitolo 06"):
  - `corpoNonValido`: `POST /api/books {"isbn":"","title":"","author":"X"}` → 400 `VALIDATION_FAILED`, `$.errors` contiene `{field:"isbn", code:"NotBlank"}` e `{field:"title", code:"NotBlank"}`, `message` in italiano.
  - `messaggiInInglese`: stesso corpo con `en` → `message` in inglese.
  - `parametroNonValido`: `GET /api/books/0` → 400 `VALIDATION_FAILED` (via `HandlerMethodValidationException`), con un elemento in `errors` per il parametro `id`.
- [ ] **Step 2: Eseguire** `./mvnw -q test -Dtest=ValidazioneTest` → Expected: FAIL.
- [ ] **Step 3: Implementare** vincoli, override e messaggi.
- [ ] **Step 4: Eseguire** `./mvnw -q test` → Expected: PASS.
- [ ] **Step 5: Scrivere `docs/06-validazione.md`** (§6/06 del design), indicando la differenza tra le due eccezioni e quando scatta ciascuna.
- [ ] **Step 6: Commit** — "Capitolo 06: validazione con errori per campo".

---

### Task 7: Capitolo 07 — Transazioni e rollback

**Files:**
- Modify: `GlobalExceptionHandler.java` (`DataIntegrityViolationException` → `DUPLICATE_ISBN` quando riguarda il vincolo di unicità sull'ISBN, altrimenti `INTERNAL_ERROR`)
- Create: `…/laboratorio/capitolo07/RegistroOperazioni.java` (entità + repository), `ServizioTransazionale.java`, `ServizioInterno.java`, `CheckedDiDominio.java` (extends `Exception`)
- Create: `docs/07-transazioni-rollback.md`
- Test: `…/api/IsbnDuplicatoTest.java`, `…/laboratorio/capitolo07/RollbackTest.java`

**Interfaces:**
- Produces: `ServizioTransazionale.scriviPoiLanciaChecked()` (`@Transactional`, salva una riga e lancia `CheckedDiDominio`); `scriviPoiLanciaCheckedConRollbackFor()` (`@Transactional(rollbackFor = CheckedDiDominio.class)`); `scriviPoiLanciaUnchecked()`; `catturaEccezioneInterna()` (chiama `ServizioInterno.fallisci()`, `@Transactional` REQUIRED che lancia unchecked, e la cattura).

- [ ] **Step 1: Test che falliscono.** `IsbnDuplicatoTest` (commento "Capitolo 07"): `POST /api/books` con un ISBN già presente in `data.sql` → 409 `DUPLICATE_ISBN`, il corpo non contiene `"constraint"`, `"SQL"` né il nome della tabella (Review Focus 5). `RollbackTest` (commento "Capitolo 07"; ogni test conta le righe di `RegistroOperazioni` dopo la chiamata): `checkedNonFaRollback` → 1 riga; `checkedConRollbackForFaRollback` → 0; `uncheckedFaRollback` → 0; `catturareUnaInternaMarcataRollbackOnly` → `UnexpectedRollbackException` e 0 righe.
- [ ] **Step 2: Eseguire** `./mvnw -q test -Dtest='IsbnDuplicatoTest,RollbackTest'` → Expected: FAIL.
- [ ] **Step 3: Implementare** la mappatura e le classi del laboratorio. Il riconoscimento del vincolo ISBN usa il nome del vincolo dichiarato sull'entità (`@Table(uniqueConstraints = @UniqueConstraint(name = "uk_book_isbn", columnNames = "isbn"))`).
- [ ] **Step 4: Eseguire** `./mvnw -q test` → Expected: PASS.
- [ ] **Step 5: Scrivere `docs/07-transazioni-rollback.md`** (§6/07 del design), incluso dove l'eccezione attraversa il proxy transazionale rispetto all'advice e perché serve `saveAndFlush` per far emergere la violazione dentro il servizio.
- [ ] **Step 6: Commit** — "Capitolo 07: transazioni, rollback e traduzione delle eccezioni di persistenza".

---

### Task 8: Capitolo 08 — Casi di confine (Security, RestClient, async)

**Files:**
- Modify: `…/web/sicurezza/SecurityConfig.java`
- Create: `…/web/sicurezza/ProblemDetailAuthenticationEntryPoint.java`, `ProblemDetailAccessDeniedHandler.java`
- Create: `…/api/AdminController.java` (`GET /api/admin/stats`)
- Create: `…/integrazione/CatalogClient.java`, `SimulatedCatalogController.java`; Modify: `BookController.java` (`GET /api/books/{id}/catalog-info?simulate=ok|error|unavailable`)
- Create: `…/laboratorio/capitolo08/AsyncConfig.java`, `ServizioAsincrono.java`, `AsyncController.java`
- Create: `docs/08-casi-di-confine.md`
- Test: `…/web/sicurezza/SicurezzaTest.java`, `…/integrazione/CatalogClientTest.java`, `…/laboratorio/capitolo08/AsyncTest.java`

**Interfaces:**
- Consumes: `ProblemDetailFactory.create(...)` (Task 4) e un `JsonMapper` di Jackson per serializzare fuori da MVC.
- Produces:
  - Utenti in memoria: `utente`/`utente` (USER) e `admin`/`admin` (ADMIN), password `{noop}`. `/api/admin/**` richiede ADMIN; tutto il resto `permitAll`. Stateless, CSRF disabilitato per `/api/**`, HTTP Basic con l'entry point personalizzato (nessun header `WWW-Authenticate`).
  - `CatalogClient.fetchInfo(String isbn, String simulate): CatalogInfo` (`record CatalogInfo(String isbn, String summary)`) che chiama `${catalog.base-url}/simulated-catalog/{isbn}?simulate=…`; `catalog.base-url` per default è `http://localhost:${server.port}`. Errori: risposta 5xx del catalogo → `IntegrationException(EXTERNAL_SERVICE_ERROR)`; connessione rifiutata o timeout (`ResourceAccessException`) → `IntegrationException(EXTERNAL_SERVICE_UNAVAILABLE)`. `SimulatedCatalogController` restituisce 200, 500 o 503 in base a `simulate`. Il servizio esterno "unavailable" restituisce 503, che il client traduce in `EXTERNAL_SERVICE_UNAVAILABLE`.
  - `ServizioAsincrono.fuocoEDimentica()` `@Async void` che lancia; `AsyncConfig` registra un `AsyncUncaughtExceptionHandler` osservabile dai test. `AsyncController`: `GET /laboratorio/capitolo08/future` restituisce `CompletableFuture<String>` completato con eccezione.

- [ ] **Step 1: Test che falliscono.**
  - `SicurezzaTest` (commento "Capitolo 08"): `senzaCredenziali` → 401, problem+json, `$.code == "UNAUTHENTICATED"`, header `WWW-Authenticate` assente; `utenteSenzaRuolo` (Basic `utente:utente`) → 403 `ACCESS_DENIED`; `admin` → 200; `inglese` (401 con `en`) → titolo inglese; `restoDellApiPubblico` → `GET /api/books/1` senza credenziali → 200.
  - `CatalogClientTest`: `GET /api/books/1/catalog-info?simulate=ok` → 200; `simulate=error` → 502 `EXTERNAL_SERVICE_ERROR`; `simulate=unavailable` → 503 `EXTERNAL_SERVICE_UNAVAILABLE`; per entrambi gli errori il corpo non contiene l'URL del catalogo.
  - `AsyncTest`: `eccezioneInVoidAsyncVaAlHandler` (attesa limitata, poi verifica che l'handler abbia ricevuto l'eccezione); `futureCompletatoConEccezioneArrivaAllAdvice` (`MockMvcTester` con dispatch asincrono → stato e corpo prodotti dall'advice del laboratorio).
- [ ] **Step 2: Eseguire** `./mvnw -q test -Dtest='SicurezzaTest,CatalogClientTest,AsyncTest'` → Expected: FAIL.
- [ ] **Step 3: Implementare** Security, client, laboratorio. Il `SecurityConfig` provvisorio del Task 3 viene sostituito dalla configurazione definitiva.
- [ ] **Step 4: Eseguire** `./mvnw -q test` → Expected: PASS.
- [ ] **Step 5: Scrivere `docs/08-casi-di-confine.md`** (§6/08 del design), incluso il compromesso di sopprimere `WWW-Authenticate`.
- [ ] **Step 6: Commit** — "Capitolo 08: errori fuori dall'advice, Security, RestClient e async".

---

### Task 9: Capitolo 09 — Logging e observability

**Files:**
- Modify: `TraceIdProvider.java` (usa `io.micrometer.tracing.Tracer`), `GlobalExceptionHandler.java` (livelli di log)
- Modify: `application.properties` (`management.tracing.sampling.probability=1.0`; il pattern di log contiene il traceId, che è il default di Boot quando il tracing è attivo)
- Create: `docs/09-logging-observability.md`
- Test: `…/web/errore/TracciamentoTest.java`

**Interfaces:**
- Produces: politica di log: 4xx → WARN su una riga senza stack trace (`code`, `instance`, `getMessage()` tecnico); 5xx → ERROR con stack trace; ogni eccezione registrata **una sola volta**, nell'advice.

- [ ] **Step 1: Test che falliscono.** `TracciamentoTest` (commento "Capitolo 09"; tracing abilitato nel test con l'annotazione di auto-configurazione dell'observability di Boot 4.1, da verificare nella documentazione di riferimento): `traceIdNelProblemDetail` (`GET /api/diagnostics/unexpected-error` → `$.traceId` di 32 caratteri esadecimali); `traceIdAncheSu4xx` (`GET /api/books/999` → `$.traceId` presente); `logUnaSolaVolta` (con `OutputCaptureExtension`: il 500 produce esattamente una riga ERROR e quella riga contiene lo stesso traceId della risposta; il 404 produce una riga WARN e nessuno stack trace).
- [ ] **Step 2: Eseguire** `./mvnw -q test -Dtest=TracciamentoTest` → Expected: FAIL.
- [ ] **Step 3: Implementare** provider e politica di log.
- [ ] **Step 4: Eseguire** `./mvnw -q test` → Expected: PASS.
- [ ] **Step 5: Scrivere `docs/09-logging-observability.md`** (§6/09 del design).
- [ ] **Step 6: Commit** — "Capitolo 09: logging e correlazione con traceId".

---

### Task 10: Frontend — Ossatura e nucleo della gestione errori

**Files:**
- Create: `gestione-eccezioni-spring-boot/frontend/` (Angular CLI 22, standalone, senza SSR, CSS), `frontend/.nvmrc` (`24`), `frontend/proxy.conf.json` (`/api` → `http://localhost:8080`, collegato a `ng serve` in `angular.json`)
- Create: `src/app/core/i18n/i18n.service.ts`, `it.ts`, `en.ts`; `core/auth/credentials.service.ts`; `core/errors/api-problem.ts`, `problem.interceptor.ts`, `error-notifier.service.ts`, `global-error-handler.ts`
- Modify: `src/app/app.config.ts` (`provideHttpClient(withInterceptors([problemInterceptor]))`, `{ provide: ErrorHandler, useClass: GlobalErrorHandler }`)
- Test: `core/errors/api-problem.spec.ts`, `core/errors/problem.interceptor.spec.ts`

**Interfaces:**
- Produces:
  - `type Lang = 'it' | 'en'`; `I18nService { lang: Signal<Lang>; setLang(l: Lang): void; t(key: string): string }` (ripiega sulla chiave se manca la traduzione).
  - `type Credentials = 'none' | 'utente' | 'admin'`; `CredentialsService { current: Signal<Credentials>; set(c: Credentials): void; authorizationHeader(): string | null }`.
  - `interface FieldErrorItem { field: string; code: string; message: string }`; `interface ApiProblem { type: string; title: string; status: number; detail?: string; instance?: string; code: string; traceId?: string; timestamp?: string; errors?: FieldErrorItem[] }`.
  - `toApiProblem(err: HttpErrorResponse, t: (key: string) => string): ApiProblem`: corpo problem+json → lo restituisce tipizzato; `status === 0` → `{ code: 'NETWORK_ERROR', status: 0, title: t('errors.network.title'), detail: t('errors.network.detail'), type: 'about:blank' }`; qualsiasi altro corpo non conforme → `code: 'UNKNOWN_ERROR'` con i testi client `errors.unknown.*`.
  - `problemInterceptor: HttpInterceptorFn`: aggiunge `Accept-Language: <lang>` e, se presente, `Authorization`; in errore rilancia `toApiProblem(...)` (con `throwError`).
  - `ErrorNotifierService { current: Signal<ApiProblem | null>; show(p: ApiProblem): void; clear(): void }`; `GlobalErrorHandler implements ErrorHandler`: logga in console e mostra un `ApiProblem` client `CLIENT_ERROR` (testi `errors.client.*`).

- [ ] **Step 1: Generare il progetto**: `nvm use 24 && npx @angular/cli@22 new frontend --standalone --style=css --ssr=false --routing=false --skip-git`, poi `.nvmrc` e `proxy.conf.json`. Verificare con `npm test` (runner predefinito) → Expected: i test di default passano.
- [ ] **Step 2: Test che falliscono.** `api-problem.spec.ts`: `problemConformeRestaInvariato` (corpo con `code: 'LOAN_LIMIT_EXCEEDED'`, `title` italiano → stessi valori, `title` non ritradotto); `status0DiventaNetworkError` (`code === 'NETWORK_ERROR'`, `title === t('errors.network.title')`); `corpoNonConformeDiventaUnknown` (corpo HTML → `UNKNOWN_ERROR`, `status` conservato). `problem.interceptor.spec.ts` (con `HttpTestingController`): `inviaAcceptLanguage` (dopo `setLang('en')` la richiesta ha `Accept-Language: en`); `inviaAuthorizationSoloSeScelto` (`none` → nessun header; `admin` → `Basic YWRtaW46YWRtaW4=`); `rilanciaApiProblem` (risposta 404 problem+json → l'osservabile fallisce con un `ApiProblem` con `code === 'BOOK_NOT_FOUND'`).
- [ ] **Step 3: Eseguire** `npm test` → Expected: FAIL.
- [ ] **Step 4: Implementare** i servizi e le funzioni come da Interfaces; dizionari `it.ts`/`en.ts` con almeno le chiavi `errors.network.*`, `errors.unknown.*`, `errors.client.*` e quelle dell'interfaccia usate in Task 11.
- [ ] **Step 5: Eseguire** `npm test` e `npm run build` → Expected: PASS / build riuscita.
- [ ] **Step 6: Commit** — "Frontend: ossatura e nucleo della gestione errori".

---

### Task 11: Frontend — Interfaccia, Capitolo 10 e README

**Files:**
- Create: `src/app/ui/toolbar/*` (selettori lingua IT/EN e credenziali nessuna/utente/admin), `ui/error-banner/*` (mostra `ErrorNotifierService.current()`: `title`, `detail`, `code` e `traceId` in piccolo, pulsante chiudi), `ui/loan-form/*` (form reattivo `userId`/`bookId`, errori per campo sotto il controllo corrispondente quando `code === 'VALIDATION_FAILED'`, altrimenti banner), `ui/scenarios/*` (un pulsante per ciascun caso: libro inesistente, limite superato, libro già in prestito, ISBN duplicato, validazione libro, admin stats, catalogo error/unavailable, errore inatteso, URL inesistente)
- Modify: `src/app/app.ts` / template (compone toolbar, banner, form, scenari)
- Create: `docs/10-client-angular.md`
- Modify: `gestione-eccezioni-spring-boot/README.md` (sezione "Come si avvia" secondo il §10 del design: porte 8080/4200, credenziali, `curl` per ogni tipo d'errore, comandi di test), `README.md` radice
- Test: `ui/loan-form/loan-form.spec.ts`, `ui/error-banner/error-banner.spec.ts`

**Interfaces:**
- Consumes: tutto quanto prodotto in Task 10.

- [ ] **Step 1: Test che falliscono.** `loan-form.spec.ts`: `erroriPerCampo` (risposta 400 con `errors: [{field:'userId', code:'NotNull', message:'Campo obbligatorio'}]` → il testo appare sotto il controllo `userId` e il banner resta vuoto); `erroreDiDominioNelBanner` (422 → `ErrorNotifierService.current().code === 'LOAN_LIMIT_EXCEEDED'`). `error-banner.spec.ts`: `mostraTitleDetailETraceId`; `nonRitraduceAlCambioLingua` (dopo `setLang('en')` il `title` mostrato è lo stesso di prima).
- [ ] **Step 2: Eseguire** `npm test` → Expected: FAIL.
- [ ] **Step 3: Implementare** i componenti.
- [ ] **Step 4: Eseguire** `npm test` e `npm run build` → Expected: PASS.
- [ ] **Step 5: Scrivere `docs/10-client-angular.md`** (§6/10 del design, inclusa la discussione su dizionario fatto in casa e i18n nativa di Angular) e completare i due README.
- [ ] **Step 6: Commit** — "Frontend: interfaccia degli scenari, capitolo 10 e istruzioni di avvio".

---

### Task 12: Verifica finale end-to-end

**Files:**
- Modify: soltanto ciò che la verifica trova da correggere.

- [ ] **Step 1: Eseguire la suite backend** `cd backend && ./mvnw test` → Expected: BUILD SUCCESS, 0 failures.
- [ ] **Step 2: Eseguire la suite frontend** `cd frontend && nvm use && npm test && npm run build` → Expected: tutto verde.
- [ ] **Step 3: Prova manuale.** Avviare il backend, eseguire tutti i `curl` del README e confrontare l'output con quanto descritto nel README.
- [ ] **Step 4: Prova nel browser.** Avviare il frontend con il proxy e fare ogni scenario sia in IT sia in EN, con le tre credenziali; con il backend spento verificare il messaggio `NETWORK_ERROR`.
- [ ] **Step 5: Coerenza dei documenti.** Ogni percorso di file e ogni nome di test citato nelle sezioni "Nel progetto" esiste davvero (verificare con `grep`/`ls`); ogni link relativo tra documenti funziona.
- [ ] **Step 6: Commit** delle eventuali correzioni — "Verifica finale dell'argomento eccezioni".
