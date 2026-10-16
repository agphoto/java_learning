# 10 – Il client Angular

> L'ultimo tratto del percorso di un errore: dalla risposta HTTP a ciò che la persona
> vede sullo schermo. Questo capitolo descrive come l'app Angular intercetta gli
> errori, li trasforma in un modello tipizzato, decide dove mostrarli e gestisce i casi
> in cui il server non ha risposto affatto.

## Il problema

Il backend fa la sua parte: ogni errore è un `ProblemDetail` con `code` stabile e
testi localizzati. Il client deve ancora:

1. **inviare la lingua** e le credenziali a ogni richiesta;
2. **normalizzare** gli errori. `HttpErrorResponse` mescola errori di rete (status 0),
   risposte non conformi (una pagina HTML di un proxy) e `ProblemDetail`;
3. **decidere la presentazione**: un errore di validazione va sotto il campo, un
   errore di dominio in un banner, un errore JavaScript in un messaggio generico;
4. **non perdere nulla**: un errore che nessun componente gestisce deve comunque
   arrivare all'utente.

## Teoria e meccanismo

### Architettura

```
Componente ──► HttpClient ──► problemInterceptor ──► backend
                                   │  aggiunge Accept-Language, Authorization
                                   │  HttpErrorResponse → ApiProblem
   ◄──────── ApiProblem ───────────┘
   ├─ VALIDATION_FAILED → errori sotto i campi del form
   ├─ altri code        → ErrorNotifierService → ErrorBanner
   └─ nessun handler    → GlobalErrorHandler   → ErrorBanner
```

I componenti **non vedono mai `HttpErrorResponse`**: ricevono sempre un `ApiProblem`.

### Il modello

```ts
export interface ApiProblem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  code: string;
  traceId?: string;
  timestamp?: string;
  errors?: FieldErrorItem[];
}
```

È la trascrizione TypeScript del contratto del capitolo 04. `traceId` è opzionale: il
backend lo inserisce sempre, ma un errore nato nel client non ce l'ha.

### Il mapper: chi scrive i testi

Coerentemente con la **strada A** (capitolo 05), il client **non traduce** gli errori
del server: li usa così come arrivano. Scrive i propri testi solo dove il server non ha
detto nulla di utilizzabile.

```ts
export function toApiProblem(err: HttpErrorResponse, t: (key: string) => string): ApiProblem {
  if (isApiProblem(err.error)) {
    return err.error;                                        // testi del server, già localizzati
  }
  if (err.status === 0) {
    return clientProblem('NETWORK_ERROR', 0, 'errors.network', t);   // nessuna risposta
  }
  return clientProblem('UNKNOWN_ERROR', err.status, 'errors.unknown', t); // risposta non conforme
}
```

`status === 0` è il modo in cui il browser segnala che **non c'è stata una risposta
HTTP**: server spento, rete assente, richiesta bloccata da CORS, timeout. In questi
casi il client è l'unico che può spiegare cosa è successo, quindi i testi vengono dal
suo dizionario.

`isApiProblem` è una *type guard*: verifica che il corpo abbia `code`, `title` e
`status` del tipo giusto. Una risposta 502 di un reverse proxy, con un corpo HTML, non
lo supera e diventa `UNKNOWN_ERROR`.

### L'interceptor funzionale

Da Angular 15 gli interceptor possono essere semplici funzioni (`HttpInterceptorFn`),
registrate con `provideHttpClient(withInterceptors([...]))`. Ottengono le dipendenze con
`inject()`:

```ts
export const problemInterceptor: HttpInterceptorFn = (req, next) => {
  const i18n = inject(I18nService);
  const credentials = inject(CredentialsService);

  let headers = req.headers.set('Accept-Language', i18n.lang());
  const authorization = credentials.authorizationHeader();
  if (authorization) {
    headers = headers.set('Authorization', authorization);
  }

  return next(req.clone({ headers })).pipe(
    catchError((err: unknown) =>
      throwError(() => (err instanceof HttpErrorResponse ? toApiProblem(err, (key) => i18n.t(key)) : err)),
    ),
  );
};
```

