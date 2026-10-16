# 03 – Gestione centralizzata

> L'obiettivo è che ogni controller lanci eccezioni e basta, senza `try/catch` né
> `ResponseEntity` d'errore costruite a mano, e che un unico componente decida come
> ogni errore diventa una risposta. In Spring MVC quel componente è un
> `@ControllerAdvice`. Questo capitolo ne spiega le regole: quale handler viene
> scelto, quando un advice si applica e cosa offre `ResponseEntityExceptionHandler`.

## Il problema

Senza gestione centralizzata ogni controller reinventa il formato dell'errore, i
codici di stato e il logging. Il risultato sono API incoerenti, eccezioni catturate
e "perse" e messaggi tecnici che arrivano all'utente. Centralizzare significa
spostare **una decisione di presentazione** (come appare un errore) fuori dalla
logica applicativa (quando si verifica).

## Teoria e meccanismo

### `@ExceptionHandler`

Un metodo annotato con `@ExceptionHandler` viene invocato da
`ExceptionHandlerExceptionResolver` (capitolo 02) quando l'eccezione lanciata è
assegnabile a uno dei tipi dichiarati. Il tipo si ricava dall'annotazione o, se
manca, dai parametri del metodo. La firma è flessibile quanto quella di un metodo
`@RequestMapping`: può ricevere l'eccezione, `HttpServletRequest`, `WebRequest`,
`Locale`, e restituire `ResponseEntity`, `ProblemDetail`, `ErrorResponse`, un oggetto
serializzato con `@ResponseBody` o una vista.

Il resolver confronta anche la **causa**. Se nessun handler corrisponde all'eccezione
di primo livello, prova con `getCause()`, risalendo la catena. È il motivo per cui un
`Error` avvolto in `ServletException` può ancora essere gestito.

### Dove viene cercato l'handler: l'ordine di selezione

`ExceptionHandlerExceptionResolver.getExceptionHandlerMethod()` segue questo ordine:

1. **Il controller stesso** (e le sue superclassi). Un `@ExceptionHandler` locale vince
   sempre su qualunque advice.
2. **Gli advice applicabili**, nell'ordine dato da `@Order` / `Ordered` (valore più
   basso = interrogato prima). Per ciascuno si controlla se ha un handler compatibile;
   **il primo advice che ne ha uno vince**.
3. **Dentro un singolo advice** (o controller), se più metodi sono compatibili, vince
   quello con il tipo più vicino all'eccezione nella gerarchia
   (`ExceptionDepthComparator`).

Il punto 2 contiene una sottigliezza che sorprende spesso: **l'ordine degli advice
prevale sulla specificità**. Se `PrimoAdvice` (`@Order(1)`) gestisce
`RuntimeException` e `SecondoAdvice` (`@Order(2)`) gestisce `IllegalStateException`,
un'`IllegalStateException` va a `PrimoAdvice`. La specificità vale solo *dentro* lo
stesso advice. Il laboratorio lo dimostra:

```java
@RestControllerAdvice(assignableTypes = { ScopedController.class, LocalHandlerController.class })
@Order(1)
public class PrimoAdvice {
    @ExceptionHandler(RuntimeException.class)          // risponde anche per IllegalStateException
    ResponseEntity<String> runtime(RuntimeException ex) { ... }

    @ExceptionHandler(IllegalArgumentException.class)  // più specifico: vince su runtime()
    ResponseEntity<String> illegalArgument(IllegalArgumentException ex) { ... }
}

@RestControllerAdvice(assignableTypes = ScopedController.class)
@Order(2)
public class SecondoAdvice {
    @ExceptionHandler(IllegalStateException.class)     // mai raggiunto per ScopedController
    ResponseEntity<String> illegalState(IllegalStateException ex) { ... }
}
```

Senza `@Order` l'ordine tra advice è **indefinito** (dipende dall'ordine di
registrazione dei bean). Con più di un advice applicabile allo stesso controller va
sempre dichiarato.

### `@ControllerAdvice` e `@RestControllerAdvice`

`@RestControllerAdvice` è `@ControllerAdvice` + `@ResponseBody`: il valore restituito
dagli handler viene serializzato con gli `HttpMessageConverter` invece di essere
interpretato come nome di vista. Per un'API REST è la scelta naturale.

### Ambito di un advice

Per default un advice si applica a **tutti** i controller. Gli attributi
dell'annotazione lo restringono (si combinano in OR):

