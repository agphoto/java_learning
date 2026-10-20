# Il percorso di un errore

*Gestione centralizzata delle eccezioni · Spring Boot 4.1 · Angular 22*

Quando il service lancia `BookNotFoundException`, nessun controller e nessun componente la gestiscono da soli. L'eccezione attraversa un punto unico per lato: l'advice sul backend e l'interceptor sulla GUI. In mezzo viaggia un contratto: il `ProblemDetail` della RFC 9457.

## Percorso di `GET /api/books/999`

| # | Lato | Chi | Cosa | Come |
|---|------|-----|------|------|
| 1 | Backend | Service | `throw BookNotFoundException(999)` | il dominio non sa nulla di HTTP |
| 2 | Backend | Controller | nessun `try/catch` | l'eccezione risale |
| 3 | Backend | DispatcherServlet | `ExceptionHandlerExceptionResolver` | cerca un `@ExceptionHandler` |
| 4 | Backend | Advice | `GlobalExceptionHandler` | eccezione → stato + `ProblemDetail` |
| 5 | **HTTP** | Filo | `404 application/problem+json` | il contratto sul filo |
| 6 | GUI | HttpClient | `HttpErrorResponse` | tipo generico di Angular |
| 7 | GUI | Interceptor | `errorInterceptor` | `HttpErrorResponse` → `ProblemDetail` |
| 8 | GUI | Componente | `error: p => error.set(p)` | un signal, nessun parsing |
| 9 | GUI | Template | `@if (error(); as problem)` | riquadro `role="alert"` |

## Il principio: un punto unico per lato, un contratto in mezzo

| Backend | Contratto | GUI |
|---------|-----------|-----|
| **`@RestControllerAdvice`** | **`ProblemDetail` · RFC 9457** | **`HttpInterceptorFn`** |
| Traduce ogni eccezione in una risposta. Controller e service si limitano a lanciare: nessun `try/catch` e nessun `ResponseEntity` d'errore costruito a mano. | Una sola forma per tutti gli errori: `type`, `title`, `status`, `detail`, `instance` e le estensioni. È l'unica cosa che i due lati condividono. | Converte ogni `HttpErrorResponse` in un `ProblemDetail` tipizzato, compresi i casi in cui il backend non ha risposto. I componenti ricevono sempre la stessa forma. |

## Backend: dall'eccezione alla risposta (5 passi)

### 1. Eccezioni di dominio, unchecked

Ogni eccezione ha un nome che dice cosa è successo e porta con sé i dati dell'occorrenza (l'id, l'ISBN). Non dichiara stati HTTP: lo strato web penserà alla traduzione.

File: `errore/BookNotFoundException.java` · `DuplicateIsbnException.java`

```java
public class BookNotFoundException
        extends RuntimeException {

    private final long bookId;

    public BookNotFoundException(long bookId) {
        super("Libro " + bookId + " inesistente");
        this.bookId = bookId;
    }

    public long getBookId() { return bookId; }
}
```

### 2. Il service lancia, il controller non fa niente

Il service verifica la regola e lancia. Il controller resta quello del caso di successo: nessun `try/catch` e nessun `if` sull'esito.

File: `libro/BookService.java`

```java
public Book findById(long id) {
    return repository.findById(id)
        .orElseThrow(() -> new BookNotFoundException(id));
}

@Transactional
public Book create(BookRequest request) {
    if (repository.existsByIsbn(request.isbn())) {
        throw new DuplicateIsbnException(request.isbn());
    }
    return repository.save(...);
}
```

### 3. Un advice che estende `ResponseEntityExceptionHandler`

Estendendo la classe base erediti il `ProblemDetail` per tutte le eccezioni standard di Spring MVC: JSON malformato, tipo sbagliato nel path, 405, 404 di routing.

- Con questo advice `spring.mvc.problemdetails.enabled` diventa ininfluente: l'handler di Boot è `@ConditionalOnMissingBean`.
- Un metodo per ogni eccezione di dominio, che restituisce `ProblemDetail`. Spring ne ricava lo stato e il Content-Type e aggiunge `instance`.

File: `errore/GlobalExceptionHandler.java`

