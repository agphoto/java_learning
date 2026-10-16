import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ApiProblem, FieldErrorItem } from '../../core/errors/api-problem';
import { ErrorNotifierService } from '../../core/errors/error-notifier.service';
import { I18nService } from '../../core/i18n/i18n.service';

/**
 * Form di prestito. Volutamente senza validatori lato client: così si vedono gli
 * errori di validazione del server mappati sui singoli campi (capitolo 10).
 */
@Component({
  selector: 'app-loan-form',
  imports: [ReactiveFormsModule],
  templateUrl: './loan-form.html',
  styleUrl: './loan-form.css',
})
export class LoanForm {
  private readonly http = inject(HttpClient);
  private readonly notifier = inject(ErrorNotifierService);
  protected readonly i18n = inject(I18nService);

  protected readonly form = new FormGroup({
    userId: new FormControl<number | null>(null),
    bookId: new FormControl<number | null>(null),
  });

  /** Errori del server raggruppati per campo. */
  protected readonly fieldErrors = signal<Record<string, FieldErrorItem[]>>({});
  protected readonly success = signal(false);

  protected submit(): void {
    this.fieldErrors.set({});
    this.success.set(false);
    this.notifier.clear();
    this.http.post('/api/loans', this.form.getRawValue()).subscribe({
      next: () => this.success.set(true),
      error: (problem: ApiProblem) => {
        if (problem.code === 'VALIDATION_FAILED' && problem.errors?.length) {
          this.fieldErrors.set(groupByField(problem.errors));
        } else {
          this.notifier.show(problem);
        }
      },
    });
  }
}

function groupByField(errors: FieldErrorItem[]): Record<string, FieldErrorItem[]> {
  const grouped: Record<string, FieldErrorItem[]> = {};
  for (const error of errors) {
    (grouped[error.field] ??= []).push(error);
  }
  return grouped;
}
