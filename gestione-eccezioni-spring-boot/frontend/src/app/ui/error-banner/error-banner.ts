import { Component, inject } from '@angular/core';
import { ErrorNotifierService } from '../../core/errors/error-notifier.service';
import { I18nService } from '../../core/i18n/i18n.service';

/**
 * Mostra l'errore globale corrente. title e detail sono quelli del server, così come
 * arrivati (strada A): al cambio di lingua restano invariati.
 */
@Component({
  selector: 'app-error-banner',
  templateUrl: './error-banner.html',
  styleUrl: './error-banner.css',
})
export class ErrorBanner {
  protected readonly notifier = inject(ErrorNotifierService);
  protected readonly i18n = inject(I18nService);
}
