# Gestione centralizzata delle eccezioni in Spring Boot

> Nota: "Spring Boot 7" non esiste. Gli esempi si riferiscono a **Spring Boot 4** (basato su **Spring Framework 7**) e funzionano anche su Spring Boot 3.x.

---

## Domanda 1

> Fammi un esempio semplice di gestione delle eccezioni centralizzata con Spring Boot. Un semplice endpoint su un RestController che va in errore per un dato non trovato su un DB. Fammi vedere come scrivi il codice per gestire questo caso.

## Risposta 1

L'idea è questa: il service lancia un'eccezione di dominio, e un `@RestControllerAdvice` la intercetta in un unico punto e la trasforma in una risposta HTTP. Il formato della risposta è `ProblemDetail`, cioè lo standard RFC 9457, supportato nativamente da Spring.

### 1. Entity e Repository

```java
@Entity
public class Product {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    // getter/setter
}
```

```java
public interface ProductRepository extends JpaRepository<Product, Long> {
}
```

### 2. Eccezione di dominio

```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " con id " + id + " non trovato");
    }
}
```

### 3. Service: lancia l'eccezione, non si occupa di HTTP

```java
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public Product findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prodotto", id));
    }
}
```

### 4. Controller: nessun try/catch

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public Product getById(@PathVariable Long id) {
        return service.findById(id);
    }
}
```

### 5. Gestore centralizzato

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Risorsa non trovata");
        return pd;
    }

    // Rete di sicurezza per tutto ciò che non è gestito sopra
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Errore non gestito", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Si è verificato un errore imprevisto");
        pd.setTitle("Errore interno");
        return pd;
    }
}
```

### Risultato

La chiamata `GET /api/products/99`, con un id che non esiste, restituisce:

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Risorsa non trovata",
  "status": 404,
  "detail": "Prodotto con id 99 non trovato",
  "instance": "/api/products/99"
}
```

### Perché funziona bene

- **Controller e service restano puliti.** Non ci sono `try/catch` né `ResponseEntity` costruiti a mano per gli errori.
- **Gli errori hanno un formato unico.** Tutte le eccezioni vengono convertite in un solo posto e con lo stesso schema.
- **Il log degli errori imprevisti è centralizzato.** Il messaggio interno dell'eccezione non viene esposto al client.

Se vuoi che Spring gestisca nello stesso formato anche le sue eccezioni standard, come gli errori di validazione o un JSON malformato, puoi far estendere al tuo advice la classe `ResponseEntityExceptionHandler`. In alternativa basta impostare `spring.mvc.problemdetails.enabled=true`.

---

## Domanda 2

> Fammi un esempio più completo con eccezioni standard, validazione parametri in entrata e JSON malformato.

## Risposta 2

Ogni errore viene restituito come `ProblemDetail`. I casi coperti sono:

- risorsa non trovata (404) e risorsa duplicata (409), cioè le eccezioni di dominio;
- body non valido rispetto ai vincoli `@Valid` (400);
- parametri di path o query non validi (400);
- JSON malformato o con tipi sbagliati (400);
- parametro del tipo sbagliato, ad esempio `/products/abc` (400);
- eccezioni standard di Spring come 405, 415, 404 su rotta inesistente o parametro mancante, gestite in automatico dalla classe base;
- qualsiasi altro errore (500).

### Dipendenze

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId> <!-- in Boot 3.x: spring-boot-starter-web -->
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

### Entity e Repository

```java
@Entity
public class Product {
    @Id @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private BigDecimal price;
    private Integer quantity;

    protected Product() {}

    public Product(String name, BigDecimal price, Integer quantity) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
    // getter
}
```

```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByName(String name);
}
```

### DTO di input con validazione

```java
public record CreateProductRequest(
        @NotBlank(message = "il nome è obbligatorio")
        @Size(max = 100, message = "il nome non può superare 100 caratteri")
        String name,

        @NotNull(message = "il prezzo è obbligatorio")
        @Positive(message = "il prezzo deve essere maggiore di zero")
        BigDecimal price,

        @NotNull(message = "la quantità è obbligatoria")
        @Min(value = 0, message = "la quantità non può essere negativa")
        Integer quantity
) {}
```

### Eccezioni di dominio

```java
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " con id " + id + " non trovato");
    }
}
```

```java
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
```

### Service

```java
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public Product findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prodotto", id));
    }

    @Transactional
    public Product create(CreateProductRequest req) {
        if (repository.existsByName(req.name())) {
            throw new DuplicateResourceException("Esiste già un prodotto con nome '" + req.name() + "'");
        }
        return repository.save(new Product(req.name(), req.price(), req.quantity()));
    }

    public List<Product> search(String name, int size) {
        return repository.findAll(PageRequest.of(0, size)).stream()
                .filter(p -> p.getName().toLowerCase().contains(name.toLowerCase()))
                .toList();
    }
}
```

### Controller

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public Product getById(@PathVariable @Positive(message = "l'id deve essere positivo") Long id) {
        return service.findById(id);
    }

    @GetMapping
    public List<Product> search(
            @RequestParam @NotBlank(message = "il parametro name non può essere vuoto") String name,
            @RequestParam(defaultValue = "10") @Max(value = 100, message = "size massimo 100") int size) {
        return service.search(name, size);
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody CreateProductRequest request) {
        Product created = service.create(request);
        URI location = URI.create("/api/products/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }
}
```

