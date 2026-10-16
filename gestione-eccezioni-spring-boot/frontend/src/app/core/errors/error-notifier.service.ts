import { Injectable, signal } from '@angular/core';
import { ApiProblem } from './api-problem';

/** L'errore "globale" attualmente mostrato nel banner. */
@Injectable({ providedIn: 'root' })
export class ErrorNotifierService {
  private readonly shown = signal<ApiProblem | null>(null);

  readonly current = this.shown.asReadonly();

  show(problem: ApiProblem): void {
    this.shown.set(problem);
  }

  clear(): void {
    this.shown.set(null);
  }
}