Due dettagli:

- `HttpClient`, con `responseType: 'json'` (il default), prova a interpretare come
  JSON anche il corpo delle risposte d'errore. Per un `application/problem+json`
  `err.error` è quindi già un oggetto;
- l'interceptor **rilancia** (`throwError`), non "risolve". Decidere cosa mostrare
  spetta al chiamante, che conosce il contesto (un form, un pulsante).

### Dove mostrare l'errore

Il form di prestito usa il `code` per decidere:

```ts
this.http.post('/api/loans', this.form.getRawValue()).subscribe({
  next: () => this.success.set(true),
  error: (problem: ApiProblem) => {
    if (problem.code === 'VALIDATION_FAILED' && problem.errors?.length) {
      this.fieldErrors.set(groupByField(problem.errors));   // sotto ciascun campo
    } else {
      this.notifier.show(problem);                          // banner globale
    }
  },
});
```

Ecco il valore del `code`: è una **decisione logica** presa su un identificatore
stabile. Basarla sul testo o sullo stato HTTP sarebbe fragile, perché anche un JSON
malformato è un 400.

Il form, per scelta didattica, **non ha validatori lato client**: così si vedono gli
errori di validazione del server sotto i campi. In un'applicazione reale si
aggiungerebbero i validatori di Angular (`Validators.required`) per un riscontro
immediato, e quelli del server resterebbero come garanzia.

### Il banner e il cambio di lingua

`ErrorBanner` legge un signal (`ErrorNotifierService.current`) e mostra `title`,
`detail`, gli eventuali `errors`, il `code` e il `traceId`. Il `traceId` è in piccolo,
perché serve solo a chi deve fare una segnalazione.

