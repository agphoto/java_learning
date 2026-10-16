import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LoanForm } from './loan-form';
import { problemInterceptor } from '../../core/errors/problem.interceptor';
import { ErrorNotifierService } from '../../core/errors/error-notifier.service';

const PROBLEM_JSON = { 'Content-Type': 'application/problem+json' };

describe('LoanForm', () => {
  let fixture: ComponentFixture<LoanForm>;
  let backend: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [LoanForm],
      providers: [provideHttpClient(withInterceptors([problemInterceptor])), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(LoanForm);
    backend = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  function type(id: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(): Promise<void> {
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('mostra gli errori di validazione sotto il campo corrispondente, non nel banner', async () => {
    type('bookId', '1');
    await submit();

    const req = backend.expectOne('/api/loans');
    expect(req.request.body).toEqual({ userId: null, bookId: 1 });
    req.flush(
      {
        type: 'https://java-learning.local/errors/validation-failed',
        title: 'Dati non validi',
        status: 400,
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'userId', code: 'NotNull', message: 'Il campo è obbligatorio.' }],
      },
      { status: 400, statusText: 'Bad Request', headers: PROBLEM_JSON },
    );
    await fixture.whenStable();

    expect(el.querySelector('[data-field-error="userId"]')?.textContent).toContain('Il campo è obbligatorio.');
    expect(el.querySelector('[data-field-error="bookId"]')).toBeNull();
    expect(TestBed.inject(ErrorNotifierService).current()).toBeNull();
  });

  it('mostra un errore di dominio nel banner globale', async () => {
    type('userId', '2');
    type('bookId', '1');
    await submit();

    backend.expectOne('/api/loans').flush(
      { type: 'about:blank', title: 'Limite di prestiti superato', status: 422, code: 'LOAN_LIMIT_EXCEEDED' },
      { status: 422, statusText: 'Unprocessable Content', headers: PROBLEM_JSON },
    );
    await fixture.whenStable();

    expect(TestBed.inject(ErrorNotifierService).current()?.code).toBe('LOAN_LIMIT_EXCEEDED');
    expect(el.querySelector('[data-field-error]')).toBeNull();
  });
});
