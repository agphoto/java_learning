# Basic: progetto d'esercizio sulle eccezioni

Applicazione minima Spring Boot 4.1 + Angular 22 (CRUD sui libri, H2 in memoria,
nessuna autenticazione) su cui implementare in autonomia la gestione degli errori
studiata nei capitoli dell'argomento.

Tutto il codice relativo alle eccezioni è presente **solo come commento**, marcato
con `ECCEZIONI (da implementare)`:

```bash
grep -rn "ECCEZIONI" backend/src frontend/src
```

Manuale operativo dell'implementazione: [docs/manuale-gestione-eccezioni.md](docs/manuale-gestione-eccezioni.md).

## Punti da implementare

| Dove | Cosa |
|---|---|
| `backend/.../errore/BookNotFoundException.java` | eccezione di dominio → 404 |
| `backend/.../errore/DuplicateIsbnException.java` | regola di dominio violata → 409 |
| `backend/.../errore/GlobalExceptionHandler.java` | `@RestControllerAdvice` con `ProblemDetail`, validazione per campo, rete di sicurezza 500 |
| `backend/.../libro/BookService.java` | lancio delle due eccezioni (`findById`, `create`) |
| `backend/.../application.properties` | `spring.mvc.problemdetails.enabled` |
| `frontend/src/app/error.interceptor.ts` | interceptor che converte `HttpErrorResponse` in `ProblemDetail` |
| `frontend/src/app/app.config.ts` | registrazione dell'interceptor |
| `frontend/src/app/app.ts`, `app.html` | callback `error` e riquadro dell'errore |

## Comportamento di partenza (senza gestione)

| Richiesta | Oggi | Dopo l'esercizio |
|---|---|---|
| `GET /api/books/999` | 500 (`NoSuchElementException`) | 404 |
| `POST` con ISBN già presente | 500 (`DataIntegrityViolationException`) | 409 |
| `POST` con campi non validi | 400, corpo generico di Boot | 400 con errori per campo |

## Come si avvia

Requisiti: Java 25, Node 24.

```bash
# Backend, porta 8082 (la 8081 resta al backend principale)
cd backend && ./mvnw spring-boot:run

# Frontend, porta 4200 con proxy verso :8082
cd frontend && nvm use && npm install && npm start
```

Console H2: <http://localhost:8082/h2-console>, JDBC URL `jdbc:h2:mem:basic`, utente `sa`, senza password.

```bash
curl -s localhost:8082/api/books/999
curl -s -X POST localhost:8082/api/books -H 'Content-Type: application/json' \
     -d '{"isbn":"9788804668237","title":"Copia","author":"X"}'
curl -s -X POST localhost:8082/api/books -H 'Content-Type: application/json'  -d '{"isbn":"123","title":"","author":"X"}'
```

## Test

```bash
cd backend && ./mvnw test           # solo i casi di successo
cd frontend && nvm use && npm test
```

I test sui casi d'errore fanno parte dell'esercizio.
