import { Injectable, signal } from '@angular/core';
import { IT } from './it';
import { EN } from './en';

export type Lang = 'it' | 'en';

const DICTIONARIES: Record<Lang, Record<string, string>> = { it: IT, en: EN };

/**
 * Lingua corrente e dizionario dell'interfaccia. Volutamente minimale: copre solo
 * i testi dell'app e gli errori senza risposta dal server (capitolo 10).
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly current = signal<Lang>('it');

  readonly lang = this.current.asReadonly();

  setLang(lang: Lang): void {
    this.current.set(lang);
  }

  /** Testo per la chiave nella lingua corrente; se manca, la chiave stessa. */
  t(key: string): string {
    return DICTIONARIES[this.current()][key] ?? key;
  }
}
