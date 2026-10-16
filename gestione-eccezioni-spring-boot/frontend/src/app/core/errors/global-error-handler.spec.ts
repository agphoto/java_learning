import { TestBed } from '@angular/core/testing';
import { GlobalErrorHandler } from './global-error-handler';
import { ErrorNotifierService } from './error-notifier.service';
import { ApiProblem } from './api-problem';

describe('GlobalErrorHandler', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [GlobalErrorHandler] });
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('mostra un ApiProblem non gestito così com\'è', () => {
    const problem: ApiProblem = { type: 'about:blank', title: 'Libro non trovato', status: 404, code: 'BOOK_NOT_FOUND' };

    TestBed.inject(GlobalErrorHandler).handleError(problem);

    expect(TestBed.inject(ErrorNotifierService).current()).toEqual(problem);
  });

  it('trasforma un errore JavaScript in CLIENT_ERROR', () => {
    TestBed.inject(GlobalErrorHandler).handleError(new TypeError('x is undefined'));

    const shown = TestBed.inject(ErrorNotifierService).current();
    expect(shown?.code).toBe('CLIENT_ERROR');
    expect(shown?.title).toBe('Errore nell\'applicazione');
  });
});
