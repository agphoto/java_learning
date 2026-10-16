# 02 – Il meccanismo interno di Spring MVC

> Un `@ExceptionHandler` sembra magia finché non si guarda chi lo invoca. Questo
> capitolo segue un'eccezione dal metodo del controller fino alla risposta HTTP,
> attraversando `DispatcherServlet`, la catena dei `HandlerExceptionResolver` e, se
> nessuno la gestisce, il container servlet e la pagina `/error`.

## Il problema

Sapere *dove* un'eccezione può essere intercettata serve per capire *perché* a volte
non lo è: un errore lanciato in un filtro, uno che il resolver trasforma in una
`sendError`, uno che viaggia in un dispatch asincrono. I casi di confine del
capitolo 08 si spiegano tutti con il meccanismo descritto qui.

## Teoria e meccanismo

Le versioni di riferimento sono Spring Framework 7.0 e Spring Boot 4.1. Il
meccanismo è sostanzialmente invariato dalla 5.x.

### 1. Il dispatch: dove l'eccezione viene catturata

`DispatcherServlet.doDispatch()` racchiude l'intero ciclo (risoluzione dell'handler,
interceptor `preHandle`, invocazione del metodo, `postHandle`) in un `try` che
cattura **qualunque** `Throwable`:

```java
// Schema semplificato di DispatcherServlet.doDispatch()
try {
    mappedHandler = getHandler(request);
    // ... preHandle, ha.handle(...), postHandle
} catch (Exception ex) {
    dispatchException = ex;
} catch (Throwable err) {
    // Gli Error (es. StackOverflowError) vengono avvolti per poter essere gestiti
    dispatchException = new ServletException("Handler dispatch failed: " + err, err);
}
processDispatchResult(request, response, mappedHandler, mv, dispatchException);
```

`processDispatchResult()`, se c'è un'eccezione, chiama `processHandlerException()`,
che interroga in ordine i `HandlerExceptionResolver` registrati. **Il primo che
restituisce un `ModelAndView` non nullo vince.** Un `ModelAndView` *vuoto* significa
"ho già scritto io la risposta". Se nessuno risponde, l'eccezione viene **rilanciata**
fuori dalla servlet.

### 2. La catena dei resolver

In un'applicazione Spring Boot la catena, in ordine, è:

| Ordine | Resolver | Cosa fa |
|---|---|---|
| `HIGHEST_PRECEDENCE` | `DefaultErrorAttributes` (Boot) | Non gestisce nulla: salva l'eccezione in un attributo di request, perché `/error` possa leggerla più tardi. Restituisce sempre `null`. |
| 0 | `HandlerExceptionResolverComposite`, che contiene: | |
| | ① `ExceptionHandlerExceptionResolver` | Cerca un metodo `@ExceptionHandler` adatto: prima nel controller che ha lanciato, poi negli `@ControllerAdvice` applicabili (capitolo 03). |
| | ② `ResponseStatusExceptionResolver` | Gestisce `ResponseStatusException` e le eccezioni annotate con `@ResponseStatus`, chiamando `response.sendError(status, reason)`. |
| | ③ `DefaultHandlerExceptionResolver` | Traduce le eccezioni standard di Spring MVC (`HttpRequestMethodNotSupportedException` → 405, `HttpMessageNotReadableException` → 400…) in `sendError`. |

Due osservazioni importanti.

**I resolver ② e ③ non scrivono un corpo.** Chiamano `HttpServletResponse.sendError()`,
che marca la risposta come errore e delega al container la generazione della pagina.
Il corpo arriva quindi, ancora una volta, da `/error`. Per questo, senza un advice,
un JSON malformato produce la risposta di `BasicErrorController` e non un
`ProblemDetail`. Con un advice che estende `ResponseEntityExceptionHandler`, invece,
le stesse eccezioni vengono intercettate prima, dal resolver ① (capitolo 03).

**Un handler non trovato è anch'esso un'eccezione.** Da Spring 6.1, una richiesta per
un URL che nessun controller gestisce finisce al `ResourceHttpRequestHandler` delle
risorse statiche, che lancia `NoResourceFoundException`. Anche lei attraversa la
catena dei resolver e può quindi diventare un `ProblemDetail` (capitolo 03).

### 3. Quando nessuno gestisce: il container e `/error`

Un'eccezione che esce dalla servlet (o una `sendError`) arriva al container. Tomcat,
nella `StandardHostValve`, cerca una *error page* registrata per quel tipo di
eccezione o per quello stato. Spring Boot ne registra una globale che punta a
`server.error.path` (default `/error`) e il container esegue un **dispatch di tipo
`ERROR`** verso quell'URL, con gli attributi standard
`jakarta.servlet.error.status_code`, `jakarta.servlet.error.exception`,
`jakarta.servlet.error.request_uri`.

A `/error` risponde `BasicErrorController`
(`org.springframework.boot.webmvc.autoconfigure.error`):

- con `Accept: text/html` restituisce la **Whitelabel Error Page**;
- altrimenti restituisce un JSON costruito da `ErrorAttributes`.

Il contenuto del JSON è governato dalle proprietà `server.error.*`. Per ragioni di
sicurezza i default sono restrittivi:

