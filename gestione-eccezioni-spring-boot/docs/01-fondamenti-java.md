# 01 – Fondamenti Java

> Prima di parlare di come Spring raccoglie le eccezioni, conviene fissare che cosa
> sia un'eccezione in Java, quali scelte di design ha fatto il linguaggio e quali ha
> rovesciato Spring. Tutto ciò che segue nei capitoli successivi poggia su queste basi.

## Il problema

Un'applicazione web deve distinguere almeno tre tipi di guasto:

- l'errore **del chiamante** (input non valido, risorsa inesistente, regola violata);
- l'errore **di un sistema esterno** (database, servizio remoto);
- l'errore **di programmazione** o dell'ambiente (bug, memoria esaurita).

Il linguaggio offre un unico meccanismo, la propagazione di un `Throwable` lungo lo
stack, e lascia al progettista il compito di modellare queste differenze. Se la
gerarchia delle eccezioni è confusa, sarà confusa anche la gestione centralizzata:
un advice non può classificare bene ciò che il dominio non ha classificato.

## Teoria e meccanismo

### La gerarchia di `Throwable`

```
Throwable
├── Error                  ← condizioni anomale da cui di norma non ci si riprende
│   ├── OutOfMemoryError
│   └── StackOverflowError …
└── Exception
    ├── RuntimeException   ← unchecked
    │   ├── IllegalArgumentException
    │   ├── IllegalStateException …
    └── IOException, SQLException …   ← checked
```

La JLS (§11.1.1) definisce **unchecked** le classi `RuntimeException`, `Error` e
le loro sottoclassi. Tutte le altre sono **checked**: il compilatore impone che ogni
metodo che può lanciarle le dichiari nella clausola `throws` o le gestisca
(JLS §11.2). Il controllo è solo statico: a runtime la JVM non distingue tra le
due categorie, e l'istruzione `athrow` tratta allo stesso modo qualsiasi `Throwable`.

### Checked contro unchecked: il dibattito

Le eccezioni checked nascono con un'idea precisa: rendere esplicito nella firma il
fatto che un metodo può fallire per cause **recuperabili** che il chiamante è in grado
di gestire (Bloch, *Effective Java*, item 70: "usa le checked per le condizioni
recuperabili e le runtime per gli errori di programmazione").

Nella pratica sono emersi tre problemi:

1. **Accoppiamento delle firme.** Aggiungere una checked a un metodo di basso livello
   costringe a modificare tutte le firme fino al punto in cui la si gestisce. In
   un'architettura a strati questo fa trapelare dettagli di infrastruttura
   (`SQLException`) nei livelli superiori.
2. **Gestione fittizia.** Il codice che non sa cosa fare è indotto a scrivere
   `catch (Exception e) {}` o `throw new RuntimeException(e)`, ottenendo il
   contrario di quanto voluto.
3. **Incompatibilità con le lambda.** Le interfacce funzionali di `java.util.function`
   non dichiarano eccezioni, quindi una checked non può attraversare uno stream.

Linguaggi successivi sulla JVM (Kotlin, Scala) hanno abolito le checked. Java le
mantiene, ma l'ecosistema le usa sempre meno.

### Perché Spring preferisce le unchecked

Spring ha preso posizione presto: la gerarchia `DataAccessException` è unchecked e
**traduce** le eccezioni specifiche del driver o di JPA in tipi indipendenti dalla
tecnologia (`DuplicateKeyException`, `DataIntegrityViolationException`…). La
motivazione, spiegata nella documentazione di riferimento (*Data Access → Consistent
Exception Hierarchy*), è proprio evitare che il codice di servizio dipenda da una
tecnologia di persistenza solo per poter dichiarare un `throws`.

C'è anche una conseguenza meno visibile ma cruciale: **per default `@Transactional`
fa rollback solo sulle unchecked** (capitolo 07). Lanciare una checked da un metodo
transazionale porta al commit, comportamento che spesso sorprende.

### Le cause concatenate

Dal JDK 1.4 ogni `Throwable` può avere una **causa** (`getCause()`). Si usa per
tradurre un'eccezione di basso livello in una di livello applicativo **senza perdere
l'informazione diagnostica**:

```java
try {
    return client.fetch(isbn);
} catch (ResourceAccessException e) {
    throw new IntegrationException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, e);
}
```

Il messaggio dell'eccezione originale finirà nei log (con lo stack trace completo,
sezione `Caused by:`) ma **non** nella risposta all'utente (capitolo 04).

### `try-with-resources` ed eccezioni soppresse

Se il corpo di un `try-with-resources` lancia un'eccezione e poi anche `close()`
fallisce, quale delle due vince? La JLS (§14.20.3.1) stabilisce che l'eccezione
**primaria** è quella del corpo; quella di `close()` viene aggiunta come
**soppressa** (`addSuppressed`) e la si recupera con `getSuppressed()`. Prima di
Java 7, con il classico `finally`, l'eccezione di chiusura *sostituiva* quella
originale, che andava persa.

### Il costo di un'eccezione

Creare un'eccezione costa soprattutto per la **cattura dello stack trace**
(`fillInStackTrace()`, chiamato dal costruttore), che percorre i frame del thread.
Lanciare e catturare sono relativamente economici. `Throwable` offre un costruttore
protetto che lo evita:

```java
protected Throwable(String message, Throwable cause,
                    boolean enableSuppression, boolean writableStackTrace)
```