| Attributo | Si applica ai controller… |
|---|---|
| `basePackages` / `basePackageClasses` | nei package indicati |
| `assignableTypes` | assegnabili ai tipi indicati |
| `annotations` | annotati con le annotazioni indicate |

Il controllo avviene in `ControllerAdviceBean.isApplicableToBeanType()`, tramite
`HandlerTypePredicate`. Il dettaglio che conta: **il predicato riceve il tipo del
controller che ha gestito la richiesta**. Se non c'è un controller, il tipo è `null`
e un advice con selettori **non si applica**.

### Il caso degli errori "di routing"

Quando succede di non avere un controller? Quando l'errore nasce **durante la ricerca
dell'handler**:

- URL che nessun controller mappa → `NoResourceFoundException` (lanciata dal
  gestore delle risorse statiche, da Spring 6.1);
- URL mappato ma con un metodo HTTP diverso → `HttpRequestMethodNotSupportedException`;
- media type non supportato o non accettabile, in alcuni casi.

Il gestore centrale del progetto è ristretto a `basePackages =
"it.javalearning.eccezioni.api"`. Per questi errori quindi non interviene: la
`DefaultHandlerExceptionResolver` chiama `sendError` e la risposta arriva da
`BasicErrorController`, non da un `ProblemDetail`. Il progetto lo verifica: senza
l'advice descritto sotto, `GET /api/nonexistent` e `DELETE /api/books/1`
restituiscono una risposta senza `Content-Type: application/problem+json`.

La soluzione adottata è un **secondo advice, senza selettori e con la precedenza più
bassa**, che gestisce *solo* queste eccezioni:

```java
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class RoutingErrorAdvice {

    @ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class })
    ResponseEntity<ProblemDetail> handleRouting(Exception ex, HttpServletRequest request) {
        ErrorResponse error = (ErrorResponse) ex;
        ProblemDetail problem = error.updateAndGetBody(messageSource, LocaleContextHolder.getLocale());
        problems.enrich(problem, StandardExceptionCodes.codeFor(ex, error.getStatusCode()), request);
        return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders()).body(problem);
    }
}
```

Si noti `headers(error.getHeaders())`: per un 405 l'eccezione porta con sé l'header
`Allow` (RFC 9110 §15.5.6), che la risposta deve includere.

L'alternativa più semplice sarebbe un unico advice senza selettori. Qui però si è
preferito restringere l'ambito per tenere separati i controller del laboratorio, che
dimostrano proprio i comportamenti *senza* gestore. In un'applicazione reale un unico
advice globale è spesso la scelta giusta.

### `ResponseEntityExceptionHandler`

