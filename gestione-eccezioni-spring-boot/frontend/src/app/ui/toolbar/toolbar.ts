import { Component, inject } from '@angular/core';
import { I18nService, Lang } from '../../core/i18n/i18n.service';
import { Credentials, CredentialsService } from '../../core/auth/credentials.service';

/** Selettori di lingua (Accept-Language) e credenziali (Authorization). */
@Component({
  selector: 'app-toolbar',
  templateUrl: './toolbar.html',
  styleUrl: './toolbar.css',
})
export class Toolbar {
  protected readonly i18n = inject(I18nService);
  protected readonly credentials = inject(CredentialsService);

  protected readonly langs: Lang[] = ['it', 'en'];
  protected readonly users: Credentials[] = ['none', 'utente', 'admin'];
}
