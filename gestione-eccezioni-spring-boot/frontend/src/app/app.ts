import { Component, inject } from '@angular/core';
import { I18nService } from './core/i18n/i18n.service';
import { Toolbar } from './ui/toolbar/toolbar';
import { ErrorBanner } from './ui/error-banner/error-banner';
import { LoanForm } from './ui/loan-form/loan-form';
import { Scenarios } from './ui/scenarios/scenarios';

@Component({
  selector: 'app-root',
  imports: [Toolbar, ErrorBanner, LoanForm, Scenarios],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly i18n = inject(I18nService);
}
