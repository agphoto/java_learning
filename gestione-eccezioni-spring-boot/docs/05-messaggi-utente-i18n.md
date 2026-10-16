# 05 – Il messaggio per l'utente e l'internazionalizzazione

> Il `ProblemDetail` ha una struttura standard, ma il suo valore per una persona sta
> in due campi: `title` e `detail`. Questo capitolo tratta la differenza tra
> messaggio tecnico e messaggio per l'utente, le due strategie possibili per la
> traduzione e il modo in cui Spring risolve i testi nella lingua della richiesta.

## Il problema

La stessa eccezione deve produrre **due testi diversi per due destinatari diversi**:

| | Messaggio tecnico | Messaggio per l'utente |
|---|---|---|
| Destinatario | sviluppatore, operatore | persona che usa l'app |
| Dove finisce | log, traccia | interfaccia |
| Lingua | una sola (inglese) | quella dell'utente |
| Contenuto | identificativi, stato interno, causa | cosa è successo e cosa fare |
| Esempio | `LOAN_LIMIT_EXCEEDED args=[3, 3]` | "Hai già 3 libri in prestito: restituiscine uno prima di prenderne un altro." |

Usare `ex.getMessage()` come `detail` mescola i due mondi: l'utente legge testo
tecnico, in inglese, che potrebbe contenere dati interni. Lo sviluppatore si trova
invece messaggi "gentili" nei log, difficili da cercare.

Un buon messaggio per l'utente risponde a tre domande: **che cosa** è successo,
**perché** (se è utile saperlo) e **che cosa può fare**. Non attribuisce colpe ("hai
sbagliato") e non contiene gergo ("constraint violation").

## Teoria e meccanismo

### Due strategie per la traduzione

**Strada A: il backend traduce.** Il client invia `Accept-Language` e il server
risponde con `title` e `detail` già nella lingua giusta.

- ✅ Una sola fonte dei testi, vicina a chi conosce il significato dell'errore.
- ✅ Funziona con qualsiasi client (app web, mobile, `curl`, altri servizi).
- ✅ I parametri (`{0}` = 3 libri) vengono inseriti dove sono noti.
- ❌ Se l'utente cambia lingua, un errore già visualizzato resta nella lingua precedente.
- ❌ Aggiungere una lingua richiede un rilascio del backend.

**Strada B: il client traduce.** Il server manda solo `code` (e parametri
strutturati); il client ha un dizionario `code → testo`.

- ✅ Cambio di lingua istantaneo, anche sugli errori già visibili.
- ✅ Il team frontend controlla il tono e i testi.
- ❌ Due cataloghi da tenere allineati: un nuovo `code` lato server senza traduzione
  lato client diventa un messaggio generico.
- ❌ Ogni client (web, mobile…) deve replicare il dizionario.
- ❌ I parametri vanno esposti come estensioni strutturate (`"limit": 3`), non come
  testo.

**La scelta del progetto è la strada A, con `code` stabile sempre presente.** Il
backend è la fonte unica dei testi, mentre il `code` consente al client di prendere
decisioni (dove mostrare l'errore, se proporre un'azione) e lascia aperta la strada
B per un client che la preferisca. Il frontend (capitolo 10) mostra `title` e `detail`
così come arrivano, e traduce da sé solo gli errori in cui il server non ha risposto.

### Da `Accept-Language` a `Locale`

Spring MVC determina la lingua con un **`LocaleResolver`**. Un filtro
(`RequestContextFilter`) e poi `DispatcherServlet` lo interrogano a ogni richiesta e
salvano il risultato in `LocaleContextHolder`, un `ThreadLocal` leggibile da
qualunque punto del codice che gira sullo stesso thread.

| Implementazione | Fonte della lingua |
|---|---|
| `AcceptHeaderLocaleResolver` (default) | header `Accept-Language` |
| `CookieLocaleResolver` | un cookie, con fallback all'header |
| `SessionLocaleResolver` | un attributo di sessione |
| `FixedLocaleResolver` | sempre la stessa |

Per un'API stateless con un client SPA la scelta naturale è l'header. Il progetto lo
configura così:

```java
@Bean
LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setSupportedLocales(List.of(Locale.ITALIAN, Locale.ENGLISH));
    resolver.setDefaultLocale(Locale.ITALIAN);
    return resolver;
}
```

L'header può contenere più lingue con pesi (`en-US,en;q=0.9,it;q=0.8`, RFC 9110
§12.5.4). Il container le ordina per peso (`request.getLocales()`) e
`AcceptHeaderLocaleResolver`, se ci sono `supportedLocales`, cerca **prima una
corrispondenza esatta, poi una sulla sola lingua**: `en-US` diventa `en`. Se nessuna
corrisponde, usa il `defaultLocale`. Il test `LocalizzazioneTest` copre i casi
`en-US,en;q=0.9` → inglese, `fr-FR` → italiano e header assente → italiano.

