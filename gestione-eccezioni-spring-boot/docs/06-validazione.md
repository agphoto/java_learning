# 06 – Validazione

> La validazione dell'input è la fonte di errori più frequente in un'API, e quella in
> cui il messaggio per l'utente conta di più: un form deve sapere **quale campo** è
> sbagliato e **perché**. Questo capitolo descrive le due eccezioni che Spring MVC
> produce, come trasformarle in errori per campo e come localizzarne i messaggi.

## Il problema

Una richiesta con dati non validi deve fallire **prima** di arrivare alla logica di
dominio, con un 400 e un elenco di problemi che il client possa associare ai propri
controlli. Senza validazione dichiarativa succede quello che il progetto ha mostrato
mentre veniva costruito: un `userId` assente arriva fino al servizio, l'unboxing di
`null` in `long` lancia una `NullPointerException` e l'utente riceve un **500**, un
errore del server, per un errore suo.

## Teoria e meccanismo

### Jakarta Validation in breve

Jakarta Validation (ex Bean Validation, JSR 380; versione 3.1 in Jakarta EE 11)
definisce vincoli dichiarativi (`@NotNull`, `@NotBlank`, `@Size`, `@Pattern`,
`@Positive`…) e un'API per valutarli. L'implementazione di riferimento è Hibernate
Validator, che Spring Boot include con `spring-boot-starter-validation`. Ogni
violazione è una `ConstraintViolation` con il percorso della proprietà, il valore
non valido e un messaggio **interpolato** dal template del vincolo
(`{jakarta.validation.constraints.NotBlank.message}`).

Spring adatta il `Validator` di Jakarta al proprio
(`LocalValidatorFactoryBean`/`SpringValidatorAdapter`) e converte ogni violazione in
un `FieldError` o `ObjectError`, che è anche un **`MessageSourceResolvable`**. È
questo il ponte con il `MessageSource` del capitolo 05.

### Due punti di validazione, due eccezioni

| | `MethodArgumentNotValidException` | `HandlerMethodValidationException` |
|---|---|---|
| Quando | un argomento annotato `@Valid` (o `@Validated`) è un **oggetto** (tipicamente `@RequestBody`, `@ModelAttribute`) | i vincoli sono **direttamente sui parametri** del metodo (`@PathVariable @Positive long id`, `@RequestParam @Size(max=10) String q`) |
| Da quando | Spring 3 | Spring 6.1 (validazione dei metodi integrata in MVC) |
| Contiene | un `BindingResult` con `FieldError` per ogni proprietà | una lista di `ParameterValidationResult`, uno per parametro, ciascuno con i propri errori |
| Dove arriva | `ResponseEntityExceptionHandler.handleMethodArgumentNotValid` | `ResponseEntityExceptionHandler.handleHandlerMethodValidationException` |

Prima della 6.1 i vincoli sui parametri richiedevano `@Validated` sulla classe del
controller. La validazione avveniva allora in un proxy AOP
(`MethodValidationPostProcessor`) e falliva con `ConstraintViolationException`, che
non è un'eccezione di Spring MVC e finiva in un 500 se non gestita. Dalla 6.1 Spring
MVC applica la validazione dei metodi **da sé**, quando un parametro ha vincoli, e
produce un'eccezione `ErrorResponse` con stato 400. **Non** bisogna più annotare il
controller con `@Validated`: farlo farebbe validare due volte.

Una sottigliezza della 6.1: se un metodo ha vincoli sui parametri **e** un
`@Valid @RequestBody`, la validazione dei metodi si applica a tutti gli argomenti e
anche gli errori del corpo arrivano come `HandlerMethodValidationException`. Nel
progetto i due casi sono in metodi diversi (`get` con `@Positive id`, `create` con
`@Valid` corpo), e quindi producono le due eccezioni distinte.

### Dall'eccezione agli errori per campo

Il contratto (capitolo 04) prevede un'estensione `errors`, un elemento per
violazione:

```java
public record FieldErrorItem(String field, String code, String message) { }
```

- `field`: il nome che il client conosce. Per il corpo è la proprietà JSON (`isbn`),
  per un parametro è il nome del parametro (`id`). Quest'ultimo richiede che il codice
  sia compilato con `-parameters`, cosa che il plugin Maven di Spring Boot fa già;