Al cambio di lingua l'interfaccia si aggiorna (le etichette passano dal dizionario e
dipendono dal signal della lingua), ma **il messaggio del server no**: è un dato
arrivato in italiano, e resta in italiano finché l'utente non ripete l'azione. È il
compromesso della strada A, ed è verificato da un test (`ErrorBanner` → "al cambio di
lingua traduce l'interfaccia ma non il messaggio del server"). Con la strada B il
banner tradurrebbe `code` → testo nella lingua corrente, e il cambio sarebbe immediato.

### `GlobalErrorHandler`: l'ultima rete

Angular inoltra all'`ErrorHandler` le eccezioni non gestite: errori nei template, negli
handler di eventi e, grazie a `provideBrowserGlobalErrorListeners()`, gli `error` e
`unhandledrejection` della finestra. Ci arriva per esempio un `subscribe()` senza
callback d'errore, perché RxJS rilancia l'errore in modo asincrono.

```ts
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    console.error(error);
    this.notifier.show(
      isApiProblem(error) ? error : clientProblem('CLIENT_ERROR', 0, 'errors.client', (key) => this.i18n.t(key)),
    );
  }
}
```

Se l'errore è un `ApiProblem` sfuggito a un componente, lo si mostra così com'è. Se è
un errore JavaScript (`TypeError`…), si mostra un messaggio generico e il dettaglio
tecnico resta nella console. In produzione andrebbe inviato a un servizio di raccolta
errori (Sentry o simili).

### L'internazionalizzazione dell'interfaccia

Angular offre una i18n **nativa** (`@angular/localize`): i testi si marcano con l'attributo
`i18n`, si estraggono in file XLIFF e si produce **una build per lingua**. È la soluzione
più solida per applicazioni grandi: le traduzioni sono compilate, senza costi a runtime,
e gli strumenti di traduzione la supportano. Ma il cambio di lingua richiede di
**ricaricare un'altra build**.

Per questo banco di prova serviva il cambio di lingua **a runtime**, per vedere in
diretta l'effetto su `Accept-Language`. Si è scelto un dizionario minimo basato su un
signal:

```ts
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly current = signal<Lang>('it');
  readonly lang = this.current.asReadonly();

  setLang(lang: Lang): void { this.current.set(lang); }

  t(key: string): string {
    return DICTIONARIES[this.current()][key] ?? key;
  }
}
```

Poiché `t()` legge il signal, ogni template che lo chiama viene aggiornato al cambio di
lingua, senza pipe né `ChangeDetectorRef`. È lo stesso meccanismo che rende l'app
compatibile con la modalità **zoneless**, il default delle nuove app Angular. Librerie
come Transloco o ngx-translate offrono la stessa idea con più funzioni (caricamento
lazy, plurali, interpolazione).

### Il proxy di sviluppo

Il dev server di Angular inoltra `/api` e `/laboratorio` a `localhost:8080`
(`proxy.conf.json`). Dal punto di vista del browser frontend e backend hanno la stessa
origine, quindi **non serve CORS**. In produzione lo stesso risultato si ottiene
servendo l'app e l'API dietro lo stesso reverse proxy.

Senza proxy, una richiesta bloccata da CORS appare al client come `status 0`, cioè come
un `NETWORK_ERROR`, anche se il server ha risposto. È un'altra ragione per cui il
messaggio di rete deve restare generico.

## Insidie e compromessi

- **`catchError` che restituisce `of(null)`** "inghiotte" l'errore: il componente riceve
  `null` come se la chiamata fosse riuscita. Gestire un errore significa mostrarlo o
  reagire, non farlo sparire.
- **Mostrare `HttpErrorResponse.message`** ("Http failure response for /api/books/999:
  404 Not Found") è testo tecnico, in inglese, con l'URL interno.
- **Basare la logica sul testo** (`if (detail.includes('limite'))`) si rompe alla prima
  traduzione o riformulazione. La logica si basa sul `code`.
- **Errori duplicati.** Se l'interceptor mostra il banner *e* il componente mostra
  l'errore sotto il campo, l'utente vede due messaggi. Qui l'interceptor si limita a
  normalizzare, e la presentazione spetta a chi chiama.
- **401 globale.** In un'app reale un 401 porterebbe alla pagina di login. Si farebbe
  nell'interceptor, ed è uno dei pochi casi in cui il comportamento è davvero globale.

## Nel progetto

| Concetto | Dove |
|---|---|
| Modello e mapper | `frontend/src/app/core/errors/api-problem.ts` |
| Interceptor | `frontend/src/app/core/errors/problem.interceptor.ts` |
| Errore globale e handler | `frontend/src/app/core/errors/error-notifier.service.ts`, `global-error-handler.ts` |
| Dizionario e lingua | `frontend/src/app/core/i18n/` |
| Credenziali | `frontend/src/app/core/auth/credentials.service.ts` |
| Form con errori per campo | `frontend/src/app/ui/loan-form/` |
| Banner | `frontend/src/app/ui/error-banner/` |
| Scenari e selettori | `frontend/src/app/ui/scenarios/`, `frontend/src/app/ui/toolbar/` |
| Registrazione | `frontend/src/app/app.config.ts` |
| Proxy | `frontend/proxy.conf.json` |
| Test | `*.spec.ts` accanto a ciascun file |

## Riferimenti

- Angular, [HTTP – Interceptors](https://angular.dev/guide/http/interceptors)
- Angular, [Error handling](https://angular.dev/best-practices/error-handling)
- Angular, [Signals](https://angular.dev/guide/signals) e [Zoneless](https://angular.dev/guide/zoneless)
- Angular, [Internationalization](https://angular.dev/guide/i18n)
- Angular CLI, [Proxying to a backend server](https://angular.dev/tools/cli/serve#proxying-to-a-backend-server)
- MDN, [`XMLHttpRequest.status`](https://developer.mozilla.org/docs/Web/API/XMLHttpRequest/status) (il valore 0)
