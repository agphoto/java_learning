import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { toApiProblem } from './api-problem';

const t = (key: string) => `[${key}]`;

describe('toApiProblem', () => {
  it('lascia invariato un problem+json conforme (il title non viene ritradotto)', () => {
    const body = {
      type: 'https://java-learning.local/errors/loan-limit-exceeded',
      title: 'Limite di prestiti superato',
      status: 422,
      detail: 'Hai già 3 libri in prestito',
      code: 'LOAN_LIMIT_EXCEEDED',
      traceId: '6ad201c3c6afc6a8a7b66346332ba264',
    };
    const err = new HttpErrorResponse({
      status: 422,
      error: body,
      headers: new HttpHeaders({ 'Content-Type': 'application/problem+json' }),
    });

    const problem = toApiProblem(err, t);

    expect(problem).toEqual(body);
  });

  it('trasforma lo status 0 in NETWORK_ERROR con i testi del client', () => {
    const err = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });

    const problem = toApiProblem(err, t);

    expect(problem.code).toBe('NETWORK_ERROR');
    expect(problem.status).toBe(0);
    expect(problem.title).toBe('[errors.network.title]');
    expect(problem.detail).toBe('[errors.network.detail]');
  });

  it('trasforma un corpo non conforme in UNKNOWN_ERROR conservando lo status', () => {
    const err = new HttpErrorResponse({ status: 500, error: '<html>Internal Server Error</html>' });

    const problem = toApiProblem(err, t);

    expect(problem.code).toBe('UNKNOWN_ERROR');
    expect(problem.status).toBe(500);
    expect(problem.title).toBe('[errors.unknown.title]');
  });

  it.each([502, 503, 504])(
    'tratta un %i senza ProblemDetail (proxy o gateway, backend spento) come NETWORK_ERROR',
    (status) => {
      const err = new HttpErrorResponse({ status, error: 'Error occurred while trying to proxy' });

      const problem = toApiProblem(err, t);

      expect(problem.code).toBe('NETWORK_ERROR');
      expect(problem.status).toBe(status);
      expect(problem.title).toBe('[errors.network.title]');
    },
  );

  it('un 503 con ProblemDetail resta un errore del backend', () => {
    const body = { type: 'about:blank', title: 'Servizio esterno non disponibile', status: 503, code: 'EXTERNAL_SERVICE_UNAVAILABLE' };

    expect(toApiProblem(new HttpErrorResponse({ status: 503, error: body }), t)).toEqual(body);
  });
});
