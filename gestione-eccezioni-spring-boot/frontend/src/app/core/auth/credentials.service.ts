import { Injectable, signal } from '@angular/core';

export type Credentials = 'none' | 'utente' | 'admin';

/** Utenti di prova del backend: username = password (solo per il laboratorio). */
@Injectable({ providedIn: 'root' })
export class CredentialsService {
  private readonly selected = signal<Credentials>('none');

  readonly current = this.selected.asReadonly();

  set(credentials: Credentials): void {
    this.selected.set(credentials);
  }

  authorizationHeader(): string | null {
    const user = this.selected();
    return user === 'none' ? null : 'Basic ' + btoa(`${user}:${user}`);
  }
}
