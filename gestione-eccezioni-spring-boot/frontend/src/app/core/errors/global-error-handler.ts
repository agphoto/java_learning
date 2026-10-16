import { ErrorHandler, Injectable, inject } from '@angular/core';
import { ErrorNotifierService } from './error-notifier.service';
import { I18nService } from '../i18n/i18n.service';
import { clientProblem, isApiProblem } from './api-problem';

/**
 * Ultima rete di sicurezza del client: riceve ciò che nessuno ha gestito, sia un
 * ApiProblem senza handler di errore sia un'eccezione JavaScript (capitolo 10).
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly notifier = inject(ErrorNotifierService);
  private readonly i18n = inject(I18nService);

  handleError(error: unknown): void {
    console.error(error);
    this.notifier.show(
      isApiProblem(error) ? error : clientProblem('CLIENT_ERROR', 0, 'errors.client', (key) => this.i18n.t(key)),
    );
  }
}
