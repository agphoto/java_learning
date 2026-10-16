import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ErrorBanner } from './error-banner';
import { ErrorNotifierService } from '../../core/errors/error-notifier.service';
import { I18nService } from '../../core/i18n/i18n.service';

describe('ErrorBanner', () => {
  let fixture: ComponentFixture<ErrorBanner>;
  let el: HTMLElement;
  let notifier: ErrorNotifierService;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [ErrorBanner] });
    fixture = TestBed.createComponent(ErrorBanner);
    el = fixture.nativeElement;
    notifier = TestBed.inject(ErrorNotifierService);
    notifier.show({
      type: 'https://java-learning.local/errors/book-not-found',
      title: 'Libro non trovato',
      status: 404,
      detail: 'Non esiste alcun libro con identificativo 999.',
      code: 'BOOK_NOT_FOUND',
      traceId: '6ad201c3c6afc6a8a7b66346332ba264',
    });
    await fixture.whenStable();
  });

  it('mostra title, detail, code e traceId', () => {
    expect(el.textContent).toContain('Libro non trovato');
    expect(el.textContent).toContain('Non esiste alcun libro con identificativo 999.');
    expect(el.textContent).toContain('BOOK_NOT_FOUND');
    expect(el.textContent).toContain('6ad201c3c6afc6a8a7b66346332ba264');
  });

  it('al cambio di lingua traduce l\'interfaccia ma non il messaggio del server', async () => {
    TestBed.inject(I18nService).setLang('en');
    await fixture.whenStable();

    expect(el.textContent).toContain('Libro non trovato');
    expect(el.querySelector('button')?.textContent).toContain('Close');
  });

  it('si chiude', async () => {
    el.querySelector<HTMLButtonElement>('button')!.click();
    await fixture.whenStable();

    expect(notifier.current()).toBeNull();
    expect(el.querySelector('[role="alert"]')).toBeNull();
  });
});
