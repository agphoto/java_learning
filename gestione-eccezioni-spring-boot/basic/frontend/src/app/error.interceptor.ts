// --- ECCEZIONI (da implementare) ---------------------------------------------
// Interceptor funzionale: intercetta ogni HttpErrorResponse, ne estrae il
// ProblemDetail (RFC 9457) e lo rilancia come errore tipizzato per i componenti.
// Va registrato in app.config.ts, dentro withInterceptors([...]).
//
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  // Estensioni del contratto: il client decide su queste, mai su detail.
  code?: string;
  bookId?: number;
  isbn?: string;
  errors?: { field: string; message: string }[];
}

export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // status 0: rete o backend spento, nessun corpo da leggere.
      const problem: ProblemDetail =
        error.status === 0
          ? { status: 0, title: 'Server non raggiungibile' }
          : { status: error.status, title: error.statusText, ...error.error };
      return throwError(() => problem);
    }),
  );