È una classe base astratta che contiene già un `@ExceptionHandler` per **tutte le
eccezioni standard di Spring MVC** (`HttpMessageNotReadableException`,
`MethodArgumentNotValidException`, `TypeMismatchException`,
`HttpRequestMethodNotSupportedException`, `NoResourceFoundException`,
`AsyncRequestTimeoutException`…, l'elenco completo è nel Javadoc). Per ognuna:

1. ottiene il `ProblemDetail` di default (quasi tutte implementano `ErrorResponse`);
2. ne risolve `title` e `detail` tramite il `MessageSource` (capitolo 05);
3. delega a un metodo `protected handleXxx(...)` sovrascrivibile;
4. converge su **`handleExceptionInternal(...)`**, il punto unico in cui si può
   intervenire su tutte le risposte.

Estenderla offre due vantaggi: **copertura completa** (non si dimentica nessuna
eccezione standard) e **formato uniforme**. Nel progetto l'override di
`handleExceptionInternal` aggiunge `code`, `timestamp` e `traceId` a ogni
`ProblemDetail` prodotto da Spring:

```java
@Override
protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
        HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null && response.getBody() instanceof ProblemDetail problem
            && request instanceof NativeWebRequest nativeRequest) {
        HttpServletRequest servletRequest = nativeRequest.getNativeRequest(HttpServletRequest.class);
        problems.enrich(problem, StandardExceptionCodes.codeFor(ex, statusCode), servletRequest);
    }
    return response;
}
```

Spring Boot offre anche `spring.mvc.problemdetails.enabled=true`, che registra
automaticamente un `ResponseEntityExceptionHandler` vuoto. Il progetto **non** la
usa: la proprietà crea l'advice solo se non ne esiste già uno di quel tipo, e qui ce
n'è uno personalizzato. Attivarla in aggiunta sarebbe inutile.

### Eccezioni che portano con sé la risposta

Spring offre due eccezioni pensate per essere lanciate da un controller:

- **`ResponseStatusException`**: stato + motivo, pronta all'uso.
  `throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Libro non trovato")`.
- **`ErrorResponseException`**: la superclasse generale. Porta un `ProblemDetail`
  completo, header e codici di messaggio per la localizzazione.

Entrambe implementano `ErrorResponse` e vengono gestite da
`ResponseEntityExceptionHandler`. Sono comode per prototipi e controller sottili, ma
legano il **livello di servizio** a HTTP. Il progetto preferisce eccezioni di dominio
con un `ErrorCode` (capitolo 01), lasciando la traduzione in HTTP al solo advice.

### Approcci storici

- **`@ResponseStatus` sull'eccezione** (Spring 3.0). L'annotazione su una classe
  d'eccezione viene letta da `ResponseStatusExceptionResolver`, che chiama
  `sendError`. Il corpo arriva quindi da `/error`, non si può personalizzare e la
  classe di dominio dipende da Spring Web. Oggi va considerato superato dagli
  approcci precedenti.
- **`HandlerExceptionResolver` personalizzato** o `SimpleMappingExceptionResolver`.
  Utile solo per esigenze molto particolari, per esempio mappare eccezioni su viste
  in applicazioni server-side.

## Insidie e compromessi

- **`@ExceptionHandler(Exception.class)` in un advice che estende
  `ResponseEntityExceptionHandler`** non intercetta le eccezioni standard: dentro lo
  stesso advice vince l'handler più specifico, cioè quello della classe base. È il
  comportamento voluto.
- **Due advice entrambi applicabili e senza `@Order`**: l'ordine è indefinito e il
  comportamento può cambiare tra un avvio e l'altro, o con l'aggiunta di un bean.
- **Advice con selettori ed errori di routing**: come visto sopra, non si applicano.
- **Gestire un'eccezione e rilanciarla.** Un handler che lancia a sua volta
  un'eccezione non viene ritentato: l'eccezione nuova risale fino al container
  (`/error`).
- **Catturare per poi restituire `null`** in un `HandlerExceptionResolver`
  personalizzato significa "non gestita"; restituire un `ModelAndView` vuoto
  significa "gestita, risposta già scritta". Confonderli porta a risposte vuote con
  stato 200.

## Nel progetto

| Concetto | Dove |
|---|---|
| Gestore centrale | `backend/src/main/java/it/javalearning/eccezioni/web/errore/GlobalExceptionHandler.java` |
| Errori di routing (advice senza selettori) | `backend/src/main/java/it/javalearning/eccezioni/web/errore/RoutingErrorAdvice.java` |
| Code per le eccezioni standard | `backend/src/main/java/it/javalearning/eccezioni/web/errore/StandardExceptionCodes.java` |
| Handler locale, ordine e specificità | `backend/src/main/java/it/javalearning/eccezioni/laboratorio/capitolo03/` |
| Test delle regole di selezione | `backend/src/test/java/it/javalearning/eccezioni/laboratorio/capitolo03/AmbitoEOrdineTest.java` |
| Contratto delle risposte (404, 405, 400, 409, 422, 500) | `backend/src/test/java/it/javalearning/eccezioni/web/errore/ProblemDetailContractTest.java` |

```bash
curl -s http://localhost:8080/laboratorio/capitolo03/state      # primo: RuntimeException
curl -s http://localhost:8080/api/nonexistent                    # 404 problem+json
curl -si -X DELETE http://localhost:8080/api/books/1             # 405 con header Allow
```

## Riferimenti

- Spring Framework Reference, [Exceptions (`@ExceptionHandler`)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html)
- Spring Framework Reference, [Controller Advice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)
- Spring Framework Reference, [Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- Javadoc di [`ResponseEntityExceptionHandler`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html)
- Sorgenti: [`ExceptionHandlerExceptionResolver`](https://github.com/spring-projects/spring-framework/blob/main/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/method/annotation/ExceptionHandlerExceptionResolver.java), [`HandlerTypePredicate`](https://github.com/spring-projects/spring-framework/blob/main/spring-web/src/main/java/org/springframework/web/method/HandlerTypePredicate.java)
- RFC 9110, [§15.5.6 – 405 Method Not Allowed](https://www.rfc-editor.org/rfc/rfc9110#section-15.5.6)
