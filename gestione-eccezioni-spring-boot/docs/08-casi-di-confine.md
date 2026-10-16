# 08 – Casi di confine

> Il gestore centrale del capitolo 03 vede solo ciò che passa per `DispatcherServlet`
> sul thread della richiesta. Questo capitolo esamina gli errori che nascono **fuori**
> da quel perimetro: nei filtri e in Spring Security, nei thread asincroni, nelle
> chiamate a servizi esterni. Per ciascun caso indica come riportarli nello stesso
> contratto `ProblemDetail`.

## Il problema

Il perimetro di un `@ControllerAdvice` è preciso:

```
Client ─► Tomcat ─► [ Filtri: Security, CORS, … ] ─► DispatcherServlet ─► Controller
                     ▲ fuori dal perimetro            └──── perimetro degli advice ────┘
```

Tutto ciò che fallisce **a sinistra** di `DispatcherServlet`, o su **un altro
thread**, non arriva agli advice. Un'API coerente deve comunque rispondere nello
stesso formato, altrimenti il client si ritrova con un 401 vuoto, una pagina HTML o
un errore silenzioso.

## Teoria e meccanismo

### 1. Spring Security

#### Dove nascono 401 e 403

Spring Security è una catena di filtri (`FilterChainProxy`). Le eccezioni di sicurezza
vengono intercettate da **`ExceptionTranslationFilter`**, che le smista così:

| Eccezione | Situazione | Delegato |
|---|---|---|
| `AuthenticationException` | credenziali assenti o non valide | `AuthenticationEntryPoint` → di solito **401** |
| `AccessDeniedException` con utente anonimo | serve autenticarsi | `AuthenticationEntryPoint` → **401** |
| `AccessDeniedException` con utente autenticato | permessi insufficienti | `AccessDeniedHandler` → **403** |

C'è poi un secondo punto: con HTTP Basic, credenziali **errate** vengono gestite
direttamente da `BasicAuthenticationFilter`, che chiama l'entry point configurato
su `httpBasic(...)`, non quello di `exceptionHandling(...)`. Per avere un formato
unico bisogna configurarli **entrambi**:

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .anyRequest().permitAll())
    .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))      // credenziali errate
    .exceptionHandling(ex -> ex
        .authenticationEntryPoint(entryPoint)                            // credenziali assenti
        .accessDeniedHandler(accessDeniedHandler))                       // ruolo mancante
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .csrf(csrf -> csrf.disable());
```

Entry point e handler sono componenti minimi che delegano a un writer comune:

```java
@Override
public void commence(HttpServletRequest request, HttpServletResponse response,
        AuthenticationException authException) throws IOException {
    writer.write(ErrorCode.UNAUTHENTICATED, request, response);
}
```

Il writer usa la stessa `ProblemDetailFactory` dell'API e il `JsonMapper` configurato
da Spring Boot, così il JSON è identico a quello prodotto da Spring MVC:

```java
void write(ErrorCode code, HttpServletRequest request, HttpServletResponse response) throws IOException {
    ProblemDetail problem = problems.create(code, new Object[0], request,
            localeResolver.resolveLocale(request));
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    jsonMapper.writeValue(response.getOutputStream(), problem);
}
```

#### Un bug trovato dai test: la lingua nei filtri

La prima versione del writer usava `LocaleContextHolder`, come gli advice. Il test
`SicurezzaTest.senzaCredenziali` ha però ricevuto **"Authentication required"** al
posto di "Autenticazione richiesta", benché la richiesta non avesse
`Accept-Language` e il default dell'applicazione fosse l'italiano. Il motivo:

- nei filtri, `LocaleContextHolder` è valorizzato da `RequestContextFilter` (che in
  Boot precede la catena di Security) con **`request.getLocale()`**;
- `request.getLocale()` non conosce il `LocaleResolver` dell'applicazione: senza
  header restituisce la lingua di default *del container* (in MockMvc l'inglese, in
  Tomcat la lingua della JVM);
- il `LocaleResolver` con lingue supportate e default italiano viene applicato
  **solo da `DispatcherServlet`**, cioè dopo i filtri.

La correzione è risolvere la lingua esplicitamente con il `LocaleResolver`, come nel
codice sopra. È un esempio tipico di bug di confine: tutto funziona negli advice,
mentre fuori da `DispatcherServlet` un'ipotesi implicita smette di valere.

#### `WWW-Authenticate` e il popup del browser

La RFC 9110 (§15.5.2) stabilisce che un 401 **deve** contenere un header
`WWW-Authenticate` con almeno una *challenge*. L'entry point di default per HTTP Basic
(`BasicAuthenticationEntryPoint`) invia `WWW-Authenticate: Basic realm="Realm"`. Ma
quando un browser riceve quella challenge in risposta a una XHR/fetch, **apre il suo
dialogo nativo di login**: una pessima esperienza in una SPA che gestisce da sé
l'autenticazione.

Il progetto sceglie consapevolmente di **non inviare l'header**. È una deviazione
dalla RFC, comune nelle SPA e accettabile perché il client è sotto il nostro
controllo. Le alternative conformi sono:

- inviare una challenge con uno **schema non standard**
  (`WWW-Authenticate: ApiBasic realm="java-learning"`): il browser non la riconosce
  e non apre il popup;
- usare un meccanismo che non preveda la challenge Basic (token Bearer, cookie di
  sessione con login applicativo).

`SicurezzaTest` verifica che l'header sia assente sia senza credenziali sia con
credenziali errate.

#### Il dispatch `ERROR` e Security

Se un'eccezione sfugge e il container inoltra a `/error` (capitolo 02), il dispatch
`ERROR` riattraversa la catena di Security. In Spring Security 6+ le regole di
autorizzazione si applicano a **tutti** i tipi di dispatch. Se `/error` non è
permesso, l'utente riceve un **401 al posto dell'errore reale**, un errore che ne
nasconde un altro. Nel progetto `anyRequest().permitAll()` copre anche `/error`. In
configurazioni più restrittive va aggiunto esplicitamente:
`.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`.

### 2. Eccezioni nei filtri personalizzati

La stessa logica vale per qualsiasi `Filter` scritto a mano: un'eccezione lanciata lì
non vede gli advice e finisce a `/error`. Le strategie sono due:

- **catturare e scrivere** la risposta nel filtro, con un writer come quello sopra;
- **delegare all'infrastruttura MVC**, iniettando nel filtro il
  `HandlerExceptionResolver` di Spring
  (`@Qualifier("handlerExceptionResolver")`) e chiamando
  `resolver.resolveException(request, response, null, ex)`. Gli advice vengono così
  interrogati come se l'eccezione venisse da un controller. Un advice con selettori,
  però, non si applica (`handler` è `null`, si veda il capitolo 03).

### 3. Esecuzione asincrona

#### `@Async void`: nessuno a cui restituire l'errore

Un metodo `@Async` con tipo di ritorno `void` restituisce il controllo al chiamante
**prima** di eseguire. Se fallisce, non c'è uno stack di chiamata in cui propagare
l'eccezione. Spring la consegna all'**`AsyncUncaughtExceptionHandler`**. Quello di
default (`SimpleAsyncUncaughtExceptionHandler`) si limita a registrarla nei log a
livello ERROR. Per reagire (metriche, notifiche, nuovi tentativi) se ne configura uno
proprio con un `AsyncConfigurer`:

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return registro;   // registra metodo, eccezione e thread
    }
}
```

