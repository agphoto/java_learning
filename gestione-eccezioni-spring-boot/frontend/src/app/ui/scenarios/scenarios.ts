import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiProblem } from '../../core/errors/api-problem';
import { ErrorNotifierService } from '../../core/errors/error-notifier.service';
import { I18nService } from '../../core/i18n/i18n.service';

interface Scenario {
  key: string;
  request: () => Observable<unknown>;
}

/** Un pulsante per ogni caso d'errore del backend (capitolo 10). */
@Component({
  selector: 'app-scenarios',
  templateUrl: './scenarios.html',
  styleUrl: './scenarios.css',
})
export class Scenarios {
  private readonly http = inject(HttpClient);
  private readonly notifier = inject(ErrorNotifierService);
  protected readonly i18n = inject(I18nService);

  protected readonly result = signal<string | null>(null);

  protected readonly scenarios: Scenario[] = [
    { key: 'scenarios.bookNotFound', request: () => this.http.get('/api/books/999') },
    { key: 'scenarios.loanLimit', request: () => this.http.post('/api/loans', { userId: 2, bookId: 1 }) },
    { key: 'scenarios.alreadyOnLoan', request: () => this.http.post('/api/loans', { userId: 1, bookId: 5 }) },
    {
      key: 'scenarios.duplicateIsbn',
      request: () => this.http.post('/api/books', { isbn: '9788804668237', title: 'Copia', author: 'Qualcuno' }),
    },
    { key: 'scenarios.invalidBook', request: () => this.http.post('/api/books', { isbn: '123', title: '', author: '' }) },
    { key: 'scenarios.adminStats', request: () => this.http.get('/api/admin/stats') },
    { key: 'scenarios.catalogError', request: () => this.http.get('/api/books/1/catalog-info?simulate=error') },
    {
      key: 'scenarios.catalogUnavailable',
      request: () => this.http.get('/api/books/1/catalog-info?simulate=unavailable'),
    },
    { key: 'scenarios.unexpected', request: () => this.http.get('/api/diagnostics/unexpected-error') },
    { key: 'scenarios.notFoundUrl', request: () => this.http.get('/api/nonexistent') },
  ];

  protected run(scenario: Scenario): void {
    this.notifier.clear();
    this.result.set(null);
    scenario.request().subscribe({
      next: (body) => this.result.set(JSON.stringify(body)),
      error: (problem: ApiProblem) => this.notifier.show(problem),
    });
  }

  /** Nessun try/catch: l'errore arriva al GlobalErrorHandler. */
  protected clientError(): void {
    throw new Error('Errore simulato nel client');
  }
}