Con `writableStackTrace = false` lo stack trace resta vuoto. È una tecnica utile per
eccezioni usate come **controllo di flusso ad alta frequenza** (raro e discutibile)
o per eccezioni pre-allocate. Per gli errori applicativi non conviene: lo stack trace
è proprio ciò che serve per diagnosticare un 500. Per un 404 invece non serve, ma il
problema si risolve meglio a livello di log (capitolo 09) che rinunciando allo stack
trace.

### Gerarchie sealed e pattern matching

Con le classi sealed (JEP 409, Java 17) e il pattern matching per `switch`
(JEP 441, Java 21) una gerarchia di eccezioni può diventare **chiusa**: il compilatore
conosce tutti i sottotipi diretti e può verificare l'esaustività di uno `switch`
senza `default`. Se domani si aggiunge una terza famiglia, ogni `switch` che non la
gestisce smette di compilare.

## Il codice

Nel progetto la gerarchia è questa:

```
ApplicationException          (abstract sealed, extends RuntimeException)
├── DomainException           (abstract non-sealed)
│   ├── ResourceNotFoundException   (final)
│   └── BusinessRuleException       (final)
└── IntegrationException      (final)
```

```java
public abstract sealed class ApplicationException extends RuntimeException
        permits DomainException, IntegrationException {

    private final ErrorCode code;
    private final Object[] args;

    protected ApplicationException(ErrorCode code, Throwable cause, Object... args) {
        super(code.name() + " args=" + Arrays.toString(args), cause);
        this.code = code;
        this.args = args.clone();
    }
    // code(), args()
}
```

Tre scelte da notare:

- **Un codice stabile (`ErrorCode`) al posto di una classe per ogni errore.** Invece di
  `LoanLimitExceededException`, `BookAlreadyOnLoanException`… ci sono poche classi che
  esprimono la *categoria* (non trovato, regola violata, integrazione) e un enum che
  esprime il *caso*. L'advice ragiona sul codice; le classi restano utili per i
  `catch` mirati e per lo `switch` esaustivo.
- **Argomenti, non messaggi.** L'eccezione porta i *dati* (`3, 3`), non una frase.
  La frase verrà composta più avanti, nella lingua dell'utente (capitolo 05).
- **`getMessage()` è per i log.** Restituisce `LOAN_LIMIT_EXCEEDED args=[3, 3]`:
  un testo tecnico, stabile, facile da cercare.

```java
static String classifica(ApplicationException ex) {
    return switch (ex) {               // nessun default: lo switch è esaustivo
        case DomainException d -> "dominio:" + d.code();
        case IntegrationException i -> "integrazione:" + i.code();
    };
}
```

## Insidie e compromessi

- **`HttpStatus` dentro `ErrorCode`.** In un'architettura esagonale pura il dominio
  non dovrebbe conoscere HTTP. Qui si accetta il compromesso per avere **un unico
  punto di verità**: aggiungere un errore vuol dire aggiungere una costante e due
  messaggi. L'alternativa pulita è una mappa `ErrorCode → HttpStatus` nel livello
  web. Funziona, ma è un secondo posto da tenere sincronizzato.
- **Gerarchie troppo profonde.** Una classe per ogni errore porta a decine di classi
  quasi vuote e a advice con decine di `@ExceptionHandler`. Il codice stabile scala
  meglio.
- **`catch (Exception e)`.** Cattura anche le `RuntimeException` di programmazione
  (NPE, `IllegalStateException`) e le nasconde. L'unico posto legittimo per un
  `catch` così ampio è il gestore centrale, che le trasforma in un 500 e le registra
  (capitoli 03 e 09).
- **Perdere la causa.** `throw new IntegrationException(code)` dentro un `catch`, senza
  passare `e`, butta via lo stack trace originale. Va passata sempre.
- **Eccezioni mutabili.** Un'eccezione può essere letta da più thread (pensiamo ai
  `CompletableFuture`). Per questo i campi sono `final` e l'array degli argomenti viene
  copiato.

## Nel progetto

| Concetto | Dove |
|---|---|
| Gerarchia sealed | `backend/src/main/java/it/javalearning/eccezioni/errore/ApplicationException.java` e sottoclassi |
| Codici stabili | `backend/src/main/java/it/javalearning/eccezioni/errore/ErrorCode.java` |
| Eccezioni soppresse, cause, stack trace non scrivibile, switch esaustivo | `backend/src/test/java/it/javalearning/eccezioni/laboratorio/capitolo01/FondamentiJavaTest.java` |
| Stato, chiave e tipo di un codice | `backend/src/test/java/it/javalearning/eccezioni/errore/ErrorCodeTest.java` |

```bash
cd backend && ./mvnw test -Dtest='FondamentiJavaTest,ErrorCodeTest'
```

## Riferimenti

- JLS 25, [Capitolo 11 – Exceptions](https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html)
- JLS 25, [§14.20.3 – try-with-resources](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20.3)
- Javadoc di [`Throwable`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Throwable.html), in particolare il costruttore con `writableStackTrace`
- [JEP 409 – Sealed Classes](https://openjdk.org/jeps/409) · [JEP 441 – Pattern Matching for switch](https://openjdk.org/jeps/441)
- J. Bloch, *Effective Java*, 3ª ed., capitolo 10 (item 69–77)
- Spring Framework Reference, [Consistent Exception Hierarchy (DAO support)](https://docs.spring.io/spring-framework/reference/data-access/dao.html#dao-exceptions)