Il test verifica che l'eccezione arrivi all'handler su un **thread diverso** da quello
del chiamante e che il chiamante non ne veda traccia.

Un metodo `@Async` che restituisce `CompletableFuture<T>` non usa l'handler:
l'eccezione viene **catturata nel future**, e chi lo consuma la vedrà come
`ExecutionException` (con `get()`) o `CompletionException` (nelle composizioni).

#### `CompletableFuture` restituito da un controller

Spring MVC supporta i tipi asincroni come valore di ritorno (`CompletableFuture`,
`DeferredResult`, `Callable`). La richiesta viene sospesa (Servlet async) e ripresa
quando il future si completa. Se si completa **con un'eccezione**:

1. Spring estrae la causa da un'eventuale `CompletionException`;
2. la imposta come risultato (`DeferredResult.setErrorResult`);
3. al **dispatch `ASYNC`** di ripresa, la tratta come se il controller l'avesse
   lanciata: la catena dei resolver e gli advice la gestiscono normalmente.

Il laboratorio lo dimostra con un advice dedicato, che riceve
l'`IllegalStateException` originale e non la `CompletionException`.

Due insidie del mondo asincrono:

- **contesto non propagato**: `LocaleContextHolder`, `SecurityContextHolder` e MDC
  sono `ThreadLocal` e sul thread dell'executor sono vuoti, a meno di configurare un
  `TaskDecorator` (Spring Security offre `DelegatingSecurityContextExecutor`, Micrometer
  `ContextPropagation`);
- **timeout**: se il future non si completa entro
  `spring.mvc.async.request-timeout`, Spring lancia `AsyncRequestTimeoutException`
  (gestita da `ResponseEntityExceptionHandler` → 503).

### 4. Chiamate a servizi esterni con `RestClient`

`RestClient` (Spring 6.1+) segnala due famiglie di errori:

| Situazione | Eccezione di default |
|---|---|
| Risposta 4xx | `HttpClientErrorException` (sottotipi per stato: `NotFound`, `BadRequest`…) |
| Risposta 5xx | `HttpServerErrorException` |
| Nessuna risposta (connessione rifiutata, timeout, DNS) | `ResourceAccessException` |

Le prime due estendono `RestClientResponseException`, che contiene stato, header e
**corpo della risposta remota**. Proprio per questo non deve mai arrivare all'utente:
il corpo di un servizio guasto può contenere stack trace, URL interni o dati di altri
clienti. Il progetto simula un catalogo che, in errore, risponde con
`boom: NullPointerException at Catalog.java:42`, e verifica che nulla di questo arrivi
nella risposta.

