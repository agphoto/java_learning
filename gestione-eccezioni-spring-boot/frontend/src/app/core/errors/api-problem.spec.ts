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
    const err = new HttpErrorResponse({ status: 502, error: '<html>Bad Gateway</html>' });

    const problem = toApiProblem(err, t);

    expect(problem.code).toBe('UNKNOWN_ERROR');
    expect(problem.status).toBe(502);
    expect(problem.title).toBe('[errors.unknown.title]');
  });
});
