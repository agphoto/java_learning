# 04 – La risposta: `ProblemDetail`

> Una volta raccolta l'eccezione, bisogna decidere **che forma ha** la risposta. Lo
> standard è la RFC 9457 *Problem Details for HTTP APIs*, che Spring implementa con
> `ProblemDetail`. Questo capitolo descrive lo standard, come Spring lo supporta e il
> contratto d'errore del progetto.

## Il problema

Un client, sia esso Angular, un altro servizio o uno script, ha bisogno di tre cose
per reagire a un errore:

1. **capire che tipo di errore è**, in modo stabile e leggibile da una macchina;
2. **sapere cosa mostrare** a una persona;
3. **trovare i dettagli specifici** (quale campo, quale limite).

Senza uno standard ogni API inventa la propria struttura (`{"error": …}`,
`{"message": …}`, `{"errors": [...]}`) e ogni client deve conoscerla. La RFC 9457
definisce un formato comune ed estendibile.

## Teoria e meccanismo

### RFC 9457 (e RFC 7807)

La RFC 9457 (luglio 2023) **sostituisce** la RFC 7807 (2016) senza cambiarne il
formato. Chiarisce l'uso dei tipi, introduce un registro IANA dei tipi comuni e
spiega come segnalare più problemi. Il media type è
**`application/problem+json`** (esiste anche la variante XML).

I membri standard, tutti facoltativi:

| Membro | Significato |
|---|---|
| `type` | URI che **identifica il tipo di problema**. È l'identificatore primario per le macchine. Default: `about:blank`. |
| `title` | Breve descrizione *leggibile* del tipo. Non dovrebbe cambiare da un'occorrenza all'altra, salvo per la localizzazione. |
| `status` | Lo stato HTTP, ripetuto per comodità (utile se la risposta viene inoltrata da un proxy). |
| `detail` | Spiegazione *leggibile* di **questa occorrenza**, utile al client per correggere il problema. |
| `instance` | URI che identifica questa occorrenza specifica (spesso il path richiesto). |

Ci sono poi i **membri di estensione**: qualsiasi altro campo è ammesso e i client
devono ignorare quelli che non conoscono (§3.2). Su questo punto si costruisce il
contratto del progetto.

La RFC dice anche esplicitamente che `detail` **non** deve servire per il debugging
né essere interpretato dal client per estrarre informazioni: i dati strutturati vanno
in membri di estensione.

### Che cosa sia `type`

`type` è un **identificatore**, non necessariamente un indirizzo da visitare. La RFC
incoraggia a renderlo risolvibile, cioè una pagina di documentazione, ma non lo
impone (§3.1.1). Due consigli pratici:

- usare URI **assoluti**: quelli relativi si risolvono rispetto all'URL della
  richiesta, e due endpoint diversi produrrebbero tipi diversi;
- non cambiarli mai: per il client sono l'equivalente di una costante.

Il progetto usa `https://java-learning.local/errors/<code-in-kebab-case>`. Il dominio
`.local` rende evidente che non risponde, ed è sufficiente per un progetto di studio.
In un'API pubblica lo si farebbe puntare a una pagina di documentazione.

### Il supporto in Spring

Spring Framework 6.0 ha introdotto:

- **`ProblemDetail`**: il modello, con i membri standard e una mappa `properties`
  per le estensioni. Jackson la serializza "appiattita" grazie a un mixin, quindi le
  proprietà appaiono al primo livello del JSON;
- **`ErrorResponse`**: interfaccia per "qualcosa che rappresenta una risposta
  d'errore" (stato, header, `ProblemDetail`, codici di messaggio). La implementano
  tutte le eccezioni standard di Spring MVC, oltre a `ErrorResponseException` e
  `ResponseStatusException`;
- **la negoziazione del contenuto**: se un handler restituisce un `ProblemDetail`
  (anche dentro `ResponseEntity`), Spring sceglie `application/problem+json`. Se
  `instance` è vuoto, lo valorizza con il path della richiesta.

### Il contratto del progetto

Ogni risposta d'errore dell'API ha questa forma:

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

| Estensione | Perché |
|---|---|
| `code` | Il `type` identifica già il problema, ma è un URI lungo e scomodo da usare in uno `switch` lato client. `code` è la stessa informazione in forma di costante: `LOAN_LIMIT_EXCEEDED`. **Mai tradotto.** |
| `traceId` | Collega la risposta alla riga di log e alla traccia distribuita. L'utente può citarlo in una segnalazione (capitolo 09). |
| `timestamp` | Momento dell'errore, in UTC. Utile per incrociare i log quando il `traceId` non c'è. |
| `errors` | Solo per la validazione: un elemento per campo, con un `code` del vincolo e un messaggio (capitolo 06). |