### `MessageSource`

Spring Boot crea un `MessageSource` (un `ResourceBundleMessageSource`) se trova
`messages.properties` nel classpath. Le proprietà `spring.messages.*` lo configurano.
Due di esse sono **essenziali** e poco note.

**`spring.messages.fallback-to-system-locale=false`.** La ricerca di un bundle per
`it` segue la catena di `java.util.ResourceBundle`: `messages_it` → *lingua di
sistema* → `messages`. Il progetto non ha `messages_it.properties`, perché l'italiano
sta nel file base. Con il valore di default (`true`), su una macchina con locale di
sistema inglese, una richiesta in italiano troverebbe `messages_en.properties` e
risponderebbe **in inglese**. Il bug si manifesta solo su certi server, ed è il tipo
più difficile da diagnosticare.

**`spring.messages.always-use-message-format=true`.** Per default Spring applica
`java.text.MessageFormat` **solo** ai messaggi con argomenti. Ma `MessageFormat`
tratta l'apostrofo come carattere di quoting: `L'indirizzo {0}` diventa
`Lindirizzo {0}`, con il segnaposto non sostituito. Si avrebbe quindi una regola
diversa per i testi con argomenti e per quelli senza. Con l'opzione attiva la regola
è una sola: **ogni apostrofo si scrive `''`**. Un test (`MessaggiCompletiTest`)
verifica che nei file non ci siano apostrofi singoli.

### Le chiavi del progetto

Per ogni `ErrorCode` esistono due chiavi in ciascuna lingua:

```properties
error.loan-limit-exceeded.title=Limite di prestiti superato
error.loan-limit-exceeded.detail=Hai già {0} libri in prestito: restituiscine uno prima di prenderne un altro.
```

La `ProblemDetailFactory` le risolve con gli argomenti dell'eccezione e la lingua
corrente:

```java
private void applyCatalog(ProblemDetail problem, ErrorCode code, Object[] args) {
    Locale locale = LocaleContextHolder.getLocale();
    problem.setType(code.type());
    problem.setTitle(messageSource.getMessage(code.messageKey() + ".title", null, locale));
    problem.setDetail(messageSource.getMessage(code.messageKey() + ".detail", args, locale));
}
```

Un test parametrico verifica che **ogni** `ErrorCode` abbia entrambe le chiavi in
entrambe le lingue. Aggiungere un codice senza i testi rompe la build, non
l'interfaccia in produzione.

### I codici di messaggio di `ErrorResponse`

Anche Spring prevede la localizzazione per le proprie eccezioni. Ogni `ErrorResponse`
espone tre codici, risolti da `ResponseEntityExceptionHandler` tramite il suo
`MessageSource`:

| Codice | Default | Uso |
|---|---|---|
| `getTitleMessageCode()` | `problemDetail.title.` + nome completo della classe | `title` |
| `getDetailMessageCode()` | `problemDetail.` + nome completo della classe | `detail` |
| `getTypeMessageCode()` | `problemDetail.type.` + nome completo della classe | `type` |

Gli argomenti di `detail` vengono da `getDetailMessageArguments()`. Per
`HttpMediaTypeNotSupportedException`, per esempio, sono il content type ricevuto e
quelli supportati. Il progetto usa questo meccanismo per le eccezioni standard **non**
mappate su un `ErrorCode`:

```properties
problemDetail.title.org.springframework.web.HttpMediaTypeNotSupportedException=Tipo di contenuto non supportato
problemDetail.org.springframework.web.HttpMediaTypeNotSupportedException=Il tipo di contenuto {0} non è supportato.
```

Per quelle **mappate** (JSON illeggibile, parametro del tipo sbagliato, URL
inesistente, metodo non supportato) il catalogo `error.*` prevale, così che lo stesso
`code` abbia sempre lo stesso testo, indipendentemente da dove nasce.

## Insidie e compromessi

- **`LocaleContextHolder` fuori dal thread della richiesta.** In un `@Async`, in un
  `CompletableFuture` o nei filtri che precedono `RequestContextFilter` la lingua
  non è disponibile (o è quella di default). Va passata esplicitamente, oppure si
  configura un `TaskDecorator` che la propaghi.
- **Messaggi di dominio costruiti con concatenazione.** `"Hai già " + n + " libri"`
  non si traduce, perché l'ordine delle parole cambia da lingua a lingua. Gli
  argomenti vanno sempre passati al `MessageSource`.
- **Plurali.** "1 libri" è un errore. `MessageFormat` supporta `ChoiceFormat`
  (`{0,choice,1#un libro|1<{0} libri}`), mentre per regole complesse (lingue slave,
  arabo) serve ICU4J. Nel progetto il limite è 3, quindi il plurale basta.
