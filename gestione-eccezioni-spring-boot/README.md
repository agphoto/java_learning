# Gestione delle eccezioni in Spring Boot

Trattazione completa della gestione degli errori in un'applicazione Spring Boot 4.1
(Spring Framework 7, stack servlet). Il filo conduttore è il percorso di un errore:

> **dove nasce → come lo si raccoglie in un punto centrale → come diventa una
> risposta HTTP → come diventa un messaggio comprensibile per l'utente, nella sua
> lingua.**

L'argomento è accompagnato da un backend eseguibile, che dimostra con test
automatici ogni comportamento descritto, e da una piccola app Angular che
visualizza gli errori in italiano e in inglese.

## Capitoli

1. [Fondamenti Java](docs/01-fondamenti-java.md)
2. [Il meccanismo interno di Spring MVC](docs/02-meccanismo-spring-mvc.md)
3. [Gestione centralizzata](docs/03-gestione-centralizzata.md)
4. [La risposta: `ProblemDetail`](docs/04-problem-detail.md)
5. [Il messaggio per l'utente e l'internazionalizzazione](docs/05-messaggi-utente-i18n.md)
6. [Validazione](docs/06-validazione.md)
7. [Transazioni e rollback](docs/07-transazioni-rollback.md)
8. [Casi di confine](docs/08-casi-di-confine.md)
9. [Logging e observability](docs/09-logging-observability.md)
10. [Il client Angular](docs/10-client-angular.md)

In sintesi visiva: [il percorso di un errore](docs/infografica-percorso-errore.html), un'infografica
che segue un errore dal service Spring al riquadro Angular (da aprire nel browser).

## Struttura

| Cartella | Contenuto |
|---|---|
| `docs/` | i capitoli |
| `backend/` | API Spring Boot (Java 25, Maven) |
| `frontend/` | app Angular 22 |
| `_progetto/` | design e piano di implementazione |
| `basic/` | [progetto d'esercizio](basic/README.md): CRUD minimo con la gestione degli errori da implementare (porta 8082) |

## Come si avvia

Requisiti: Java 25, Node 24 (con nvm: il default della macchina può essere un altro).

### 1. Backend (porta 8081)

```bash
cd backend
./mvnw spring-boot:run
```

Il database H2 in memoria viene ricreato a ogni avvio con i dati di `data.sql`:

| Utente | id | Prestiti attivi |
|---|---|---|
| Ada | 1 | 0 |
| Linus | 2 | 3 (libri 4, 5, 6): è al limite |

### 2. Frontend (porta 4200)

```bash
cd frontend
nvm use          # legge .nvmrc → Node 24
npm install
npm start        # http://localhost:4200, con proxy verso :8081
```

### 3. Credenziali di prova

| Username | Password | Ruoli |
|---|---|---|
| `utente` | `utente` | USER |
| `admin` | `admin` | USER, ADMIN |

Solo `/api/admin/**` richiede l'autenticazione (HTTP Basic, ruolo ADMIN).

### 4. Ogni errore con `curl`

```bash
# 404 risorsa inesistente
curl -s localhost:8081/api/books/999
# 422 limite di prestiti, in inglese
curl -s -X POST localhost:8081/api/loans -H 'Content-Type: application/json' \
     -H 'Accept-Language: en' -d '{"userId":2,"bookId":1}'
# 409 libro già in prestito
curl -s -X POST localhost:8081/api/loans -H 'Content-Type: application/json' -d '{"userId":1,"bookId":5}'
# 409 ISBN duplicato (vincolo del database)
curl -s -X POST localhost:8081/api/books -H 'Content-Type: application/json' \
     -d '{"isbn":"9788804668237","title":"Copia","author":"Qualcuno"}'
# 400 validazione con errori per campo
curl -s -X POST localhost:8081/api/books -H 'Content-Type: application/json' -d '{"isbn":"123","title":"","author":"X"}'
# 400 JSON malformato · 400 parametro non numerico · 404 URL inesistente · 405 metodo
curl -s -X POST localhost:8081/api/loans -H 'Content-Type: application/json' -d '{"userId":'
curl -s localhost:8081/api/books/abc
curl -s localhost:8081/api/nonexistent
curl -si -X DELETE localhost:8081/api/books/1
# 401 · 403 · 200
curl -si localhost:8081/api/admin/stats
curl -s -u utente:utente localhost:8081/api/admin/stats
curl -s -u admin:admin localhost:8081/api/admin/stats
# 502 · 503 servizio esterno
curl -s 'localhost:8081/api/books/1/catalog-info?simulate=error'
curl -s 'localhost:8081/api/books/1/catalog-info?simulate=unavailable'
# 500 errore inatteso (con traceId da cercare nei log)
curl -s localhost:8081/api/diagnostics/unexpected-error
# Laboratorio: fallback su /error (capitolo 02) e regole degli advice (capitolo 03)
curl -s localhost:8081/laboratorio/capitolo02/unhandled
curl -s localhost:8081/laboratorio/capitolo03/state
```

Aggiungendo `| jq` si ottiene un output leggibile.

### 5. Ogni errore con Bruno

La cartella `bruno/` è una collection di [Bruno](https://www.usebruno.com/) (formato
YAML OpenCollection, Bruno 4.x) con le stesse richieste, più alcuni casi di confine
(firewall, `Content-Type` non supportato, lingua non supportata, laboratorio del
capitolo 08). Ogni richiesta verifica la risposta con asserzioni sul `code`, sullo
stato e sul `Content-Type`, e la documentazione di ogni cartella rimanda al capitolo.

- **Bruno desktop**: *Open Collection* → cartella `bruno/`, ambiente `locale`.
- **Bruno CLI**, con il backend avviato:

```bash
cd bruno
npx @usebruno/cli run -r --env locale
```

Il percorso felice crea un libro e lo presta ad Ada: dopo tre esecuzioni Ada è al
limite di prestiti e quella richiesta riceve 422. Riavviare il backend ricrea il database.

### 6. Test

```bash
cd backend && ./mvnw test                     # backend: JUnit + MockMvcTester
cd frontend && nvm use && npm test            # frontend: Vitest
```

Ogni capitolo, nella sezione *Nel progetto*, indica quali test dimostrano i
comportamenti descritti.
