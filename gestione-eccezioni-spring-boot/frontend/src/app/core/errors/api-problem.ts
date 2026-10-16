import { HttpErrorResponse } from '@angular/common/http';

/** Un errore di validazione su un campo (capitolo 06). */
export interface FieldErrorItem {
  field: string;
  code: string;
  message: string;
}

/**
 * Modello di un errore dell'API: RFC 9457 più le estensioni del progetto.
 * I codici NETWORK_ERROR, UNKNOWN_ERROR e CLIENT_ERROR nascono nel client.
 */
export interface ApiProblem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  code: string;
  traceId?: string;
  timestamp?: string;
  errors?: FieldErrorItem[];
}

export function isApiProblem(value: unknown): value is ApiProblem {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate['code'] === 'string' &&
    typeof candidate['title'] === 'string' &&
    typeof candidate['status'] === 'number'
  );
}

/**
 * Converte un errore HTTP in ApiProblem. Il corpo del backend viene usato così com'è
 * (strada A: i testi sono già nella lingua giusta); il client scrive i propri testi
 * solo quando il server non ha risposto o ha risposto con qualcosa di inatteso.
 */
export function toApiProblem(err: HttpErrorResponse, t: (key: string) => string): ApiProblem {
  if (isApiProblem(err.error)) {
    return err.error;
  }
  if (err.status === 0) {
    return clientProblem('NETWORK_ERROR', 0, 'errors.network', t);
  }
  return clientProblem('UNKNOWN_ERROR', err.status, 'errors.unknown', t);
}

export function clientProblem(code: string, status: number, keyPrefix: string, t: (key: string) => string): ApiProblem {
  return { type: 'about:blank', code, status, title: t(`${keyPrefix}.title`), detail: t(`${keyPrefix}.detail`) };
}