La corrispondenza tra codici e stati HTTP è nel catalogo `ErrorCode`:

| Stato | Code | Quando |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `TYPE_MISMATCH` | input non valido, JSON illeggibile, parametro del tipo sbagliato |
| 401 | `UNAUTHENTICATED` | credenziali assenti o errate |
| 403 | `ACCESS_DENIED` | autenticato ma senza permessi |
| 404 | `BOOK_NOT_FOUND`, `USER_NOT_FOUND`, `RESOURCE_NOT_FOUND` | risorsa o URL inesistente |
| 405 | `METHOD_NOT_ALLOWED` | metodo HTTP non supportato |
| 409 | `BOOK_ALREADY_ON_LOAN`, `DUPLICATE_ISBN` | conflitto con lo stato attuale della risorsa |
| 422 | `LOAN_LIMIT_EXCEEDED` | richiesta comprensibile ma che viola una regola |
| 500 | `INTERNAL_ERROR` | errore inatteso |
| 502 / 503 | `EXTERNAL_SERVICE_ERROR` / `EXTERNAL_SERVICE_UNAVAILABLE` | servizio esterno guasto / irraggiungibile |

La scelta tra **409 e 422** merita una nota. La RFC 9110 definisce il 409 come
"conflitto con lo stato corrente della risorsa" (§15.5.10) e il 422 come contenuto
"ben formato ma non elaborabile" (§15.5.21). Un libro già in prestito è un conflitto
di stato: riprovando dopo la restituzione la stessa richiesta riesce. Il limite di
prestiti è invece una regola dell'utente, indipendente dalla risorsa richiesta.
Molte API usano il 409 o il 422 per entrambi i casi. L'importante è che `code`
renda la distinzione esplicita, a prescindere dallo stato.

### Il codice

La costruzione è concentrata in un'unica classe, usata sia dagli advice sia dai
gestori di Spring Security (capitolo 08):

```java
@Component
public class ProblemDetailFactory {

    public ProblemDetail create(ErrorCode code, Object[] args, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(code.status());
        problem.setType(code.type());
        // title e detail localizzati: capitolo 05
        return enrich(problem, code.name(), request);
    }

    public ProblemDetail enrich(ProblemDetail problem, String code, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        String traceId = traceIdProvider.currentTraceId();
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
```

## Insidie e compromessi

- **Che cosa non mettere mai nella risposta**: stack trace, messaggi SQL, nomi di
  classi o di tabelle, il messaggio di un'eccezione inattesa, URL interni. Sono
  informazioni utili a un attaccante (CWE-209, *Generation of Error Message
  Containing Sensitive Information*) e inutili all'utente. Il progetto lo verifica
  con test espliciti sui 500, sui 409 da vincolo del database e sugli errori dei
  servizi esterni.
- **`detail` interpretato dal client.** Se il frontend estrae il "3" da "Hai già 3
  libri" con una regex, il contratto è rotto alla prima riformulazione del testo. I
  dati vanno in proprietà di estensione dedicate.
- **Riusare `type` per casi diversi.** Ogni `type` deve corrispondere a un solo
  significato. `about:blank` va bene solo quando lo stato HTTP basta da solo a
  descrivere il problema.
- **Un 200 con `{"success": false}`.** È un anti-pattern che nasconde l'errore a
  proxy, cache, monitoraggio e client HTTP. Lo stato HTTP deve essere quello giusto.
- **Estensioni al primo livello o annidate?** La RFC le vuole al primo livello. Un
  oggetto `"extensions": {...}` funziona, ma non è lo stile raccomandato.

## Nel progetto

| Concetto | Dove |
|---|---|
| Catalogo codici → stato e `type` | `backend/src/main/java/it/javalearning/eccezioni/errore/ErrorCode.java` |
| Costruzione del `ProblemDetail` | `backend/src/main/java/it/javalearning/eccezioni/web/errore/ProblemDetailFactory.java` |
| Contratto verificato | `backend/src/test/java/it/javalearning/eccezioni/web/errore/ProblemDetailContractTest.java` |

```bash
curl -s http://localhost:8081/api/books/999 | jq
curl -s -X POST localhost:8081/api/loans -H 'Content-Type: application/json' \
     -d '{"userId":2,"bookId":1}' | jq
```

## Riferimenti

- [RFC 9457 – Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)
- [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) (sostituita)
- [Registro IANA dei problem types](https://www.iana.org/assignments/http-problem-types/)
- RFC 9110, [§15 – Status Codes](https://www.rfc-editor.org/rfc/rfc9110#section-15)
- Spring Framework Reference, [Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [CWE-209](https://cwe.mitre.org/data/definitions/209.html)