| Proprietà | Default | Effetto |
|---|---|---|
| `server.error.include-message` | `never` | il messaggio dell'eccezione non compare |
| `server.error.include-binding-errors` | `never` | gli errori di binding non compaiono |
| `server.error.include-stacktrace` | `never` | lo stack trace non compare |
| `server.error.include-exception` | `false` | il nome della classe non compare |
| `server.error.whitelabel.enabled` | `true` | la pagina HTML di default è attiva |

Il risultato è un JSON di questa forma, diverso da un `ProblemDetail`:

```json
{ "timestamp": "2026-10-16T10:15:30.000+00:00", "status": 500,
  "error": "Internal Server Error", "path": "/laboratorio/capitolo02/unhandled" }
```

### 4. Perché il dispatch `ERROR` conta

Il dispatch verso `/error` è una **nuova richiesta interna** che attraversa di nuovo
la catena dei filtri registrati per il tipo `ERROR`. Due conseguenze pratiche:

- se Spring Security non permette l'accesso a `/error`, l'utente riceve un **401 al
  posto dell'errore reale**. Spring Boot autorizza automaticamente i dispatch `ERROR`
  nella configurazione di default, ma una configurazione personalizzata deve tenerne
  conto (capitolo 08);
- nei test con **MockMvc il dispatch `ERROR` non avviene**: MockMvc non è un container.
  Un test che vuole osservare `BasicErrorController` deve usare un server reale.

## Il codice

Nel laboratorio un controller vive volutamente **fuori** dal package `api`, e quindi
fuori dall'ambito dell'advice centrale (`basePackages = "it.javalearning.eccezioni.api"`):

```java
@RestController
public class UnhandledController {

    @GetMapping("/laboratorio/capitolo02/unhandled")
    public String unhandled() {
        throw new IllegalStateException("nessuno mi gestisce");
    }
}
```

Il test avvia Tomcat su una porta casuale e osserva la risposta di
`BasicErrorController`:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FallbackErrorControllerTest {
    @LocalServerPort int port;

    @Test
    void eccezioneNonGestitaArrivaABasicErrorController() {
        var response = RestClient.create("http://localhost:" + port)
            .get().uri("/laboratorio/capitolo02/unhandled")
            .retrieve().onStatus(s -> true, (req, res) -> { })
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() { });

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody())
            .containsKeys("timestamp", "status", "error", "path")
            .doesNotContainKeys("trace", "message", "exception");
    }
}
```

## Insidie e compromessi

- **Affidarsi a `/error` come unico gestore.** Funziona e non espone dettagli, ma il
  formato non è standard, non è localizzabile senza riscrivere `ErrorAttributes` e
  produce doppie elaborazioni (eccezione, poi dispatch `ERROR`). In un'API conviene
  gestire tutto nel resolver ① e lasciare `/error` come **rete di sicurezza** per
  ciò che sfugge (eccezioni nei filtri, errori del container).
- **`server.error.include-message=always` in produzione.** È comodo in sviluppo, ma
  espone messaggi come `could not execute statement [Unique index or primary key
  violation: "PUBLIC.UK_BOOK_ISBN ..."]`. Va confinato a un profilo di sviluppo.
- **Risposta già committed.** Se il controller ha già iniziato a scrivere il corpo
  (streaming) e poi fallisce, nessun resolver può più cambiare lo stato HTTP: la
  risposta è già partita. È un limite strutturale di HTTP/1.1, non di Spring.
- **Errori `Error`.** Un `StackOverflowError` viene avvolto in una `ServletException`.
  `ExceptionHandlerExceptionResolver` sa confrontare anche le cause, ma gestire gli
  `Error` è quasi sempre una cattiva idea: lo stato della JVM potrebbe non essere
  affidabile.

## Nel progetto

| Concetto | Dove |
|---|---|
| Controller fuori dall'ambito dell'advice | `backend/src/main/java/it/javalearning/eccezioni/laboratorio/capitolo02/UnhandledController.java` |
| Fallback su `BasicErrorController` con server reale | `backend/src/test/java/it/javalearning/eccezioni/laboratorio/capitolo02/FallbackErrorControllerTest.java` |

Prova manuale, con il backend avviato:

```bash
curl -s http://localhost:8080/laboratorio/capitolo02/unhandled
curl -s -H 'Accept: text/html' http://localhost:8080/laboratorio/capitolo02/unhandled   # Whitelabel
```

## Riferimenti

- Spring Framework Reference, [DispatcherServlet → Exceptions](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/exceptionhandlers.html)
- Sorgenti: [`DispatcherServlet`](https://github.com/spring-projects/spring-framework/blob/main/spring-webmvc/src/main/java/org/springframework/web/servlet/DispatcherServlet.java), [`DefaultHandlerExceptionResolver`](https://github.com/spring-projects/spring-framework/blob/main/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/support/DefaultHandlerExceptionResolver.java)
- Spring Boot Reference, [Servlet Web Applications → Error Handling](https://docs.spring.io/spring-boot/reference/web/servlet.html#web.servlet.spring-mvc.error-handling)
- Sorgenti Boot: `BasicErrorController`, `DefaultErrorAttributes` nel modulo [`spring-boot-webmvc`](https://github.com/spring-projects/spring-boot/tree/main/module/spring-boot-webmvc)
- Jakarta Servlet 6.1 Specification, §10.9 *Error Handling*