Il client traduce ogni guasto in un'eccezione del **proprio** dominio
(`IntegrationException`), in modo che servizi e advice non dipendano dalla tecnologia
HTTP usata:

```java
try {
    return restClient.get()
        .uri(baseUrl + "/simulated-catalog/{isbn}?simulate={simulate}", isbn, simulate)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (request, response) -> {
            throw new IntegrationException(codeFor(response.getStatusCode()), response.getStatusCode().value());
        })
        .body(CatalogInfo.class);
} catch (ResourceAccessException ex) {
    throw new IntegrationException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, ex);
}
```

La scelta degli stati verso il **nostro** client:

| Guasto del servizio esterno | Stato restituito | Perché |
|---|---|---|
| risponde 5xx / 4xx inatteso | **502 Bad Gateway** | abbiamo ricevuto una risposta non valida da un upstream (RFC 9110 §15.6.3) |
| risponde 503 | **503 Service Unavailable** | indisponibilità temporanea: il client può riprovare |
| non risponde | **503** | non raggiungibile |

Una scelta più fine userebbe **504 Gateway Timeout** per i timeout. Il progetto li
accorpa al 503 per semplicità, perché l'azione suggerita all'utente ("riprova più
tardi") è la stessa.

Non va mai propagato lo **stesso stato** del servizio esterno: un 404 del catalogo non
significa che la *nostra* risorsa non esista, e un 401 del catalogo non significa che
il *nostro* utente non sia autenticato. Propagarlo ingannerebbe il client.

## Insidie e compromessi

- **Formati divergenti.** Ogni punto di uscita (advice, entry point, access denied
  handler, filtri) deve passare per la stessa factory. È l'unico modo per avere un
  contratto davvero uniforme.
- **Ipotesi implicite sul contesto.** Lingua, utente, traceId: tutto ciò che vive in un
  `ThreadLocal` può mancare fuori dal flusso standard. Il bug della lingua nei filtri ne
  è la prova.
- **Nuovi tentativi ciechi.** Riprovare una chiamata fallita (Spring Retry, Resilience4j)
  è sensato per 503 e timeout, non per 4xx. E solo per operazioni idempotenti.
- **Timeout di default.** Senza timeout configurati, un servizio esterno lento blocca i
  thread del server. In Spring Boot 4 i timeout dei client HTTP si impostano per tutta
  l'applicazione con le proprietà `spring.http.clients.*`.

## Nel progetto

| Concetto | Dove |
|---|---|
| Configurazione di Security | `backend/src/main/java/it/javalearning/eccezioni/web/sicurezza/SecurityConfig.java` |
| Entry point, access denied handler, writer | `backend/src/main/java/it/javalearning/eccezioni/web/sicurezza/` |
| Endpoint protetto | `backend/src/main/java/it/javalearning/eccezioni/api/AdminController.java` |
| Test 401/403, lingua, header | `backend/src/test/java/it/javalearning/eccezioni/web/sicurezza/SicurezzaTest.java` |
| Client e catalogo simulato | `backend/src/main/java/it/javalearning/eccezioni/integrazione/` |
| Test 502/503 e connessione rifiutata | `backend/src/test/java/it/javalearning/eccezioni/integrazione/CatalogClientTest.java`, `CatalogoIrraggiungibileTest.java` |
| `@Async` e `CompletableFuture` | `backend/src/main/java/it/javalearning/eccezioni/laboratorio/capitolo08/` |
| Test asincroni | `backend/src/test/java/it/javalearning/eccezioni/laboratorio/capitolo08/AsyncTest.java` |

Credenziali di prova: `utente` / `utente` (ruolo USER), `admin` / `admin` (ruoli USER e ADMIN).

```bash
curl -si localhost:8080/api/admin/stats                       # 401, senza WWW-Authenticate
curl -s  -u utente:utente localhost:8080/api/admin/stats      # 403
curl -s  -u admin:admin   localhost:8080/api/admin/stats      # 200
curl -s 'localhost:8080/api/books/1/catalog-info?simulate=error'        # 502
curl -s 'localhost:8080/api/books/1/catalog-info?simulate=unavailable'  # 503
curl -s localhost:8080/laboratorio/capitolo08/future                     # gestito dall'advice
```

## Riferimenti

- Spring Security Reference, [Handling Security Exceptions](https://docs.spring.io/spring-security/reference/servlet/architecture.html#servlet-exceptiontranslationfilter)
- Spring Security Reference, [Authorize HttpServletRequests → All Dispatches Are Authorized](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- RFC 9110, [§15.5.2 – 401 Unauthorized](https://www.rfc-editor.org/rfc/rfc9110#section-15.5.2), [§15.6.3 – 502](https://www.rfc-editor.org/rfc/rfc9110#section-15.6.3), [§15.6.4 – 503](https://www.rfc-editor.org/rfc/rfc9110#section-15.6.4)
- Spring Framework Reference, [Asynchronous Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
- Spring Framework Reference, [`@Async` Exception Management](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-exception)
- Spring Framework Reference, [RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
