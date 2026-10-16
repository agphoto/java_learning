import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { I18nService } from '../i18n/i18n.service';
import { CredentialsService } from '../auth/credentials.service';
import { toApiProblem } from './api-problem';

/**
 * Aggiunge Accept-Language (e Authorization, se scelto) a ogni richiesta e converte
 * ogni HttpErrorResponse in un ApiProblem tipizzato: i componenti non vedono mai
 * HttpErrorResponse.
 */
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