```java
@RestControllerAdvice
public class GlobalExceptionHandler
        extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    ProblemDetail handleNotFound(BookNotFoundException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());
        p.setType(URI.create("https://java-learning.local/errors/book-not-found"));
        p.setTitle("Libro inesistente");
        p.setProperty("code", "BOOK_NOT_FOUND");
        p.setProperty("bookId", ex.getBookId());
        return p;
    }

    @ExceptionHandler(DuplicateIsbnException.class)
    ProblemDetail handleDuplicateIsbn(DuplicateIsbnException ex) { /* 409 */ }
}
```

### 4. Validazione: override, non `@ExceptionHandler`

`MethodArgumentNotValidException` è già gestita dalla classe base. Un secondo `@ExceptionHandler` per la stessa eccezione renderebbe ambiguo il mapping e l'applicazione non partirebbe. Si fa l'override del metodo e si aggiunge l'estensione `errors`.

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers,
        HttpStatusCode status, WebRequest request) {
    ProblemDetail problem = ex.getBody();
    problem.setProperty("errors", ex.getBindingResult()
        .getFieldErrors().stream()
        .map(e -> Map.of("field", e.getField(),
                         "message", String.valueOf(e.getDefaultMessage())))
        .toList());
    return handleExceptionInternal(ex, problem, headers, status, request);
}
```

### 5. Due reti di sicurezza

- **Vincolo del database**: `DataIntegrityViolationException` diventa 409. Copre ciò che il controllo applicativo non vede, cioè l'update e le richieste concorrenti.
- **Ultima istanza**: `Exception` diventa 500 con un testo generico. Lo stack trace va nel log e non arriva mai al client (CWE-209).

Per le eccezioni MVC il resolver sceglie sempre l'handler più specifico, quindi `Exception.class` non le intercetta.

```java
@ExceptionHandler(DataIntegrityViolationException.class)
ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Dati in conflitto");  // mai ex.getMessage()
}

@ExceptionHandler(Exception.class)
ProblemDetail handleUnexpected(Exception ex) {
    logger.error("Errore inatteso", ex);           // ereditato
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Errore inatteso");
}
```

## Il contratto, e chi legge cosa

*Sul filo · `application/problem+json`*

```json
{
  "type":     "https://java-learning.local/errors/book-not-found",
  "title":    "Libro inesistente",
  "status":   404,
  "detail":   "Libro 999 inesistente",
  "instance": "/api/books/999",
  "code":     "BOOK_NOT_FOUND",
  "bookId":   999
}
```

Validazione, con l'estensione `errors`:

```json
{
  "status": 400,
  "title":  "Bad Request",
  "detail": "Invalid request content.",
  "errors": [
    { "field": "isbn",  "message": "..." },
    { "field": "title", "message": "..." }
  ]
}
```

| Campo | Lettore | Significato |
|-------|---------|-------------|
| `type` | macchina | URI stabile, uno per significato: è la chiave dell'errore. Se manca, vale `about:blank`, cioè "basta lo stato". |
| `title` | persona | Fisso per un dato `type`. Cambia solo con la lingua. |
| `status` | macchina | Ripete lo stato HTTP, utile quando la risposta passa da un proxy. |
| `detail` | persona | Descrive questa occorrenza. Il client non deve interpretarlo. |
| `instance` | diagnosi | Quale richiesta. Spring lo valorizza con il path se lo lasci vuoto. |
| estensioni | macchina | `code`, `bookId`, `errors`, `traceId`: i dati su cui il client decide. |

> **Regola d'oro.** Tutto ciò su cui la GUI prende decisioni sta in `type` o in un'estensione, mai in `detail`.

## GUI: dalla risposta al riquadro (4 passi)

### 1. Il tipo condiviso

Un'interfaccia TypeScript che rispecchia il contratto. Mettila accanto all'interceptor, che è l'unico punto in cui la si costruisce.

File: `src/app/error.interceptor.ts`

```typescript
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: { field: string; message: string }[];
}
```

### 2. Un interceptor funzionale

Intercetta ogni `HttpErrorResponse` e la rilancia come `ProblemDetail`. Deve coprire anche le risposte che non vengono dal tuo backend:

- **status 0**: rete assente o backend spento, nessun corpo da leggere;
- **corpo non JSON**: un 502 o 504 del proxy arriva come testo o HTML, e lo spread di una stringa produrrebbe le chiavi `0`, `1`, `2`… Controlla `typeof`.

```typescript
export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const body = typeof error.error === 'object' ? error.error : {};
      const problem: ProblemDetail =
        error.status === 0
          ? { status: 0, title: 'Server non raggiungibile' }
          : { status: error.status, title: error.statusText, ...body };
      return throwError(() => problem);
    }),
  );
