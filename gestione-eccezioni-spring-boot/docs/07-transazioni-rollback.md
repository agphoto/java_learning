# 07 – Transazioni e rollback

> Un'eccezione non decide soltanto la risposta HTTP: decide anche se i dati scritti
> fino a quel momento restano nel database. In Spring il legame tra eccezioni e
> transazioni ha regole precise, alcune controintuitive. Questo capitolo le descrive,
> insieme alla traduzione delle eccezioni di persistenza.

## Il problema

Due domande che ogni gestione degli errori deve porsi:

1. **Quando un metodo fallisce, il lavoro fatto viene annullato?** Non sempre: dipende
   dal *tipo* di eccezione.
2. **Un errore del database come arriva all'advice?** Come eccezione JDBC, JPA,
   Hibernate o Spring? E con quali informazioni utili a decidere la risposta?

## Teoria e meccanismo

### Dove agisce `@Transactional`

`@Transactional` è implementato da un **proxy** AOP: il `TransactionInterceptor`
apre la transazione prima di invocare il metodo e la chiude (commit o rollback) dopo,
in base a come il metodo termina. Ne seguono due fatti.

**Il percorso dell'eccezione attraversa prima il proxy, poi l'advice.**

```
Controller ──► [proxy transazionale] ──► Service.borrow()
                                            │ throw BusinessRuleException
               rollback ◄───────────────────┘
   ◄── l'eccezione prosegue
DispatcherServlet ──► HandlerExceptionResolver ──► GlobalExceptionHandler
```

Quando il `GlobalExceptionHandler` riceve l'eccezione, la transazione è **già
chiusa**. L'advice non può più leggere entità lazy, e non deve neppure farlo: il
progetto ha `spring.jpa.open-in-view=false`.

**L'auto-invocazione non passa dal proxy.** Un metodo che chiama un altro metodo
`@Transactional` della *stessa* classe (`this.altro()`) ne ignora l'annotazione.

### Le regole di rollback di default

`DefaultTransactionAttribute.rollbackOn(Throwable ex)` è una riga sola:

```java
return (ex instanceof RuntimeException || ex instanceof Error);
```

Quindi:

| Il metodo esce con… | Esito |
|---|---|
| `RuntimeException` (e sottoclassi) | **rollback** |
| `Error` | **rollback** |
| eccezione **checked** | **commit** |

La scelta risale alle origini di Spring ed è coerente con EJB: un'eccezione checked
era considerata un "esito applicativo previsto", non un fallimento. Il risultato
sorprende ancora oggi: un metodo che scrive e poi lancia una `IOException` lascia la
scrittura nel database.

Gli strumenti per cambiare le regole sono:

- `@Transactional(rollbackFor = CheckedDiDominio.class)`: rollback anche per quella
  checked (e le sue sottoclassi);
- `@Transactional(noRollbackFor = AvvisoNonBloccante.class)`: commit anche per
  quell'unchecked;
- da Spring Framework 6.2, `@EnableTransactionManagement(rollbackOn =
  RollbackOn.ALL_EXCEPTIONS)`: cambia il default per **tutta** l'applicazione.

Le regole sono valutate da `RuleBasedTransactionAttribute`. Vince la regola più
vicina nella gerarchia dell'eccezione, e senza regole applicabili si ricade sul
default.

Il progetto evita il problema alla radice. Le eccezioni applicative sono unchecked
(capitolo 01), quindi qualunque errore di dominio annulla la transazione.

### Rollback-only e `UnexpectedRollbackException`

Che cosa succede se un metodo interno, che partecipa alla stessa transazione
(propagazione `REQUIRED`, il default), esce con un'eccezione, e il chiamante la
cattura e prosegue?

```java
@Transactional
public void catturaEccezioneInterna() {
    registro.save(new RegistroOperazioni("esterna"));
    try {
        interno.fallisci();          // @Transactional, lancia IllegalStateException
    } catch (IllegalStateException ex) {
        // "gestita"
    }
}                                     // ← qui Spring lancia UnexpectedRollbackException
```

Quando l'eccezione attraversa il proxy di `interno.fallisci()`, il
`TransactionInterceptor` non può fare rollback, perché la transazione non è sua, ma
la **marca come rollback-only**. Il chiamante cattura l'eccezione e termina
normalmente, quindi il suo proxy tenta il commit. Il transaction manager vede il
flag, esegue il rollback e, poiché il chiamante si aspettava un commit, lancia
**`UnexpectedRollbackException`**. Anche la riga "esterna" viene annullata.

È un comportamento **corretto**: una transazione è atomica, e una sua parte non può
fallire mentre il resto prosegue. Se si vuole che l'operazione interna possa fallire
in modo isolato, la si mette in una transazione separata (`REQUIRES_NEW`) o in un
savepoint (`NESTED`, se il transaction manager lo supporta).

### La traduzione delle eccezioni di persistenza

Un vincolo violato nasce come `SQLException` dal driver
(`JdbcSQLIntegrityConstraintViolationException` per H2, `SQLState 23505`). Hibernate
la avvolge in una propria `org.hibernate.exception.ConstraintViolationException`,
e Spring la traduce in un'eccezione della gerarchia **`DataAccessException`**:

```
DataIntegrityViolationException                          ← Spring (unchecked, indipendente dalla tecnologia)
 └─ caused by: org.hibernate.exception.ConstraintViolationException   ← Hibernate: getConstraintName()
     └─ caused by: JdbcSQLIntegrityConstraintViolationException       ← driver JDBC
```

La traduzione avviene in due punti:

- nei **repository**: Spring Data JPA applica la traduzione a ogni repository, e i
  bean `@Repository` scritti a mano la ricevono da
  `PersistenceExceptionTranslationPostProcessor`;
