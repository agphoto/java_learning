# 09 – Logging e observability

> Il `ProblemDetail` è la faccia dell'errore rivolta all'utente. I log e le tracce sono
> quella rivolta a chi deve capire cosa è successo. Questo capitolo tratta cosa
> registrare, a quale livello, una sola volta, e come collegare la risposta vista
> dall'utente alla riga di log giusta.

## Il problema

Tre difetti ricorrenti:

1. **Log troppo rumorosi.** Ogni 404 registrato come ERROR con 80 righe di stack trace.
   I veri problemi si perdono, e gli allarmi basati sul numero di ERROR scattano per
   colpa degli utenti che sbagliano URL.
2. **Log duplicati.** La stessa eccezione registrata dal servizio, poi dal controller,
   poi dall'advice. Tre stack trace per un solo evento, e conteggi falsati.
3. **Nessun collegamento.** L'utente segnala "ho ricevuto un errore alle 10:15", e
   nei log ci sono 400 errori in quel minuto.

## Teoria e meccanismo

### Che livello per quale errore

Il criterio è: **chi deve intervenire?**

| Situazione | Chi interviene | Livello | Stack trace |
|---|---|---|---|
| 5xx: bug, risorsa guasta, servizio esterno in errore | il team, ora | **ERROR** | sì: serve per la diagnosi |
| 4xx: input non valido, risorsa inesistente, regola violata, credenziali errate | nessuno: l'applicazione funziona come previsto | **WARN** (o INFO) | no: è sempre lo stesso punto, non aggiunge nulla |

Alcuni preferiscono INFO o DEBUG per i 4xx. WARN è un compromesso: si vede, ma non
fa scattare gli allarmi sugli ERROR. Un'impennata di 4xx può comunque segnalare un
problema reale (un client rilasciato con un bug, un attacco di forza bruta sul 401),
e per questo va monitorata con **metriche** più che letta nei log.

### Registrare una sola volta, nel punto giusto

La regola "*log or throw, never both*" ha un corollario pratico: in un'applicazione
con gestione centralizzata **l'unico punto che registra le eccezioni propagate è il
gestore centrale**. Servizi e repository lanciano e basta. Si registra più in basso
solo ciò che si **cattura e non si propaga** (un fallback, un nuovo tentativo).

Il progetto concentra la politica in un componente, usato da tutti i punti di uscita:
gli advice, il writer di Spring Security e il gestore degli errori di routing.

```java
@Component
public class ErrorLogger {

    public void log(ProblemDetail problem, Throwable ex) {
        Object code = problem.getProperties() != null ? problem.getProperties().get("code") : null;
        if (problem.getStatus() >= 500) {
            log.error("{} {} code={}", problem.getStatus(), problem.getInstance(), code, ex);
        } else {
            log.warn("{} {} code={} cause={}", problem.getStatus(), problem.getInstance(), code, ex.toString());
        }
    }
}
```

Si noti l'ultimo argomento di `log.error`. SLF4J, se l'ultimo argomento è un
`Throwable` senza segnaposto corrispondente, lo tratta come eccezione e stampa lo
stack trace completo, cause comprese. Nel ramo WARN si usa invece `ex.toString()`:
classe e messaggio, su una riga.

Con questa politica, `TracciamentoTest` verifica che:

- un 500 produca **esattamente una** riga ERROR, con stack trace;
- un 404 produca **esattamente una** riga WARN e **nessuno** stack trace.

### Correlazione: il `traceId`

Spring Boot integra **Micrometer Observation** e **Micrometer Tracing**. Con un bridge
di tracing nel classpath (nel progetto Brave: `micrometer-tracing-bridge-brave`), ogni
richiesta HTTP in ingresso apre uno *span* (`ServerHttpObservationFilter`). Il suo
contesto contiene:

- **`traceId`**: 128 bit (32 caratteri esadecimali), identifica l'intera operazione
  distribuita, anche attraverso più servizi (si propaga con l'header W3C
  `traceparent` o B3);
- **`spanId`**: 64 bit, identifica il singolo passo.

Spring Boot mette i due valori nell'**MDC** e il pattern di log di default li mostra
così:

```
2026-10-16T12:51:47.387+02:00 ERROR 206437 --- [eccezioni] [main] [6ad201c3c6afc6a8a7b66346332ba264-12e774f973b66c45] i.j.e.web.errore.ErrorLogger : 500 /api/diagnostics/unexpected-error code=INTERNAL_ERROR
```

Il progetto inserisce lo **stesso `traceId`** nel `ProblemDetail`:

```java
@Component
public class TraceIdProvider {

    private final Tracer tracer;

    @Nullable
    public String currentTraceId() {
        Span span = tracer.currentSpan();
        return (span != null) ? span.context().traceId() : null;
    }
}
```

L'utente vede "codice di traccia `6ad201c3…`" e può citarlo in una segnalazione.
L'operatore cerca quel valore nei log, o nel sistema di tracing (Zipkin, Jaeger,
Grafana Tempo), e trova la riga ERROR con lo stack trace e tutti gli span della
richiesta. Il test verifica che il `traceId` della risposta coincida con quello della
riga di log.

`management.tracing.sampling.probability=1.0` fa campionare ogni richiesta. Il
default di Boot è 0.1, che è ragionevole in produzione per contenere i costi di
esportazione. Il `traceId` esiste comunque anche per le richieste non campionate:
cambia solo se gli span vengono esportati.

Il `traceId` è presente anche nei **4xx**. Anche un errore del client può richiedere
un'indagine ("perché il mio ISBN viene rifiutato?"), e averlo sempre semplifica il
contratto.

### Esporre il `traceId`: rischi

Il `traceId` è un identificativo casuale e non rivela nulla dell'infrastruttura. Va
però considerato che:

- se il sistema accetta un `traceparent` dal client, un chiamante può **scegliere** il
  proprio `traceId`. Va quindi trattato come un identificativo di correlazione, non come
  una prova di autenticità;
- in un'API pubblica può valere la pena esporre un identificativo diverso
  (`errorId`), generato ad hoc e legato al `traceId` solo nei log.

### Oltre i log: metriche

Lo stesso filtro che crea gli span registra la metrica `http.server.requests`, con il
tag `status` e, per gli errori, il tag `exception`. Da qui si ricavano tassi di errore
per endpoint, che sono il segnale giusto per gli allarmi. Più affidabile, cioè, del
conteggio delle righe ERROR.

Un dettaglio: se l'eccezione è gestita da un advice, non risale fino al filtro. Per il
filtro la risposta è "normale", solo con uno stato 4xx o 5xx, e il tag `exception`
resta `none`. Per registrare l'eccezione anche nell'osservazione, l'advice può
chiamare
`ServerHttpObservationFilter.findObservationContext(request).ifPresent(ctx -> ctx.setError(ex))`.
Il progetto non lo fa, perché non espone metriche, ma è il punto da cui partire.

## Insidie e compromessi

- **Stack trace per gli errori del client.** Riempiono i log senza aggiungere
  informazione.
- **Messaggi di log con dati personali.** Il valore rifiutato di un campo può essere
  un'email, un codice fiscale, una password digitata nel campo sbagliato. La politica
  WARN registra `ex.toString()`. Per `MethodArgumentNotValidException` quel testo
  include i valori rifiutati, e in un contesto con dati sensibili va ridotto.
- **Log nel servizio *e* nell'advice.** È la causa più comune di duplicati. Se un
  servizio vuole aggiungere contesto, lo mette nell'eccezione (argomenti, causa), non
  in una riga di log in più.
- **Contesto perso sui thread asincroni.** Come la lingua (capitolo 08), anche MDC e
  span sono legati al thread. Spring Boot propaga il contesto nei suoi executor quando
  è attiva la *context propagation* (`spring.task.execution` con un `TaskDecorator`
  di Micrometer).
- **Campionamento al 100% in produzione.** Ha un costo di esportazione e storage.
  Esistono strategie di *tail sampling* per conservare tutte le tracce in errore e solo
  una parte delle altre.

## Nel progetto

| Concetto | Dove |
|---|---|
| Politica di log | `backend/src/main/java/it/javalearning/eccezioni/web/errore/ErrorLogger.java` |
| `traceId` corrente | `backend/src/main/java/it/javalearning/eccezioni/web/errore/TraceIdProvider.java` |
| `traceId` nel `ProblemDetail` | `backend/src/main/java/it/javalearning/eccezioni/web/errore/ProblemDetailFactory.java` (`enrich`) |
| Configurazione | `backend/src/main/resources/application.properties` (`management.tracing.*`) |
| Test su traceId e log una sola volta | `backend/src/test/java/it/javalearning/eccezioni/web/errore/TracciamentoTest.java` |

```bash
curl -s localhost:8080/api/diagnostics/unexpected-error | jq .traceId
# poi cercare quel valore nell'output del backend
```

## Riferimenti

- Spring Boot Reference, [Tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- Spring Boot Reference, [Logging → Correlation IDs](https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.correlation-ids)
- [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/)
- W3C, [Trace Context](https://www.w3.org/TR/trace-context/)
- [SLF4J FAQ – logging exceptions](https://www.slf4j.org/faq.html#paramException)