> **Attenzione:** non mettere `@Validated` sulla classe del controller. Da Spring 6.1 la validazione dei parametri (`@PathVariable`, `@RequestParam`) è integrata e lancia una `HandlerMethodValidationException`. Con `@Validated` si attiverebbe invece il vecchio meccanismo AOP, che lancia una `ConstraintViolationException` e va gestita a parte.

### Gestore centralizzato

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- Eccezioni di dominio ----------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Risorsa non trovata", ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        return problem(HttpStatus.CONFLICT, "Risorsa già esistente", ex.getMessage());
    }

    // ---------- Validazione del body (@Valid @RequestBody) ----------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Dati non validi",
                "Uno o più campi non rispettano i vincoli");
        pd.setProperty("errors", errors);
        return ResponseEntity.status(status).headers(headers).body(pd);
    }

    // ---------- Validazione di @PathVariable / @RequestParam ----------

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String param = result.getMethodParameter().getParameterName();
            result.getResolvableErrors()
                    .forEach(err -> errors.putIfAbsent(param, err.getDefaultMessage()));
        });

        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Parametri non validi",
                "Uno o più parametri della richiesta non sono validi");
        pd.setProperty("errors", errors);
        return ResponseEntity.status(status).headers(headers).body(pd);
    }

    // ---------- JSON malformato o tipi non convertibili nel body ----------

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        log.debug("Body non leggibile", ex);
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Richiesta malformata",
                "Il body JSON è malformato o contiene valori di tipo non valido");
        return ResponseEntity.status(status).headers(headers).body(pd);
    }

    // ---------- Tipo errato su path/query (es. /products/abc) ----------

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        String param = (ex instanceof MethodArgumentTypeMismatchException m) ? m.getName() : ex.getPropertyName();
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "sconosciuto";

        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Parametro non valido",
                "Il valore '" + ex.getValue() + "' non è valido per '" + param + "' (atteso: " + expected + ")");
        return ResponseEntity.status(status).headers(headers).body(pd);
    }

    // ---------- Violazioni di vincoli DB non intercettate prima ----------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violazione di integrità dei dati", ex);
        return problem(HttpStatus.CONFLICT, "Conflitto sui dati",
                "L'operazione viola un vincolo di integrità");
    }

    // ---------- Rete di sicurezza ----------

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Errore non gestito", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno",
                "Si è verificato un errore imprevisto");
    }

    // ---------- Helper ----------

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
```

Estendendo `ResponseEntityExceptionHandler` erediti la gestione di tutte le eccezioni standard di Spring MVC. Fra queste ci sono `HttpRequestMethodNotSupportedException` (405), `HttpMediaTypeNotSupportedException` (415), `MissingServletRequestParameterException` (400) e `NoResourceFoundException` (404 su rotta inesistente). Tutte vengono già restituite come `ProblemDetail`. Sovrascrivi i metodi solo dove vuoi un messaggio personalizzato.

### Esempi di risposta

**Validazione del body.** Richiesta `POST /api/products` con body `{"name": "", "price": -5}`:

```json
{
  "type": "about:blank",
  "title": "Dati non validi",
  "status": 400,
  "detail": "Uno o più campi non rispettano i vincoli",
  "instance": "/api/products",
  "timestamp": "2026-10-16T16:35:00Z",
  "errors": {
    "name": "il nome è obbligatorio",
    "price": "il prezzo deve essere maggiore di zero",
    "quantity": "la quantità è obbligatoria"
  }
}
```

**JSON malformato.** Richiesta `POST /api/products` con body `{"name": "Penna", "price": }`:

```json
{
  "type": "about:blank",
  "title": "Richiesta malformata",
  "status": 400,
  "detail": "Il body JSON è malformato o contiene valori di tipo non valido",
  "instance": "/api/products",
  "timestamp": "2026-10-16T16:35:00Z"
}
```

**Parametro di path non valido.** Richiesta `GET /api/products/-3`:

```json
{
  "title": "Parametri non validi",
  "status": 400,
  "errors": { "id": "l'id deve essere positivo" }
}
```

**Tipo errato.** Richiesta `GET /api/products/abc`:

```json
{
  "title": "Parametro non valido",
  "status": 400,
  "detail": "Il valore 'abc' non è valido per 'id' (atteso: Long)"
}
```

**Duplicato.** Una seconda `POST` con lo stesso nome:

```json
{
  "title": "Risorsa già esistente",
  "status": 409,
  "detail": "Esiste già un prodotto con nome 'Penna'"
}
```

### Note pratiche

- Il campo `instance` viene valorizzato automaticamente da Spring con il path della richiesta.
- Per il JSON malformato il messaggio è volutamente generico, perché i dettagli del parser possono esporre informazioni interne. Se vuoi indicare il campo con il tipo sbagliato, puoi controllare se `ex.getCause()` è una `InvalidFormatException` di Jackson. Fai attenzione al package: con Spring Boot 4 (Jackson 3) è `tools.jackson.databind.exc`, mentre con Boot 3.x è `com.fasterxml.jackson.databind.exc`.
- Se ti serve un `type` diverso da `about:blank`, imposta un URI con `pd.setType(URI.create("https://api.example.com/errors/not-found"))`. È utile perché il client può distinguere gli errori in modo programmatico.