- **Testi nel codice Java.** Anche "solo per ora" diventano permanenti. Ogni testo per
  l'utente va nel catalogo.
- **Header `Content-Language`.** La risposta potrebbe dichiarare la lingua scelta.
  Spring non lo aggiunge per gli errori. Il client del progetto non ne ha bisogno,
  perché è lui a sceglierla.

## Nel progetto

| Concetto | Dove |
|---|---|
| `LocaleResolver` | `backend/src/main/java/it/javalearning/eccezioni/web/LocaleConfig.java` |
| Cataloghi | `backend/src/main/resources/messages.properties`, `messages_en.properties` |
| Proprietà `spring.messages.*` | `backend/src/main/resources/application.properties` |
| Risoluzione dei testi | `backend/src/main/java/it/javalearning/eccezioni/web/errore/ProblemDetailFactory.java` |
| Test sulle lingue e sulla negoziazione | `backend/src/test/java/it/javalearning/eccezioni/web/errore/LocalizzazioneTest.java` |
| Test di completezza dei cataloghi | `backend/src/test/java/it/javalearning/eccezioni/web/errore/MessaggiCompletiTest.java` |

```bash
curl -s -X POST localhost:8081/api/loans -H 'Content-Type: application/json' \
     -H 'Accept-Language: en-US,en;q=0.9' -d '{"userId":2,"bookId":1}' | jq .title
curl -s -X POST localhost:8081/api/loans -H 'Content-Type: application/json' \
     -H 'Accept-Language: fr' -d '{"userId":2,"bookId":1}' | jq .title     # italiano
```

## Riferimenti

- Spring Framework Reference, [Internationalization using MessageSource](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-messagesource)
- Spring Framework Reference, [Locale (Spring MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/localeresolver.html)
- Spring Framework Reference, [Error Responses → Internationalization](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html#mvc-ann-rest-exceptions-i18n)
- Spring Boot Reference, [Internationalization](https://docs.spring.io/spring-boot/reference/features/internationalization.html)
- Javadoc di [`MessageFormat`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/MessageFormat.html) (sezione sulle virgolette) e di [`ResourceBundle.Control`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ResourceBundle.Control.html) (catena di fallback)
- RFC 9110, [§12.5.4 – Accept-Language](https://www.rfc-editor.org/rfc/rfc9110#section-12.5.4)
