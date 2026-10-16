import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { problemInterceptor } from './problem.interceptor';
import { ApiProblem } from './api-problem';
import { I18nService } from '../i18n/i18n.service';
import { CredentialsService } from '../auth/credentials.service';

describe('problemInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([problemInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('invia Accept-Language con la lingua scelta', () => {
    TestBed.inject(I18nService).setLang('en');

    http.get('/api/books/1').subscribe();

    expect(backend.expectOne('/api/books/1').request.headers.get('Accept-Language')).toBe('en');
  });

  it('invia Authorization solo se sono state scelte delle credenziali', () => {
    const credentials = TestBed.inject(CredentialsService);

    http.get('/api/admin/stats').subscribe();
    expect(backend.expectOne('/api/admin/stats').request.headers.has('Authorization')).toBe(false);

    credentials.set('admin');
    http.get('/api/admin/stats').subscribe();
    expect(backend.expectOne('/api/admin/stats').request.headers.get('Authorization')).toBe('Basic YWRtaW46YWRtaW4=');
  });

  it('rilancia la risposta d\'errore come ApiProblem', () => {
    let received: ApiProblem | undefined;

    http.get('/api/books/999').subscribe({ error: (e: ApiProblem) => (received = e) });
    backend.expectOne('/api/books/999').flush(
      { type: 'https://java-learning.local/errors/book-not-found', title: 'Libro non trovato', status: 404, code: 'BOOK_NOT_FOUND' },
      { status: 404, statusText: 'Not Found', headers: { 'Content-Type': 'application/problem+json' } },
    );

    expect(received?.code).toBe('BOOK_NOT_FOUND');
    expect(received?.title).toBe('Libro non trovato');
  });
});
