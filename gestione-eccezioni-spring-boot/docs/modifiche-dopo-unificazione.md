# Modifiche dal backend unificato in poi

Questo documento raccoglie le modifiche al codice fatte a partire dall'unificazione di BFF e API,
con il motivo di ogni scelta. Il capitolo 9 descrive il contratto OpenAPI fra backend e frontend e
le regole da seguire perché resti affidabile.

Il progetto e la proposta iniziale sono descritti in
[architettura-backend-unificato.md](architettura-backend-unificato.md); come provare gli scenari a
mano è in [CURL.md](../CURL.md).

## Indice

1. [Quadro d'insieme](#1-quadro-dinsieme)
2. [Rete di sicurezza: i test prima della fusione](#2-rete-di-sicurezza-i-test-prima-della-fusione)
3. [Fusione di BFF e API](#3-fusione-di-bff-e-api)
4. [Sessioni in PostgreSQL e back-channel logout](#4-sessioni-in-postgresql-e-back-channel-logout)
5. [Salvataggio di apparati e profili di accesso](#5-salvataggio-di-apparati-e-profili-di-accesso)
6. [La GUI sul modello del backend](#6-la-gui-sul-modello-del-backend)
7. [Infrastruttura e configurazione](#7-infrastruttura-e-configurazione)
8. [Test](#8-test)
9. [Il contratto OpenAPI e gli standard da seguire](#9-il-contratto-openapi-e-gli-standard-da-seguire)
10. [Limiti noti e lavoro rimasto](#10-limiti-noti-e-lavoro-rimasto)

---

## 1. Quadro d'insieme

| Prima | Dopo |
|---|---|
| Due servizi: BFF (8090) e API (8081) | Un solo backend, `backend/api`, sulla 8090 |
| Il BFF inoltrava le chiamate all'API con il token dell'utente | Il backend risponde direttamente, senza salti di rete |
| Tre copie del modello dati (API, BFF, TypeScript) | Una sola, in Java; i tipi TypeScript si generano dal contratto OpenAPI |
| Sessioni in memoria del BFF | Sessioni e token in PostgreSQL |
| La sessione restava valida anche se chiusa su Keycloak | Back-channel logout: cade subito |
| Apparati in sola lettura, credenziali mai salvate | Creazione, modifica, eliminazione; credenziali cifrate nel database |
| La GUI convertiva gli apparati in un proprio modello (`toDevice()`) | La GUI usa gli apparati così come arrivano dal backend |
| Stati della GUI inventati (`online`, `critical`, `maintenance`…) | Gli stati del database: `UP`, `WARNING`, `DOWN`, `UNMANAGED` |
| Nessun test sul BFF | 105 test nel backend, 19 nel frontend |

Il principio che tiene insieme le scelte: **una sola fonte di verità per ogni cosa**. Il database
per i dati, il backend per le regole, il contratto OpenAPI per la forma dei messaggi. Dove la GUI
aveva un proprio modello, propri stati o dati scritti a mano, ora legge quelli del backend.

---

## 2. Rete di sicurezza: i test prima della fusione

**Cosa.** Prima di toccare il BFF sono stati scritti test che ne fissano il comportamento: login
con PKCE, 401 sotto `/api/**` e redirect altrove, CSRF, logout federato, ruoli, endpoint di debug.

**Perché.** Il BFF non aveva test. Spostare la sicurezza da un servizio all'altro senza una rete
avrebbe significato scoprire le regressioni in produzione. Gli stessi test, spostati nel backend
unico, sono passati prima e dopo la fusione: è la prova che il comportamento è rimasto quello.

**Come.**

- I test descrivono il comportamento **visto da fuori** (URL, status, cookie, header), non le
  classi interne: così sono sopravvissuti allo spostamento quasi senza modifiche.
- Keycloak è sostituito da un **provider OIDC finto** (`support/FakeOidcProvider`), basato
  sull'`HttpServer` del JDK: pubblica configurazione OIDC e chiavi, e firma JWT. Il backend percorre
  lo stesso codice della produzione (si configura dall'`issuer-uri`), ma i test non hanno bisogno di
  servizi esterni.
- Il CSRF nei test si fa con un **double submit vero** (stesso valore nel cookie e nell'header),
  non con `csrf()` di spring-security-test: quel post-processor sostituisce il repository CSRF del
  contesto condiviso, e da lì in poi il cookie non veniva più emesso negli altri test.

**Marcatori `DEBUG:`.** Log verbosi, profilo `dev` attivo di default ed endpoint
`/api/debug/tokens` sono rimasti, ma marcati con un commento che inizia con `DEBUG:`. Prima del
rilascio si trovano tutti con `grep -rn "DEBUG:" backend`. La scelta dei due punti evita di trovare
anche i livelli di log scritti come `: DEBUG`.

---

## 3. Fusione di BFF e API

### 3.1 Un solo backend, due catene di sicurezza

**Cosa.** Le classi del BFF (configurazione di sicurezza, CSRF per la SPA, entry point, handler,
`/api/me`, `/api/debug/tokens`) sono state spostate in `backend/api` con `git mv`, e
`backend/bff` eliminato. `SecurityConfig` ha due catene di filtri:

| Catena | Chi | Come si riconosce | Comportamento |
|---|---|---|---|
| 1 | Client applicativi (poller, integrazioni, strumenti) | Header `Authorization: Bearer …` | Resource server stateless, nessun CSRF, nessuna sessione |
| 2 | Il browser | Tutto il resto | Login OIDC con PKCE, sessione con cookie `HttpOnly`, CSRF |

**Perché.**

- Il progetto è un prodotto, non più un esempio didattico: il passaggio del token da BFF ad API
  aggiungeva un servizio, un salto di rete e un caso di guasto senza dare nulla in cambio.
- La copia dei DTO nel BFF è stata la causa di un bug reale (l'IP vuoto nella GUI): il BFF
  scartava in silenzio i campi che la sua copia non conosceva.
- La catena Bearer resta perché servirà: la tabella `remote_pollers` indica che i poller remoti
  dovranno chiamare il backend senza un browser.
- Il pattern BFF (il browser non vede mai un token) è intatto: è la catena 2.

**Stessi ruoli e stesso nome per entrambe le catene.** La catena del browser legge i ruoli dal
claim `roles` dell'ID token, la catena Bearer da `realm_access.roles` dell'access token; entrambe
producono le stesse authority `ROLE_*`. Il nome dell'utente è `preferred_username` in tutte e due
(`user-name-attribute` in `application.yml` e `setPrincipalClaimName` nel convertitore JWT). Così
`@PreAuthorize("hasRole('ADMIN')")` e `authentication.getName()` valgono identici per qualunque
chiamante, e i controller non dipendono più da `Jwt` o `OidcUser`.

### 3.2 Percorsi e porta

- Gli endpoint di dominio passano da `/devices` a **`/api/devices`**: la regola "401 sotto
  `/api/**`, redirect altrove" dell'entry point deve valere anche per loro.
- Il backend usa la **porta 8090**, quella del vecchio BFF: proxy di sviluppo, nginx e redirect URI
  di Keycloak sono rimasti invariati.

### 3.3 Cosa è stato eliminato

`backend/bff` per intero: il `RestClient` verso l'API, le copie di `Device` e `MaintenanceTicket`,
`ApiErrorHandler` (che traduceva gli errori dell'API remota), il `DeviceController` di inoltro.

---

## 4. Sessioni in PostgreSQL e back-channel logout

### 4.1 Spring Session JDBC

**Cosa.** Le sessioni HTTP stanno nelle tabelle `nemo.spring_session` e
`nemo.spring_session_attributes`, create dal nuovo script `database/03_spring_session.sql`.

**Perché.** Con l'unificazione il backend diventa stateful. In memoria, le sessioni andrebbero
perse a ogni riavvio e impedirebbero più istanze. PostgreSQL c'è già: niente Redis né altri
componenti. Lo schema è creato dallo script SQL e non dall'applicazione
(`spring.session.jdbc.initialize-schema: never`), coerentemente con il resto del database.

Il cookie di sessione ora si chiama `SESSION` (era `JSESSIONID`), è `HttpOnly` e `SameSite=Lax`;
dura 30 minuti di inattività, come la sessione SSO del realm.

### 4.2 Token OAuth2 nella sessione

**Cosa.** Bean `OAuth2AuthorizedClientRepository` = `HttpSessionOAuth2AuthorizedClientRepository`.

**Perché.** Per default Spring Boot tiene access e refresh token in memoria, indicizzati per utente
e **separati dalla sessione**: persi a ogni riavvio, non condivisi fra istanze, e conservati anche
dopo il logout. Ora vivono e muoiono con la sessione. Il difetto è emerso osservando le tabelle
durante le prove: la sessione conteneva solo il contesto di sicurezza.

### 4.3 Nessuna sessione per le richieste anonime

**Cosa.** `requestCache(NullRequestCache)` nella catena del browser.

**Perché.** Spring Security salva in sessione ogni richiesta anonima respinta, per ripresentarla
dopo il login. Qui non serve (dopo il login si torna sempre al frontend, `defaultSuccessUrl(…, true)`)
e, con le sessioni in PostgreSQL, ogni richiesta senza login sarebbe diventata una riga scritta nel
database: un modo facile per riempire la tabella. Anche questo è emerso dalle prove reali.

### 4.4 Back-channel logout

**Cosa.** Quando una sessione SSO viene chiusa su Keycloak (logout da un'altra applicazione,
utente disabilitato, chiusura dalla console), Keycloak chiama
`POST /logout/connect/back-channel/keycloak` con un logout token firmato, e la sessione locale
corrispondente viene cancellata subito.

**Perché.** Senza, una sessione resterebbe valida fino alla scadenza anche dopo la revoca. Prima
dell'unificazione il problema era mitigato dalla durata breve dell'access token (2 minuti) usato
verso l'API; con il backend unico l'autorizzazione dipende dalla sessione, che dura 30 minuti.

**Come.**

- `SessionRepositoryBackChannelLogoutHandler` cancella la sessione **direttamente dal repository di
  Spring Session**. Il gestore predefinito di Spring Security chiude la sessione con una chiamata
  HTTP verso l'applicazione stessa (POST `/logout` con il cookie), che richiede che il backend
  raggiunga se stesso e attraversi il CSRF: più fragile, e inutile avendo il repository a
  disposizione.
- L'URL è registrato sul client `nemo-bff` nel realm (`backchannel.logout.url`), verso
  `host.docker.internal:8090`: Keycloak gira in un container, il backend in sviluppo no.

---

## 5. Salvataggio di apparati e profili di accesso

### 5.1 Endpoint

| Metodo e percorso | Ruolo | Cosa fa |
|---|---|---|
| `GET /api/devices` | viewer | Elenco apparati |
| `GET /api/devices/{id}` | viewer | Un apparato |
| `GET /api/devices/options` | viewer | Valori per i form: modelli di catalogo, siti, poller, cicli di vita |
| `POST /api/devices` | admin | Crea un apparato |
| `PUT /api/devices/{id}` | admin | Sostituisce un apparato |
| `DELETE /api/devices/{id}` | admin | Elimina un apparato (con stato, elementi e metriche, in cascata) |
| `POST /api/devices/lifecycle` | admin | Stesso ciclo di vita su più apparati |
| `POST /api/devices/{id}/maintenance` | admin | Ticket di manutenzione (invariato) |
| `GET /api/login-profiles` | viewer | Profili di accesso, senza segreti |
| `GET /api/login-profiles/{id}` | viewer | Un profilo |
| `POST /api/login-profiles` | admin | Crea un profilo |
| `PUT /api/login-profiles/{id}` | admin | Modifica un profilo |
| `GET /api/me` | autenticato | Identità e ruoli (ora anche per i client Bearer) |

### 5.2 Scelte sugli apparati

- **Lo stato operativo non si scrive mai.** Lo produce il polling. Le azioni manuali agiscono sul
  **ciclo di vita** (`lifecycle_status`: `PRODUCTION`, `MAINTENANCE`, `STAGING`,
  `DECOMMISSIONED`): un apparato in manutenzione resta `UP` o `DOWN`.
- **Un apparato nuovo nasce `UNMANAGED`.** Il backend crea anche la riga di
  `device_operational_state` con i default della tabella, così ogni apparato ha sempre uno stato.
  Per gli apparati che non hanno quella riga, la risposta restituisce gli stessi default
  (`OperationalState.NEVER_POLLED`), e `operationalState` è un campo obbligatorio del contratto.
- **Riferimenti per nome, catalogo per id.** Sito, poller e profili si indicano per nome: sono
  univoci nel database ed è quello che la GUI mostra. Il modello si indica per id della voce di
  catalogo, perché i nomi dei modelli non sono univoci.
- **`PUT` sostituisce l'apparato intero.** Un riferimento non mandato viene scollegato. È la
  semantica standard di `PUT`; la GUI usa `deviceRequest()` per partire dai valori attuali e cambiare
  solo ciò che serve.
- **Solo indirizzi IP letterali.** Un nome host verrebbe risolto via DNS e l'indirizzo salvato
  sarebbe quello del momento, non quello scritto.
- **Riferimenti validati prima di salvare.** Un test ha mostrato che la prima versione salvava
  l'apparato e solo dopo si accorgeva di un sito inesistente; ora i riferimenti si controllano prima.
- **Conflitti esplicativi.** Hostname e IP duplicati rispondono 409 con il motivo; un riferimento
  sconosciuto o un IP non valido 400.

### 5.3 Credenziali cifrate

**Cosa.** I profili di accesso (SNMP v1/v2c/v3, WMI) si salvano sul backend. Password e community
SNMP vengono cifrate con **AES-256-GCM** prima di arrivare al database (`CredentialCipher`).

**Perché le scelte di dettaglio.**

- **GCM** cifra e autentica: un valore alterato nel database non viene decifrato ma rifiutato.
- **IV casuale nuovo** a ogni cifratura, e un'**AAD** che lega il testo cifrato a questo uso.
- La busta salvata riporta **versione, algoritmo e identificativo della chiave** (`kid`), per poter
  ruotare la chiave in futuro.
- Nella colonna `credentials_enc` le impostazioni non segrete (versione, porta, protocolli) stanno
  **in chiaro**, i segreti **solo nella busta cifrata**, più l'elenco dei **nomi** dei segreti
  salvati (`storedSecrets`). Così la GUI sa se una password esiste senza che venga mai decifrata per
  lei.
- **I segreti sono solo in scrittura.** Nessuna API li restituisce; in modifica un campo lasciato
  vuoto conserva il segreto salvato (la GUI non lo riceve, quindi non potrebbe rimandarlo).
  Cambiando tipo di profilo i segreti precedenti vengono scartati.
- La chiave è in `nemo.credentials.encryption-key` (32 byte in base64). Quella in
  `application.yml` è di sviluppo.

### 5.4 Gestione degli errori

**Cosa.** Ogni errore arriva al client in formato **RFC 9457** (`application/problem+json`), con il
motivo leggibile nel campo `detail`. I servizi segnalano gli errori con **eccezioni di dominio**, che
non sanno nulla di HTTP; un solo componente, `error/ApiExceptionHandler`, le traduce in risposte.

**Perché.** La GUI mostra il motivo vero ("Esiste gia un apparato con hostname libre") invece di un
generico "errore 409", e il formato è uno standard, non una convenzione del progetto. Separare
l'errore di dominio dalla sua traduzione HTTP permette di chiamare gli stessi servizi anche da chi
non parla HTTP (§5.4.1).

#### 5.4.1 Eccezioni di dominio

Un'eccezione di dominio dice **cosa è andato storto** nel problema che il backend risolve, non come
comunicarlo. Stanno nel package `error`:

| Eccezione | Quando | Status |
|---|---|---|
| `NotFoundException` | La risorsa non esiste (apparato, profilo) | 404 |
| `DuplicateException` | Hostname, IP o nome di profilo già usati | 409 |
| `InvalidInputException` | Riferimento sconosciuto (sito, poller, modello, profilo del tipo sbagliato), IP non valido | 400 |

Prima i servizi lanciavano `ResponseStatusException`, che decide insieme la regola di dominio e lo
status HTTP:

```java
// prima: il servizio sa che un duplicato è un 409
throw new ResponseStatusException(HttpStatus.CONFLICT, "Esiste gia un apparato con hostname " + hostname);

// ora: il servizio dice solo cosa è successo
throw new DuplicateException("Esiste gia un apparato con hostname " + hostname);
```

Cosa si guadagna:

- **I servizi non dipendono da HTTP.** `DeviceService` e `LoginProfileService` non importano più
  nulla di Spring Web. Un job di importazione o un poller che registra apparati via messaggio
  chiameranno lo stesso `DeviceService`: per loro un "409" non significa nulla, mentre
  `catch (DuplicateException ex)` sì (per esempio: salta la riga e prosegui).
- **Reazioni selettive.** Tipi distinti si catturano con `catch` distinti, senza leggere uno status
  code.
- **Test più diretti.** `assertThatThrownBy(…).isInstanceOf(DuplicateException.class)`.
- **Una sola mappa errore → status.** Cambiare lo status di un tipo di errore è una riga
  nell'handler, non una ricerca fra tutti i `throw`.

**La gerarchia è chiusa.** Le tre eccezioni sono `final` ed estendono `DomainException`, che è
`sealed`:

```java
public abstract sealed class DomainException extends RuntimeException
        permits NotFoundException, DuplicateException, InvalidInputException { … }
```

L'handler assegna gli status con uno `switch` senza `default`, che compila solo perché il compilatore
conosce tutti i sottotipi. Un tipo nuovo va aggiunto a `permits`, e finché non gli si dà uno status
nello `switch` il codice non compila: non può esistere un'eccezione di dominio dimenticata che
arriva al client come 500.

Il messaggio dell'eccezione arriva all'utente così com'è (`detail`): va scritto per lui, in
italiano.

#### 5.4.2 Chi traduce le eccezioni in risposte

Nessuna parte del nostro codice chiama `ApiExceptionHandler`: lo invoca Spring MVC quando
un'eccezione esce da un controller.

```
Richiesta HTTP
  → filtri di Spring Security (autenticazione, CSRF)          ← errori qui: §5.4.4
  → DispatcherServlet
      → (lettura e validazione del body, @Valid)
      → controller → servizio → throw new DuplicateException(…)
      ← l'eccezione risale, nessun try/catch
      → DispatcherServlet.processHandlerException(): prova gli HandlerExceptionResolver in ordine
          1. ExceptionHandlerExceptionResolver   ← chiama i metodi di ApiExceptionHandler
          2. ResponseStatusExceptionResolver
          3. DefaultHandlerExceptionResolver
      ← il primo che produce una risposta vince
  ← HTTP 409  application/problem+json
```

1. **All'avvio** `@RestControllerAdvice` registra `ApiExceptionHandler` come gestore globale, valido
   per tutti i controller.
2. `ExceptionHandlerExceptionResolver` costruisce una mappa **tipo di eccezione → metodo** da tutti
   i metodi annotati con `@ExceptionHandler`, compresi quelli ereditati da
   `ResponseEntityExceptionHandler`.
3. **A ogni eccezione** sceglie il metodo il cui tipo è il più vicino nella gerarchia: una
   `DuplicateException` va al metodo dichiarato per `DomainException`, la sua superclasse mappata più
   vicina.
4. Il valore restituito (`ProblemDetail` o `ResponseEntity`) viene serializzato da Jackson come
   `application/problem+json`, con lo status preso dal `ProblemDetail`.

`ApiExceptionHandler` estende `ResponseEntityExceptionHandler`: Spring Boot registra il proprio
gestore dei problem details (`spring.mvc.problemdetails.enabled`) solo se non ne trova già uno, quindi
il nostro prende il suo posto, e le eccezioni standard di Spring MVC continuano ad arrivare come
problem+json.

#### 5.4.3 I membri di `ApiExceptionHandler`

**`handleDomainException(DomainException)`**, con `@ExceptionHandler(DomainException.class)`.
Invocato da `ExceptionHandlerExceptionResolver` per ogni eccezione di dominio uscita da un
controller. Lo `switch` sul tipo concreto sceglie lo status; `ProblemDetail.forStatusAndDetail` crea
il body con il messaggio dell'eccezione nel `detail`.

**`handleMethodArgumentNotValid(…)`**, un `@Override`, senza `@ExceptionHandler` proprio. È il
pattern *template method*: la superclasse decide quando chiamarlo, noi decidiamo cosa fa.

```
Spring MVC legge il JSON e valida il DeviceRequest (@Valid) → fallisce
  → MethodArgumentNotValidException (il controller non viene nemmeno eseguito)
  → ExceptionHandlerExceptionResolver
  → ResponseEntityExceptionHandler.handleException(…)   ereditato, @ExceptionHandler su ~20
                                                       eccezioni standard di Spring MVC
  → instanceof MethodArgumentNotValidException → handleMethodArgumentNotValid(…)
  → il nostro override
```

Il metodo:

1. prende dal `BindingResult` gli errori sui campi, li ordina per nome e li trasforma in
   `FieldViolation`;
2. compone il `detail`, che nomina ogni campo sbagliato (il default di Spring era "Invalid request
   content.", senza dire quale);
3. arricchisce il `ProblemDetail` già preparato da Spring (`ex.getBody()`: status 400, title,
   instance) con `setDetail` e con la proprietà `errors`;
4. chiude con `handleExceptionInternal(…)`, ereditato, che costruisce la `ResponseEntity` applicando
   le regole comuni della superclasse.

```json
{
  "status": 400,
  "detail": "Dati non validi: hostname non deve essere vuoto; lifecycleStatus non deve essere vuoto",
  "errors": [
    { "field": "hostname", "message": "non deve essere vuoto" },
    { "field": "lifecycleStatus", "message": "non deve essere vuoto" }
  ]
}
```

I messaggi sono quelli di Bean Validation, nella lingua dell'`Accept-Language` della richiesta. Le
altre eccezioni standard (JSON malformato, `UUID` non valido nel percorso, metodo non supportato,
`ResponseStatusException`) passano dallo stesso `handleException` ereditato con il comportamento di
default, perché non sono sovrascritte.

**`record FieldViolation(String field, String message)`**. DTO interno: un errore di validazione,
campo e messaggio. Jackson lo serializza negli elementi di `errors`, che una GUI può usare per
evidenziare i campi.

**`handleDataIntegrityViolation(DataIntegrityViolationException)`**, con
`@ExceptionHandler(DataIntegrityViolationException.class)`. Copre i **duplicati concorrenti**: i
servizi controllano hostname, IP e nomi dei profili prima di salvare, ma due scritture contemporanee
superano entrambe il controllo, e a fermare la seconda è il vincolo `UNIQUE` del database. Spring
traduce l'errore di Hibernate in `DataIntegrityViolationException` al flush (nella query che segue il
salvataggio, o al commit). Il metodo:

1. guarda la causa: se è una `ConstraintViolationException` di Hibernate di tipo `UNIQUE`, è un
   duplicato;
2. cerca il nome del vincolo in `UNIQUE_CONSTRAINTS` per il messaggio, altrimenti usa quello
   generico;
3. risponde 409, come la `DuplicateException` che il servizio avrebbe lanciato senza la
   concorrenza.

Se la violazione non è di unicità (chiave esterna, check, not null) il metodo **rilancia**
l'eccezione. `ExceptionHandlerExceptionResolver` la considera non risolta, nessun altro resolver la
gestisce, e arriva alla pagina `/error` di Spring Boot: **500**. È voluto: quei casi sono errori del
backend, non dell'utente, e non vanno mascherati.

**`UNIQUE_CONSTRAINTS`**. Mappa nome del vincolo (come in `database/01_init_nemodb.sql`) → messaggio
per l'utente: `uq_devices_hostname`, `uq_devices_management_ip`, `uq_login_profiles_name`. Un nuovo
vincolo di unicità va aggiunto qui.

**`@Hidden`** sulla classe: senza, springdoc aggiungerebbe le risposte d'errore di ogni handler a
tutte le operazioni del contratto, anche alle `GET` che non possono produrle.

#### 5.4.4 Cosa non passa dall'handler

- **Errori dei filtri di sicurezza.** Avvengono prima del `DispatcherServlet`: la richiesta senza
  login la gestisce `ApiAwareEntryPoint` (401 sotto `/api/**`, redirect altrove), il CSRF fallito
  `AuthAwareAccessDeniedHandler` (401 se anonimo, 403 se autenticato).
- **`@PreAuthorize` negato.** L'`AccessDeniedException` nasce dentro il `DispatcherServlet`, ma
  nessun metodo dell'handler la mappa: risale fino all'`ExceptionTranslationFilter` di Spring
  Security, che risponde 403.
- **Eccezioni impreviste** (`IllegalStateException` di `CredentialCipher`, errori del database non
  di unicità): nessun handler, pagina `/error` di Spring Boot, 500.

#### 5.4.5 Riepilogo

| Origine | Gestita da | Status |
|---|---|---|
| `NotFoundException` / `DuplicateException` / `InvalidInputException` | `handleDomainException` | 404 / 409 / 400 |
| `@Valid` fallito (`MethodArgumentNotValidException`) | `handleMethodArgumentNotValid` (via `handleException` ereditato) | 400 con `errors` |
| Vincolo `UNIQUE` violato (`DataIntegrityViolationException`) | `handleDataIntegrityViolation` | 409 |
| Altri vincoli del database | rilanciata, pagina `/error` | 500 |
| JSON malformato, metodo non supportato, `ResponseStatusException` | `handleException` ereditato | quello dell'eccezione |
| Nessun login | `ApiAwareEntryPoint` | 401 |
| CSRF fallito | `AuthAwareAccessDeniedHandler` | 401 / 403 |
| `@PreAuthorize` negato | `ExceptionTranslationFilter` | 403 |
| Qualunque altra eccezione | pagina `/error` | 500 |

**Test.** La tabella è verificata riga per riga, su due livelli.

- **Traduzione in HTTP: `error/ErrorResponsesTests`** (24 test, MockMvc, servizi simulati). Un
  gruppo `@Nested` per blocco della tabella:

  | Gruppo | Cosa verifica |
  |---|---|
  | `DomainExceptions` | Status di ciascuna eccezione di dominio, problem+json completo (`title`, `status`, `instance`; `type` assente vale `about:blank`), stessa traduzione per gli endpoint dei profili e per il 404 lanciato dal controller |
  | `Validation` | Elenco ordinato dei campi non validi, limiti `@Size`, validazione di `LoginProfileRequest` e `DeviceLifecycleRequest`; il servizio non viene chiamato |
  | `DatabaseConstraints` | 409 per vincolo noto (anche con il nome in maiuscolo), sconosciuto o senza nome; 500 per chiave esterna o per una violazione senza causa Hibernate |
  | `FrameworkErrors` | JSON malformato, body assente, id non valido (400), metodo non supportato (405), media type non supportato (415), tutti in problem+json |
  | `Security` | 401 senza login, CSRF assente (401 anonimo, 403 autenticato), `@PreAuthorize` che scatta prima di qualunque errore del servizio |
  | `Unexpected` | Un'eccezione imprevista non viene tradotta (500) |

- **Quale eccezione lancia ciascun servizio: `DeviceServiceTests` e `LoginProfileServiceTests`**, su
  PostgreSQL vero (duplicati, riferimenti sconosciuti, risorse inesistenti, rinomina su un nome già
  usato).

Un test in `PersistenceMappingTests` verifica inoltre su PostgreSQL vero la forma dell'eccezione che
`handleDataIntegrityViolation` riconosce (causa Hibernate di tipo `UNIQUE` con il nome del vincolo):
se cambiasse con un aggiornamento di Hibernate o Spring, lo segnalerebbe lui invece di un 500 in
produzione.

### 5.5 Validazione

`spring-boot-starter-validation` con `@NotBlank`, `@Size`, `@NotNull` sulle richieste. Oltre a
fermare gli input sbagliati prima del servizio, springdoc riporta queste annotazioni nel contratto
(i campi `@NotBlank` diventano obbligatori nei tipi TypeScript).

---

## 6. La GUI sul modello del backend

### 6.1 Nessuna conversione

**Cosa.** `Device` (in `models/nemo.ts`) è **esattamente** il tipo generato dal contratto,
`components['schemas']['DeviceResponse']`. `toDevice()` e il flag `source` non esistono più. Tutta
la GUI usa i nomi del backend.

| Prima | Ora |
|---|---|
| `name` | `hostname` |
| `ip` | `managementIp` |
| `typeModel` | `model` |
| `category` | `deviceType` |
| `status` (`online`…) | `operationalState.status` (`UP`…) |
| `rtt` | `operationalState.lastRttMs` |
| `lastChecked` | `operationalState.lastCheckAt` |
| `site` (stringa) | `site.name`, con `site.latitude` / `site.longitude` |
| `network` | `networkZone` |
| `lifeCycle` | `lifecycleStatus` |
| `oid` | `sysObjectId` |

**Perché.** L'adattatore era il punto in cui i dati si perdevano: un campo rinominato nel backend
arrivava vuoto senza che nessuno se ne accorgesse. Senza adattatore, un cambiamento del backend
arriva alla GUI attraverso il contratto e, se rompe qualcosa, si vede in compilazione.

I campi del mockup senza colonna nel database (`hostId`, `swVersion`, `cpuUsage`, `notes`…) sono
elencati nel commento di `Device`; i valori dimostrativi sono annotati nello script dei dati demo.

### 6.2 Gli stati come arrivano dal backend

**Cosa.** `DeviceStatus` è `'UP' | 'WARNING' | 'DOWN' | 'UNMANAGED'`, preso dal contratto.
`STATUS_META` (colori ed etichette) è indicizzato su quei valori; la manutenzione ha un badge di
ciclo di vita (`lifecycleBadge`). Card KPI, filtri, griglia, dashboard, mappa IP e report usano gli
stessi valori.

**Perché.** La GUI combinava stato operativo e ciclo di vita in un suo stato a cinque valori:
logica di dominio duplicata nel frontend, e un concetto che non esiste nel database. Ora ognuno dei
due concetti ha il suo campo e la sua resa grafica.

### 6.3 Lo store

- La lista degli apparati è quella del backend, in sola lettura (`linkedSignal` che conserva
  l'ultima risposta durante i reload e in caso di errore).
- Ogni scrittura passa da `DevicesApi` (client `HttpClient`, quindi con interceptor del 401 e header
  CSRF) e poi la lista viene riletta.
- Gli errori del backend diventano toast con il motivo (`apiErrorMessage()` legge `detail`).
- È stata **tolta la variazione simulata dell'RTT** del mockup: l'RTT è quello misurato.
- Opzioni dei form e profili di accesso sono due `httpResource` caricate dallo store.

### 6.4 Viste e finestre

| Dove | Cosa è cambiato |
|---|---|
| Griglia apparati | Colonne sui campi del backend (anche annidati: `site.name`, `operationalState.lastRttMs`); colonna Life Cycle con badge; "Force Mark as Healthy" diventa "Back to Production"; eliminazione via API |
| Card KPI | Filtri `UP` / `DOWN` |
| New Device | Modello, sito, poller e ciclo di vita scelti dai valori del database; le credenziali non si digitano più nel form ma si sceglie (o si crea) un profilo di accesso |
| Device Login Profile | Stile scuro come il resto della GUI; salva davvero; mostra "Stored encrypted" per i segreti salvati |
| Device Inspector | Il campo Status del form di modifica diventa Life Cycle; salvataggio ed eliminazione via API; i segreti del profilo elencati per nome, mai per valore |
| Dettaglio apparato | Coordinate dal sito (prima erano uguali per tutti); profilo SNMP come menu a tendina dei profili del database, salvato alla scelta (solo admin) |
| Admin & Vault | L'archivio credenziali mostra i profili reali invece di tre esempi scritti a mano |
| Tabella IP | "Timeout" solo per gli apparati `DOWN`; senza misura appare "—" |

**Valori del mockup rimossi dove ingannavano.** Ripieghi come
`device.snmpProfile || 'test juniper-mx960-13-3r9-13'` o `device.poller || 'Local-Daemon-01'`
mostravano, con dati veri, il profilo o il poller di un altro apparato. Ora un valore assente si
vede come assente.

---

## 7. Infrastruttura e configurazione

| File | Modifica | Motivo |
|---|---|---|
| `docker-compose.all.yml` | Servizio `backend` al posto di `api` e `bff`; porta 8090 pubblicata; `extra_hosts` su Keycloak | Un solo servizio; Keycloak deve raggiungere il backend per il back-channel logout |
| `docker-compose.yml` | `extra_hosts: host.docker.internal:host-gateway` su Keycloak | Su Linux quel nome non esiste da solo |
| `frontend/nginx.conf`, `proxy-backend.conf` | Proxy verso `backend:8090` | Nome del nuovo servizio |
| `backend/api/Dockerfile` | `EXPOSE 8090` | Nuova porta |
| `database/Dockerfile` | Copia anche `03_spring_session.sql` | Tabelle delle sessioni |
| `keycloak/realm-export.json` | URL di back-channel logout sul client `nemo-bff` | §4.4 |
| `frontend/package.json` | `openapi-typescript`, script `api:types`, `prestart`, `prebuild`, `overrides` | §9 |
| `application.yml` | Porta 8090, client OAuth2 e resource server, sessioni JDBC, springdoc, `default-property-inclusion: non_null`, problem details, chiave delle credenziali | Configurazione del backend unico |

---

## 8. Test

| Gruppo | Classi | Test | Con |
|---|---|---|---|
| Sicurezza (browser e Bearer), ruoli su letture e scritture, back-channel logout, errori | `security/*`, `auth/*`, `error/*`, `device/Device*SecurityTests`, `config/KeycloakRoleConverterTests` | 66 | MockMvc, provider OIDC finto |
| Cifratura | `credential/CredentialCipherTests` | 5 | Nessuno |
| Persistenza e salvataggio | `persistence/*`, `device/DemoSeedTests`, `device/DeviceServiceTests`, `credential/LoginProfileServiceTests` | 24 | PostgreSQL in container |
| Contratto | `contract/OpenApiContractTests` | 1 | MockMvc |
| Flusso completo | `EndToEndTests` | 9 | Keycloak e PostgreSQL veri in container |
| Frontend | `*.spec.ts` | 19 | Vitest |

**Scelte.**

- **Slice `@WebMvcTest` per i test web**, con repository e servizi simulati: veloci e senza
  database. La sicurezza però è quella vera (`SecurityConfig` importata).
- **Token Bearer firmati davvero** dal provider finto, invece del post-processor `jwt()`: passano
  dal decoder reale (firma, issuer, audience, scadenza) e dalla conversione reale dei ruoli. Ogni
  token rifiutato è verificato per il motivo giusto.
- **Utente OIDC simulato con `preferred_username` come nome**, come in produzione: il default di
  `oidcLogin()` (il `sub`) nascondeva un'incoerenza.
- **Un solo container PostgreSQL** per i test di persistenza (`PostgresTestSupport`), inizializzato
  con gli stessi script di `database/`.
- **La prova end-to-end** fa il login sul form vero di Keycloak, come un browser, con un cookie jar
  che invia i cookie `Secure` su `http://localhost` (come fanno i browser). È l'unico test che
  attraversa il back-channel logout reale.

`mvn test` richiede Docker. Il build dell'immagine usa `-DskipTests`.

---

## 9. Il contratto OpenAPI e gli standard da seguire

### 9.1 Come funziona

```
  Controller e DTO Java                  springdoc
  (record con @Schema, @NotBlank…)  ──────────────▶  /v3/api-docs
                                                          │
                                   OpenApiContractTests   │ confronta e, se diverso,
                                                          ▼ riscrive e fallisce
                                          frontend/api/nemo-api.json   (nel repository)
                                                          │
                                   npm run api:types      │ openapi-typescript
                                   (anche prestart/prebuild)
                                                          ▼
                                          frontend/src/app/api/nemo-api.ts   (generato)
                                                          │
                                                          ▼
                                   models/nemo.ts: Device, DeviceRequest, LoginProfile…
```

1. **springdoc** genera la specifica OpenAPI 3.1 dai controller e dai record dei DTO, su
   `/v3/api-docs` (serve autenticazione). Chiavi ordinate (`writer-with-order-by-keys`) perché il
   file generato sia stabile e i diff leggibili; `application/json` come tipo di risposta.
2. **`OpenApiContractTests`** chiede la specifica al backend e la confronta con
   `frontend/api/nemo-api.json`. Se sono diverse riscrive il file e **fallisce**: un cambio del
   contratto non passa mai inosservato, e compare nel diff della pull request. Rilanciando, passa.
3. **openapi-typescript** genera `src/app/api/nemo-api.ts` dal file JSON. Lo script `api:types` gira
   da solo prima di `npm start` e di `npm run build`, anche nella build Docker.
4. **`models/nemo.ts`** dà un nome ai tipi generati, e il resto della GUI usa quei nomi.

Il risultato: un campo rinominato o tolto nel backend diventa un **errore di compilazione** nel
frontend, invece di un campo vuoto a runtime.

**Il limite da conoscere.** I tipi sono verificati in compilazione, non a runtime: `HttpClient` non
controlla il JSON che arriva. La garanzia viene dal test di contratto. Un backend in esecuzione con
una versione diversa da quella del repository può quindi rompere la promessa (è il caso del vecchio
bug dell'IP vuoto).

### 9.2 Standard lato backend

**DTO**

- **I controller non restituiscono mai entità JPA.** Restituiscono record dedicati
  (`DeviceResponse`, `LoginProfileResponse`): il contratto non deve cambiare quando cambia lo schema
  del database, e le entità contengono relazioni lazy e dati interni.
- **Un record per la risposta, uno per la richiesta** (`DeviceResponse` / `DeviceRequest`), anche
  quando oggi i campi coincidono. Il motivo, e le regole sui nomi, sono in §9.3.
- **Tipi espliciti nelle firme dei controller.** `List<DeviceResponse>`, non `ResponseEntity<?>` né
  `Object`: springdoc genera il contratto dalle firme, e un tipo generico produce un contratto vuoto.
- **Niente `Map<String, Object>`** salvo dove i dati sono davvero liberi (`customFields`): una mappa
  diventa `Record<string, unknown>` in TypeScript e il compilatore non controlla più nulla.
- **Enum Java per i valori chiusi** (`DeviceStatus`, `ProfileType`): diventano unioni di stringhe in
  TypeScript (`"UP" | "DOWN" | …`), e un valore sbagliato è un errore di compilazione.
- **Date come `Instant`** (stringhe ISO 8601 in JSON), **id come `UUID`** (stringhe).

**Record per i DTO, classi per le entità**

Ogni DTO (richiesta o risposta) è un `record` Java, ogni entità JPA una classe. Il motivo è che i
due hanno nature opposte: il DTO è un dato che attraversa il confine HTTP, l'entità è una riga del
database con un'identità e uno stato che cambia.

Cosa dà un record a un DTO:

- **Immutabilità.** Campi `final`, nessun setter: una richiesta, una volta letta dal JSON e validata
  da `@Valid`, non può più essere modificata lungo il percorso controller → servizio; una risposta,
  costruita una volta (`DeviceResponse.from(...)`), viene solo serializzata.
- **Meno codice, meno errori.** Costruttore, accessori, `equals`, `hashCode` e `toString` li genera il
  compilatore. La stessa `DeviceRequest` come classe richiederebbe un centinaio di righe, in cui un
  campo dimenticato in `equals` è un bug silenzioso.
- **Uguaglianza per valore, che i test sfruttano.** In `DeviceWriteSecurityTests`,
  `verify(loginProfiles).create(new LoginProfileRequest("nuovo", ProfileType.SNMP, null, null, null))`
  verifica che al servizio sia arrivata esattamente quella richiesta, confrontando tutti i campi.
- **Funziona con tutta la catena senza configurazione.** Jackson 3 crea il record dal JSON con il
  costruttore canonico (senza costruttore vuoto né setter); Bean Validation legge `@NotBlank` e
  `@Size` sui componenti; springdoc legge le stesse annotazioni e `@Schema` per il contratto, e da lì
  i tipi TypeScript. Un'annotazione scritta una volta sul componente arriva ovunque serva.
- **Dichiara l'intento.** `public record DeviceRequest(...)` dice subito che è un dato, non un oggetto
  con comportamento.

Perché le entità restano classi: JPA/Hibernate vuole un costruttore senza argomenti e campi
modificabili (carica l'oggetto, ne imposta i valori e ne traccia le modifiche), crea proxy per le
relazioni lazy (sottoclassi generate a runtime, impossibili con i record, che sono `final`), e
un'entità ha un'identità (l'id) che resta la stessa mentre i valori cambiano: il contrario
dell'uguaglianza per valore. Il confine netto fra i due è anche ciò che permette di cambiare lo
schema del database senza cambiare il contratto.

I limiti dei record, da tenere presenti:

- **Tutti i campi vanno passati al costruttore, nell'ordine.** Con molti campi diventa scomodo
  (`DeviceSummaryProjection` ne ha 24; regge perché lo costruisce soprattutto la query JPQL).
- **Niente ereditarietà**, solo interfacce: per i DTO di solito va bene.
- **Niente valori di default nei campi.** I default (es. "riferimento assente = relazione
  scollegata") stanno nel servizio, che è il posto giusto.

**Date di creazione e modifica: `@CreationTimestamp`, non `@PrePersist`**

Le entità valorizzano `created_at` con l'annotazione di Hibernate (e `updated_at`, dove c'è, con
`@UpdateTimestamp`), non con una callback JPA:

```java
// Così (Hibernate)
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;

// Non così (JPA standard)
@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;

@PrePersist
void onCreate() {
    createdAt = Instant.now();
}
```

| | `@CreationTimestamp` | `@PrePersist` |
|---|---|---|
| Standard | Specifico di Hibernate | JPA, portabile su ogni provider |
| Codice | Un'annotazione sul campo | Un metodo per entità, o una `@MappedSuperclass` comune |
| Quando assegna il valore | All'inserimento, gestito da Hibernate | Alla chiamata di `persist()`, eseguendo codice dell'entità |
| Flessibilità | Solo il timestamp; con `source = SourceType.DB` può usare l'ora del database | Qualsiasi logica: default di altri campi, normalizzazioni, validazioni |
| Testabilità | Non si inietta un `Clock` | Si può usare un `Clock` iniettabile e fissare l'ora nei test |

Perché `@CreationTimestamp`:

- **La portabilità non serve.** Il progetto usa Hibernate tramite Spring Boot e ne dipende già in altri
  punti (`@Immutable` su `DeviceMetricRawEntity`, la `ConstraintViolationException` letta da
  `ApiExceptionHandler`): il vantaggio "standard JPA" sarebbe solo teorico.
- **Uniformità con poco codice.** Lo usano otto entità; con le callback sarebbero otto metodi quasi
  identici (più un `@PreUpdate` per `updated_at`), o una superclasse introdotta solo per questo.
- **Alla creazione non c'è altra logica.** `@PrePersist` conviene quando, oltre al timestamp, bisogna
  fare qualcos'altro.

**Chi decide l'ora.** Lo schema ha `created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`, ma
oggi il valore lo calcola la JVM e Hibernate lo scrive esplicitamente nell'`INSERT`: il `DEFAULT` del
database non viene mai usato. Con un solo backend è indifferente; con più istanze, o con un poller che
scrive direttamente in PostgreSQL, le ore verrebbero da orologi diversi. Se servirà un'unica fonte di
verità basta un attributo, `@CreationTimestamp(source = SourceType.DB)`: Hibernate usa l'ora del
database nello stesso `INSERT`. Né `@PrePersist` né la forma attuale lo permettono.

**Quando passare a `@PrePersist`:** se un test deve verificare date esatte con un `Clock` iniettabile,
se alla creazione serve altra logica, o se il progetto dovesse usare un provider JPA diverso da
Hibernate.

**Obbligatorio o facoltativo**

- Il JSON **omette i campi senza valore** (`spring.jackson.default-property-inclusion: non_null`).
- Ogni campo **sempre presente** va marcato
  `@Schema(requiredMode = Schema.RequiredMode.REQUIRED)` sul componente del record. Diventa
  obbligatorio nel tipo TypeScript (`hostname: string`).
- Tutti gli altri sono facoltativi (`model?: string`): in TypeScript "facoltativo" e "assente nel
  JSON" coincidono. Non usare `Optional` nei DTO.
- Nelle **richieste**, `@NotBlank`/`@NotNull` fanno sia la validazione sia il "required" del
  contratto.

**Endpoint**

- Tutti sotto **`/api/**`**: è lì che vale la regola 401 invece del redirect.
- **`@PreAuthorize` su ogni metodo**, con i ruoli del realm (`VIEWER` per leggere, `ADMIN` per
  scrivere): l'autorizzazione vera è nel backend, la GUI che nasconde un bottone è solo cortesia.
- Endpoint che non fanno parte del prodotto (debug, strumenti interni) vanno marcati **`@Hidden`**,
  altrimenti finiscono nel contratto e nei tipi della GUI.
- Parametri di sicurezza (`Authentication`, `@AuthenticationPrincipal`) sono esclusi dal contratto
  da springdoc; altri parametri iniettati vanno verificati nel diff del contratto.

**Errori**

- **I servizi lanciano eccezioni di dominio**, mai `ResponseStatusException` né altre classi di
  Spring Web: `NotFoundException`, `DuplicateException`, `InvalidInputException` (§5.4). Il
  messaggio arriva all'utente nel campo `detail`, quindi va scritto per lui, in italiano. Lo stesso
  vale per i controller, così il backend ha un solo modo di segnalare un errore.
- **Gli status li decide `ApiExceptionHandler`**: 400 input non valido, 404 risorsa inesistente,
  409 conflitto. 403 lo decide `@PreAuthorize`, 401 la catena di sicurezza.
- **Un tipo di errore nuovo** (es. un'operazione non permessa nello stato attuale, 422) è una nuova
  sottoclasse di `DomainException`: va aggiunta al `permits` e, finché non le si assegna uno status
  nello `switch` dell'handler, il codice non compila. Le eccezioni descrivono un tipo di problema, non
  una risorsa: niente `DeviceNotFoundException` accanto a `NotFoundException`, il messaggio basta a
  dire quale risorsa manca.
- **Validare prima di salvare, ma lasciare l'ultima parola al database.** Il controllo nel servizio
  dà il messaggio preciso nel caso normale; il vincolo `UNIQUE` copre le scritture concorrenti, e
  `ApiExceptionHandler` lo traduce in 409. Un nuovo vincolo di unicità va aggiunto alla sua mappa
  `UNIQUE_CONSTRAINTS` con un motivo leggibile.
- Non aggiungere altri `@RestControllerAdvice`: gli errori trasversali stanno in
  `ApiExceptionHandler`, che deve restare `@Hidden`.

**Segreti**

- I segreti sono **solo in ingresso**: mai in una risposta, nemmeno mascherati. La risposta dice al
  massimo *quali* segreti esistono (`storedSecrets`).

### 9.3 Convenzioni sui nomi

Il nome di una classe dice **che ruolo ha**: la risorsa al singolare, in PascalCase, seguita da un
suffisso che ne indica il ruolo.

| Suffisso | Cos'è | Dove compare | Esempi |
|---|---|---|---|
| `Request` | Body in ingresso di un endpoint | Parametro `@RequestBody` di un controller | `DeviceRequest`, `LoginProfileRequest`, `DeviceLifecycleRequest` |
| `Response` | Body in uscita di un endpoint | Tipo restituito da un controller | `DeviceResponse`, `LoginProfileResponse`, `DeviceFormOptionsResponse`, `UserInfoResponse`, `MaintenanceTicketResponse` |
| `Projection` | Risultato di una query JPQL `select new …` | Solo dentro il backend (repository → servizio) | `DeviceSummaryProjection` |
| `Entity` | Entità JPA, una riga del database | Solo dentro il backend | `DeviceEntity`, `DeviceLoginProfileEntity`, `SiteEntity` |
| `Repository`, `Service`, `Controller` | Componenti Spring | — | `DeviceRepository`, `DeviceService` |
| `Exception` | Eccezione di dominio: il tipo di problema, non la risorsa | Lanciata dai servizi, tradotta da `ApiExceptionHandler` | `NotFoundException`, `DuplicateException` |
| nessuno | Parte di un DTO: il tipo di un suo campo | Dentro un `Request` o un `Response` | `DeviceResponse.SiteRef`, `SnmpSettings`, `ProfileSecrets` |

I repository portano il nome della risorsa (`DeviceRepository`, non `DeviceEntityRepository`): il
suffisso `Repository` dice già cosa sono. Le chiavi composte `@Embeddable` (`DeviceMetricRawId`)
non sono entità e non hanno il suffisso `Entity`.

#### `Request` e `Response`: decide il ruolo, non la forma

Il suffisso non descrive i campi del record ma **il posto che occupa nella firma del controller**:

```java
@PostMapping("/api/devices")
public DeviceResponse create(@Valid @RequestBody DeviceRequest request)
//     ^^^^^^^^^^^^^^ esce: Response          ^^^^^^^^^^^^^ entra: Request
```

- **`XxxRequest`** è ciò che il client manda. Porta la validazione (`@NotBlank`, `@Size`,
  `@NotNull`), i campi in sola scrittura (i segreti) e i riferimenti nella forma in cui il client li
  conosce (un id, un nome).
- **`XxxResponse`** è ciò che il backend restituisce. Porta `@Schema(requiredMode = REQUIRED)` sui
  campi sempre presenti, e i dati che solo il backend conosce: l'id, le date di creazione, i valori
  calcolati, le relazioni risolte.
- Un'operazione che non è il CRUD della risorsa ha il suo `Request`, con l'azione nel nome:
  `DeviceLifecycleRequest` (cambio di ciclo di vita su più apparati).
- Anche una risposta che non rappresenta una risorsa ha il suffisso `Response`:
  `DeviceFormOptionsResponse` (valori per i form), `UserInfoResponse` (utente corrente).
- `POST` e `PUT` della stessa risorsa usano **lo stesso `Request`**: `PUT` sostituisce la risorsa
  intera, quindi i campi sono gli stessi della creazione. Si separano in `XxxCreateRequest` e
  `XxxUpdateRequest` solo quando i campi divergono davvero.

**Due record anche quando i campi coincidono.** Se oggi richiesta e risposta di una risorsa hanno
gli stessi campi, si scrivono comunque due record, `XxxRequest` e `XxxResponse`. Non si usa un
unico tipo senza suffisso, per tre motivi:

1. **Divergono quasi sempre, e presto.** Il confronto fra apparati e profili lo mostra:

   | Campo | `DeviceRequest` | `DeviceResponse` |
   |---|---|---|
   | `id`, `createdAt` | assenti: li decide il backend | presenti |
   | `site` | `String`, il nome del sito | `SiteRef`, nome più coordinate |
   | `vendor`, `model`, `deviceType` | assenti: si manda solo `catalogId` | presenti, letti dal catalogo |
   | `operationalState` | assente: lo produce il polling | presente |

   | Campo | `LoginProfileRequest` | `LoginProfileResponse` |
   |---|---|---|
   | `secrets` | presente: i segreti entrano | assente: i segreti non escono mai |
   | `storedSecrets` | assente | presente: solo i nomi dei segreti salvati |

   Il campo `site` ha lo stesso nome e un tipo diverso nei due sensi: con un record unico non si
   potrebbe scrivere.

2. **"Obbligatorio" vuol dire due cose diverse.** In una richiesta significa "il client deve
   mandarlo" (`@NotBlank`); in una risposta "c'è sempre" (`REQUIRED`). springdoc genera un solo
   schema per record, quindi con un tipo unico un campo è obbligatorio in entrambi i sensi o in
   nessuno. Un `id` obbligatorio costringerebbe la GUI a inventarne uno per creare un apparato;
   un `id` facoltativo la costringerebbe a controllare `undefined` ovunque lo legga.

3. **Separare dopo costa di più.** Il nome del record è il nome dello schema OpenAPI e del tipo
   TypeScript. Dividere un tipo unico quando i campi divergono significa rinominarlo, cioè
   cambiare il contratto e toccare tutti i punti della GUI che lo usano. Partire con due record costa
   qualche riga duplicata.

**Le parti di un DTO non hanno suffisso, e si possono condividere.** Un record usato come tipo di un
campo non è né una richiesta né una risposta, quindi non prende il suffisso. Quando un concetto è
davvero lo stesso nei due sensi, lo stesso tipo può comparire in entrambi:

```java
public record LoginProfileRequest(String name, ProfileType profileType,
        SnmpSettings snmp, WmiSettings wmi, ProfileSecrets secrets) { }

public record LoginProfileResponse(UUID id, String name, ProfileType profileType,
        SnmpSettings snmp, WmiSettings wmi, List<String> storedSecrets, Instant createdAt) { }
```

Le impostazioni SNMP sono le stesse in lettura e in scrittura, e `SnmpSettings` con un suffisso
sarebbe sbagliato in uno dei due sensi. Gli involucri restano due, ed è lì che stanno le
differenze: `id` e `storedSecrets` nella risposta, `secrets` nella richiesta. Una parte che serve a
un solo DTO si dichiara annidata in quel record (`DeviceResponse.SiteRef`,
`DeviceFormOptionsResponse.CatalogOption`).

**La regola in breve:** se un record compare nella firma di un controller, ha il suffisso
`Request` o `Response` e non è condiviso fra i due sensi. Se è solo il tipo di un campo, ha un nome
di dominio senza suffisso e può essere condiviso.

#### `Projection` ed `Entity`

- **`Projection`**: un record che una query JPQL costruisce direttamente (`select new
  com.deda.nemo.api.device.DeviceSummaryProjection(…)`), per leggere in una sola query senza
  caricare le entità. Il servizio lo trasforma in un `Response` (`DeviceResponse.from(…)`); non
  compare mai nella firma di un controller.
- **`Entity`**: le classi JPA. Il suffisso rende impossibile confondere l'entità con il DTO della
  stessa risorsa (`DeviceEntity` / `DeviceResponse`), e un'entità nella firma di un controller salta
  all'occhio in revisione. Il nome dell'entità è anche quello usato in JPQL (`from DeviceEntity d`);
  la tabella è fissata da `@Table(name = …)` e non dipende dal nome della classe.

#### Perché i nomi dei DTO fanno parte del contratto

- springdoc usa il **nome semplice** del record come nome dello schema (`DeviceResponse`,
  `SiteRef`), e openapi-typescript lo usa come chiave del tipo
  (`components['schemas']['DeviceResponse']`). **Rinominare un DTO cambia il contratto**: si vede nel
  diff di `nemo-api.json` e va trattato come ogni altro cambio di contratto (§9.6).
- Il nome semplice deve essere **unico in tutto il backend**, anche per i record annidati: due
  `SiteRef` in package o record diversi finirebbero sullo stesso schema, e uno sovrascriverebbe
  l'altro. I record annidati vanno quindi chiamati con nomi specifici, non `Item` o `Ref`.
- Il frontend dà ai tipi generati **nomi di dominio** in `models/nemo.ts` (`Device`,
  `DeviceFormOptions`, `LoginProfile`): i suffissi restano al confine HTTP e la GUI non li vede.

#### Da evitare

- **Il suffisso `Dto`** (`DeviceDto`): non dice in che direzione va il dato, che è proprio la
  distinzione che serve.
- **Il plurale** (`DevicesResponse`): una lista si restituisce come `List<DeviceResponse>`. Se un
  giorno serve un involucro (paginazione), avrà un nome esplicito, come `DevicePageResponse`.
- **Prefissi** (`ApiDevice`): era il nome del vecchio tipo scritto a mano nel frontend, eliminato.
- **Un DTO con lo stesso nome dell'entità**: è ciò che il suffisso `Entity` impedisce.

### 9.4 Standard lato frontend

- **Mai scrivere a mano un tipo che arriva dal backend.** Si usa il tipo generato, con un nome in
  `models/nemo.ts`:

  ```ts
  export type Device = components['schemas']['DeviceResponse'];
  ```

- **Mai modificare `src/app/api/nemo-api.ts`.** È generato e viene riscritto a ogni `npm start`
  e `npm run build`.
- **Mai aggiungere campi della GUI ai tipi del backend.** Se serve uno stato solo della GUI (una
  selezione, un flag di caricamento), sta in un signal o in un tipo separato, non dentro `Device`.
  Era il caso del vecchio flag `source`.
- **Le chiamate dichiarano il tipo generato**: `http.get<Device[]>(…)`,
  `httpResource<DeviceFormOptions>(…)`. Le scritture passano da `DevicesApi`, che usa `HttpClient`
  (interceptor del 401, header CSRF).
- **I campi facoltativi si trattano come assenti, non come `null`**: `device.model ?? '-'`,
  `device.site?.name`, `lastRttMs !== undefined`. Nei template non stampare un oggetto
  (`{{ device.site }}` mostrerebbe `[object Object]`) e non usare ripieghi che inventano un valore
  (`|| 'Local-Daemon-01'`): un valore assente si mostra come assente.
- **I valori fra cui scegliere vengono dal backend** (`/api/devices/options`, `/api/login-profiles`),
  non da liste scritte nel template.
- **Gli errori si mostrano con `apiErrorMessage()`**, che legge il `detail` RFC 9457.
- **Per costruire un `PUT`** partire da `deviceRequest(device, changes)`: `PUT` sostituisce tutto,
  e un campo dimenticato verrebbe scollegato.
- **Gli stati sono quelli del backend.** Colori ed etichette stanno in `shared/status.ts`
  (`STATUS_META`, `STATUS_ORDER`, `lifecycleBadge`), indicizzati sui valori del contratto.

### 9.5 Flusso di lavoro: aggiungere o cambiare un campo o un endpoint

1. Modificare record e controller nel backend, con `@Schema(REQUIRED)` o le annotazioni di
   validazione dove serve.
2. `mvn -f backend/api/pom.xml test`: `OpenApiContractTests` rigenera `frontend/api/nemo-api.json`
   e fallisce. Controllare il diff del file: è la descrizione esatta del cambio di contratto.
3. Rilanciare i test: ora passano.
4. Nel frontend `npm run api:types` (o semplicemente `npm start`). Il compilatore segnala ogni punto
   che usa un campo cambiato; sistemarli.
5. Fare commit di backend, `nemo-api.json`, `nemo-api.ts` e frontend **insieme**: il contratto e i
   suoi due lati devono restare allineati in ogni commit.

Prima di un cambio, valutare se è compatibile con i client esistenti (§9.6).

### 9.6 Versionamento delle API

Versionare le API significa poterle cambiare **senza rompere chi le usa già**: la versione nuova si
affianca alla vecchia per un periodo concordato, invece di sostituirla di colpo.

**Il problema che risolve.** Oggi l'unico client è la GUI: sta nello stesso repository, viene
rilasciata insieme al backend e, grazie al contratto OpenAPI, scopre in compilazione ogni campo
cambiato. Per lei il versionamento non serve.

Il backend però ha già una seconda porta d'ingresso, la catena Bearer, pensata per i poller remoti
e le integrazioni. Quei client:

- vivono in un altro repository o su un'altra macchina, e possono essere scritti da un altro team;
- si aggiornano con tempi propri: un poller installato presso un cliente può restare alla stessa
  versione per mesi;
- non hanno il compilatore TypeScript che li avvisa.

Se un giorno `managementIp` venisse rinominato in `ipAddress`, la GUI si aggiornerebbe nello stesso
commit, ma ogni poller in giro smetterebbe di funzionare al rilascio del backend.

**Quali cambi rompono i client.**

| Cambio | Rompe i client esistenti? |
|---|---|
| Aggiungere un endpoint | No |
| Aggiungere un campo facoltativo in una risposta | No, se il client ignora i campi che non conosce |
| Aggiungere un campo facoltativo in una richiesta | No |
| Rinominare o togliere un campo | **Sì** |
| Rendere obbligatorio un campo di una richiesta | **Sì** |
| Cambiare il tipo o il formato di un campo | **Sì** |
| Aggiungere un valore a un enum (es. un nuovo stato) | Spesso sì: un client con uno `switch` sui valori noti non sa gestirlo |
| Cambiare il significato di un campo senza cambiarne il nome | **Sì**, ed è il caso peggiore, perché nessuno strumento lo rileva |

**Come si versiona.** Il client dichiara quale versione del contratto parla, e il backend risponde
in quella forma. Le varianti più comuni:

- **Nel percorso:** `/api/v1/devices`, `/api/v2/devices`. È la più semplice da vedere, da provare
  con curl e da instradare in nginx.
- **In un header:** `API-Version: 2`, oppure un media type come
  `Accept: application/vnd.nemo.v2+json`. L'URL resta lo stesso, ma la versione è meno evidente.
- **In un parametro:** `?version=2`. Poco usata per API di questo tipo.

Spring Framework 7, quello sotto Spring Boot 4, supporta il versionamento in modo nativo: si
configura dove leggere la versione (percorso, header…) e si dichiara sui metodi del controller quale
versione servono, senza librerie aggiuntive. springdoc può pubblicare un contratto OpenAPI per ogni
versione.

**Il ciclo di un cambio incompatibile.**

1. Si introduce la v2 accanto alla v1, che continua a funzionare.
2. La v1 viene marcata come deprecata, con una data di dismissione comunicata ai client.
3. I client migrano con i loro tempi.
4. Alla data concordata la v1 viene tolta.

**Indicazioni per NeMo.**

- **Adesso non serve.** Non ci sono client esterni, e versionare senza client da proteggere è solo
  complessità in più.
- **Nel frattempo, prediligere i cambi compatibili:** aggiungere invece di rinominare, e lasciare
  facoltativi i campi nuovi. È una disciplina che costa poco e rimanda il problema.
- **Al primo client esterno** (verosimilmente il primo poller), partire subito con la versione nel
  percorso, `/api/v1/...`. Introdurre la v1 quando c'è già un client in produzione è molto più
  scomodo che farlo prima.

### 9.7 Nota su openapi-typescript e TypeScript 6

openapi-typescript 7.13.0 dichiara il supporto solo a TypeScript 5, mentre il progetto usa
TypeScript 6. In `package.json` un `overrides` gli fa usare il TypeScript 6 del progetto; il codice
generato compila senza problemi. Quando uscirà una versione che dichiara il supporto a TypeScript 6,
l'`overrides` andrà tolto.

---

## 10. Limiti noti e lavoro rimasto

| Tema | Stato | Cosa serve |
|---|---|---|
| Registro delle sessioni SSO | In memoria: con più istanze del backend il logout token deve arrivare all'istanza che ha gestito il login | Un registro condiviso, per lo scenario multi-istanza |
| URL di back-channel logout | `host.docker.internal:8090`, valido in sviluppo e nel compose | L'indirizzo reale del backend in produzione |
| Chiave delle credenziali | Di sviluppo, in `application.yml` | Variabile d'ambiente o vault; la rotazione richiede una nuova `key-id` e la ricifratura dei profili |
| Altri segreti | Client secret e password del database in chiaro | Variabili d'ambiente o vault |
| Debug | Log verbosi, profilo `dev`, `/api/debug/tokens` | Rimuovere quanto marcato `DEBUG:` |
| HTTPS | Non attivo | `server.servlet.session.cookie.secure: true`, TLS verso Keycloak |
| Eliminazione dei profili | Non esiste un endpoint | Da aggiungere quando servirà (i dispositivi collegati restano senza profilo, `ON DELETE SET NULL`) |
| Profili dei dati demo | Creati dallo script senza impostazioni SNMP | Aprirli e salvarli una volta dalla GUI |
| `frontend-test/` | La vecchia GUI di test non usa il nuovo contratto | Aggiornarla o eliminarla |
| Versionamento delle API | Assente, non ancora necessario (§9.6) | Versione nel percorso (`/api/v1/...`) al primo client esterno |