- al **commit**: `JpaTransactionManager` traduce le eccezioni sollevate durante il
  flush finale.

Il secondo punto ha una conseguenza pratica. Con `save()` l'INSERT può essere
rimandato fino al commit. La violazione emerge allora **dal proxy, fuori dal
metodo di servizio**, che non ha modo di intercettarla. Il progetto usa
`saveAndFlush()` nella creazione del libro proprio perché la violazione si manifesti
dentro il servizio, in un punto prevedibile.

### Riconoscere il vincolo

`DataIntegrityViolationException` dice *che* un vincolo è stato violato, non *quale*.
Per dare all'utente un messaggio sensato bisogna distinguere "ISBN duplicato" (errore
del chiamante, 409) da "chiave esterna mancante" (probabilmente un bug, 500).

Il progetto dà al vincolo un **nome esplicito** e lo cerca nella catena delle cause:

```java
@Table(name = "book", uniqueConstraints =
        @UniqueConstraint(name = Book.UK_ISBN, columnNames = "isbn"))   // "uk_book_isbn"
```

```java
@ExceptionHandler(DataIntegrityViolationException.class)
ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
    if (violatesConstraint(ex, Book.UK_ISBN)) {
        ProblemDetail problem = problems.create(ErrorCode.DUPLICATE_ISBN, new Object[0], request);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
    return handleUnexpected(ex, request);           // vincolo sconosciuto: 500 generico
}

private static boolean violatesConstraint(Throwable ex, String constraintName) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
        if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null
                && cve.getConstraintName().toLowerCase(Locale.ROOT).contains(constraintName)) {
            return true;
        }
    }
    return false;
}
```

Il confronto usa `contains` e non l'uguaglianza perché il nome estratto dipende dal
database: H2 riporta `PUBLIC.UK_BOOK_ISBN INDEX PUBLIC.UK_BOOK_ISBN_INDEX_1`.

Il messaggio originale contiene la query SQL, il nome della tabella e il valore
duplicato. Finisce nei log e **mai** nella risposta. `IsbnDuplicatoTest` lo verifica.

### Controllare prima o intercettare dopo?

L'alternativa è controllare prima di scrivere (`existsByIsbn`) e lanciare una
`BusinessRuleException`. È più leggibile, ma soggetta a **race condition**: due
richieste concorrenti superano entrambe il controllo e una delle due fallisce
comunque sul vincolo. Il vincolo del database è l'unica garanzia vera, e il controllo
preventivo è al più un'ottimizzazione per avere un messaggio più ricco. Il progetto
si affida al vincolo.

## Insidie e compromessi

- **Catturare un'eccezione di persistenza e continuare a usare l'`EntityManager`.**
  Dopo un'eccezione Hibernate la sessione è in uno stato non affidabile e la
  transazione è rollback-only. L'unica cosa sensata è uscire.
- **Checked dentro un metodo transazionale.** Commit silenzioso. Se il progetto usa
  checked, servono `rollbackFor` o il default globale della 6.2.
- **`@Transactional` sul controller.** Allarga la transazione alla serializzazione
  della risposta e rende più difficile capire dove avviene il rollback. Il confine
  transazionale appartiene al servizio.
- **Eccezioni lanciate dopo il commit.** Se il metodo ha già committato e poi fallisce
  qualcosa a valle (per esempio l'invio di un evento), l'utente riceve un errore per
  un'operazione che è invece avvenuta. Servono pattern come il *transactional outbox*
  o `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- **Test `@Transactional`.** Un test annotato così fa rollback alla fine e non vede
  mai un commit reale. `RollbackTest` volutamente **non** lo è.

## Nel progetto

| Concetto | Dove |
|---|---|
| Regole di rollback (checked, `rollbackFor`, unchecked, rollback-only) | `backend/src/main/java/it/javalearning/eccezioni/laboratorio/capitolo07/ServizioTransazionale.java` |
| Test delle quattro regole | `backend/src/test/java/it/javalearning/eccezioni/laboratorio/capitolo07/RollbackTest.java` |
| Vincolo con nome | `backend/src/main/java/it/javalearning/eccezioni/dominio/Book.java` |
| `saveAndFlush` | `backend/src/main/java/it/javalearning/eccezioni/dominio/BookService.java` |
| Traduzione in 409 | `backend/src/main/java/it/javalearning/eccezioni/web/errore/GlobalExceptionHandler.java` (`handleDataIntegrity`) |
| Test del 409 senza dettagli SQL | `backend/src/test/java/it/javalearning/eccezioni/api/IsbnDuplicatoTest.java` |

```bash
curl -s -X POST localhost:8080/api/books -H 'Content-Type: application/json' \
     -d '{"isbn":"9788804668237","title":"Copia","author":"Qualcuno"}' | jq
```

## Riferimenti

- Spring Framework Reference, [Declarative Transaction Management → Rolling Back](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)
- Spring Framework Reference, [Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
- Spring Framework Reference, [Exception Translation (ORM)](https://docs.spring.io/spring-framework/reference/data-access/orm/general.html#orm-exception-translation)
- Sorgenti: [`DefaultTransactionAttribute`](https://github.com/spring-projects/spring-framework/blob/main/spring-tx/src/main/java/org/springframework/transaction/interceptor/DefaultTransactionAttribute.java), [`RuleBasedTransactionAttribute`](https://github.com/spring-projects/spring-framework/blob/main/spring-tx/src/main/java/org/springframework/transaction/interceptor/RuleBasedTransactionAttribute.java)
- Javadoc di [`UnexpectedRollbackException`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/UnexpectedRollbackException.html)