- `code`: il nome semplice del vincolo (`NotBlank`), stabile e mai tradotto;
- `message`: il testo localizzato.

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
        HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    Locale locale = LocaleContextHolder.getLocale();
    List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> new FieldErrorItem(error.getField(), error.getCode(),
                messageSource.getMessage(error, locale)))
        .toList();
    return validationFailed(errors, request);   // ProblemDetail VALIDATION_FAILED + "errors"
}
```

Per `HandlerMethodValidationException` la struttura è analoga: si scorrono
`getParameterValidationResults()` e, per ciascuno, `getResolvableErrors()`.

Un campo può violare **più vincoli insieme**: un ISBN vuoto viola sia `@NotBlank`
sia `@Pattern`. Il contratto lo ammette, con due elementi per lo stesso `field`, e
il client sceglie se mostrarli tutti o solo il primo.

### I messaggi: codici a cascata

`messageSource.getMessage(error, locale)` non usa il messaggio di Hibernate
Validator, se il catalogo ha qualcosa di meglio. Un `FieldError` porta una **lista di
codici, dal più specifico al più generale**, generata da
`DefaultMessageCodesResolver`:

```
Pattern.bookRequest.isbn     ← vincolo.oggetto.campo
Pattern.isbn                 ← vincolo.campo
Pattern.java.lang.String     ← vincolo.tipo
Pattern                      ← vincolo
```

Il `MessageSource` prova i codici in ordine e usa il primo trovato. Se non ne trova
nessuno ricade sul *default message* dell'errore, che è il messaggio interpolato da
Hibernate Validator (anch'esso localizzato, perché Spring imposta un interpolatore
che usa la lingua di `LocaleContextHolder`). Il catalogo del progetto sfrutta la
cascata per avere testi generici e uno specifico:

```properties
Pattern=Il formato non è valido.
Pattern.bookRequest.isbn=L''ISBN deve essere composto da 13 cifre.
Size=Il testo può contenere al massimo {1} caratteri.
```

Gli **argomenti** sono costruiti da `SpringValidatorAdapter`: `{0}` è il nome del
campo (a sua volta un `MessageSourceResolvable`), poi seguono gli attributi del
vincolo **in ordine alfabetico**. Per `@Size` sono `{1}` = `max` e `{2}` = `min`.

### Perché non lasciare i messaggi a Hibernate Validator

Si potrebbe usare `ValidationMessages.properties` e scrivere i messaggi nelle
annotazioni (`@NotBlank(message = "{book.isbn.required}")`). Funziona, ma:

- i testi finiscono in due cataloghi (`ValidationMessages` e `messages`) con sintassi
  diverse (Expression Language contro `MessageFormat`);
- le annotazioni del DTO si riempiono di chiavi.

Con i codici a cascata il DTO resta pulito e tutti i testi stanno in un solo
catalogo.

## Insidie e compromessi

- **Validazione ≠ regole di dominio.** "Il campo è obbligatorio" è validazione: è
  sintattica, non richiede stato e si può controllare anche nel client. "Hai già 3
  libri in prestito" è una regola di dominio: richiede lo stato e va nel servizio
  (`BusinessRuleException`, 422). Mescolarle porta o a validatori che interrogano il
  database o a regole duplicate.
- **Tipi primitivi nei DTO.** `long userId` con il campo assente nel JSON vale `0`,
  e `@NotNull` non può accorgersene. Per questo i record usano `Long`.
- **Il client valida già.** È comunque necessario validare lato server: il client
  non è sotto il tuo controllo. Il valore aggiunto del contratto `errors` è che le
  due validazioni possono condividere il `code` del vincolo.
- **Errori a livello di oggetto** (`ObjectError`, per esempio un vincolo di classe
  "data fine dopo data inizio"): non hanno un campo. Il progetto non ne ha. Si
  potrebbe usare `field = null` o il nome dell'oggetto.
- **Messaggi con il valore rifiutato.** "Il valore 'abc' non è valido" sembra utile,
  ma riflette nell'output un input non fidato. Se il client lo mostra come HTML, è un
  vettore di XSS.

## Nel progetto

| Concetto | Dove |
|---|---|
| Vincoli sui DTO | `backend/src/main/java/it/javalearning/eccezioni/api/BookRequest.java`, `LoanRequest.java` |
| Vincolo su path variable | `backend/src/main/java/it/javalearning/eccezioni/api/BookController.java` |
| Override dei due handler | `backend/src/main/java/it/javalearning/eccezioni/web/errore/GlobalExceptionHandler.java` |
| Elemento `errors` | `backend/src/main/java/it/javalearning/eccezioni/web/errore/FieldErrorItem.java` |
| Messaggi a cascata | `backend/src/main/resources/messages.properties` (sezione *Validazione*) |
| Test | `backend/src/test/java/it/javalearning/eccezioni/api/ValidazioneTest.java` |

```bash
curl -s -X POST localhost:8081/api/books -H 'Content-Type: application/json' \
     -d '{"isbn":"123","title":"","author":"X"}' | jq .errors
curl -s localhost:8081/api/books/0 | jq .errors
```

## Riferimenti

- [Jakarta Validation 3.1](https://jakarta.ee/specifications/bean-validation/3.1/)
- Spring Framework Reference, [Validation (Spring MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- Spring Framework Reference, [Java Bean Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
- Javadoc di [`DefaultMessageCodesResolver`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/validation/DefaultMessageCodesResolver.html)
- [Hibernate Validator Reference Guide](https://docs.jboss.org/hibernate/validator/9.0/reference/en-US/html_single/)
