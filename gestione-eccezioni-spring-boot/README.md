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

## Struttura

| Cartella | Contenuto |
|---|---|
| `docs/` | i capitoli |
| `backend/` | API Spring Boot (Java 25, Maven) |
| `frontend/` | app Angular 22 |
| `_progetto/` | design e piano di implementazione |

## Come si avvia

*(completato con l'app Angular)*
