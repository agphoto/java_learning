import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('mostra i libri restituiti dal backend', async () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/books').flush([{ id: 1, isbn: '9788804668237', title: 'Il nome della rosa', author: 'Umberto Eco' }]);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('tbody td:nth-child(3)').textContent).toContain('Il nome della rosa');
    http.verify();
  });
});