```

### 3. Registrazione, una volta sola

L'interceptor vale per ogni chiamata fatta con `HttpClient`. I service, come `BookService`, restano puliti.

File: `src/app/app.config.ts`

```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(
      withInterceptors([errorInterceptor]),
    ),
  ],
};
```

### 4. Componente e template

Il componente si limita a salvare il `ProblemDetail` in un signal: nessun `instanceof` e nessun parsing. Il template lo mostra.

- Azzera `error` prima di ogni nuova azione.
- Il blocco `@if` non deve stare dentro un commento HTML `<!-- -->`: lì Angular non compila nulla.

File: `src/app/app.ts` · `app.html`

```typescript
protected readonly error = signal<ProblemDetail | null>(null);

save() {
  this.error.set(null);
  call.subscribe({
    next: () => { ... },
    error: (p: ProblemDetail) => this.error.set(p),
  });
}
```

```html
<!-- app.html -->
@if (error(); as problem) {
  <section class="error" role="alert">
    <strong>{{ problem.status }} {{ problem.title }}</strong>
    @if (problem.detail) { <p>{{ problem.detail }}</p> }
    @for (e of problem.errors ?? []; track e.field) {
      <p>{{ e.field }}: {{ e.message }}</p>
    }
  </section>
}
```

## Verifica: ogni scenario, da capo a fondo

La colonna "origine" indica chi produce la risposta: un handler scritto da te, uno ereditato dalla classe base, oppure l'interceptor quando il backend non risponde.

| Scenario | Eccezione | Origine | Stato | Il riquadro mostra |
|----------|-----------|---------|-------|--------------------|
| `GET /api/books/999` | `BookNotFoundException` | handler tuo | 404 | Libro 999 inesistente |
| `POST` con ISBN già presente | `DuplicateIsbnException` | handler tuo | 409 | ISBN … già presente |
| `PUT` con l'ISBN di un altro libro | `DataIntegrityViolationException` | rete di sicurezza | 409 | Dati in conflitto |
| ISBN `123`, titolo vuoto | `MethodArgumentNotValidException` | override | 400 | una riga per campo: `isbn`, `title` |
| JSON malformato | `HttpMessageNotReadableException` | ereditato | 400 | Failed to read request |
| `GET /api/books/abc` | `MethodArgumentTypeMismatchException` | ereditato | 400 | tipo del parametro non valido |
| bug imprevisto | qualsiasi `Exception` | ultima istanza | 500 | Errore inatteso (dettagli solo nel log) |
| backend spento | nessuna: la richiesta non arriva | interceptor | 0 | Server non raggiungibile |

## Insidie: dove si sbaglia più spesso

- **Il client interpreta `detail`.** Una regex su "ISBN … già presente" si rompe alla prima riformulazione del testo. Metti `isbn` in un'estensione e decidi in base a `code` o a `type`.
- **Messaggi interni nella risposta.** SQL, nomi di tabelle, stack trace, `ex.getMessage()` di un'eccezione inattesa: vanno nel log, non al client.
- **Errori fuori dal DispatcherServlet.** Un'eccezione in un `Filter` (per esempio Spring Security) non arriva all'advice. La gestisce `BasicErrorController` su `/error`, con il JSON classico di Boot.
- **`try/catch` nei controller.** Ogni catch locale è un secondo punto di traduzione, e prima o poi produrrà una forma diversa dal contratto. Lascia risalire l'eccezione.
- **Test solo sui casi felici.** Il contratto d'errore fa parte dell'API. Verifica con `MockMvcTester` stato, `application/problem+json` e i campi su cui il client decide.
- **200 con `success: false`.** Nasconde l'errore a proxy, cache, monitoraggio e a `HttpClient`, che chiamerebbe `next` invece di `error`.

---

Basato sul progetto d'esercizio `gestione-eccezioni-spring-boot/basic`: CRUD dei libri, H2, backend sulla porta 8082. Riferimenti: RFC 9457 *Problem Details for HTTP APIs* e Spring Framework Reference, *Error Responses*.
