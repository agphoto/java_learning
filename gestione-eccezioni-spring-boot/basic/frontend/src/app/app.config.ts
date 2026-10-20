import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { errorInterceptor } from './error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(
      withInterceptors([
        // --- ECCEZIONI (da implementare) ---------------------------------------
        // Punto unico in cui leggere le risposte di errore HTTP (ProblemDetail).
        errorInterceptor,
      ]),
    ),
  ],
};
