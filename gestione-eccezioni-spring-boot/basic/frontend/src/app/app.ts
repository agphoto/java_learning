import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { Book, BookService } from './book.service';
import { ProblemDetail } from './error.interceptor';

@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly bookService = inject(BookService);

  protected readonly books = signal<Book[]>([]);
  protected readonly editingId = signal<number | null>(null);

  // --- ECCEZIONI (da implementare) -------------------------------------------
  // Ultimo errore ricevuto dal backend, mostrato nel template.
  protected readonly error = signal<ProblemDetail | null>(null);

  // Nessun validatore lato client sul formato dell'ISBN: così le richieste non
  // valide arrivano al backend e se ne può osservare la risposta di errore.
  protected readonly form = inject(NonNullableFormBuilder).group({
    isbn: ['', Validators.required],
    title: ['', Validators.required],
    author: ['', Validators.required],
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.bookService.list().subscribe({
      next: (books) => this.books.set(books),
      error: (problem: ProblemDetail) => this.error.set(problem),
    });
  }

  protected edit(book: Book): void {
    this.editingId.set(book.id);
    this.form.setValue({ isbn: book.isbn, title: book.title, author: book.author });
  }

  protected cancel(): void {
    this.editingId.set(null);
    this.form.reset();
  }

  protected save(): void {
    const id = this.editingId();
    const request = this.form.getRawValue();
    const call = id === null ? this.bookService.create(request) : this.bookService.update(id, request);
    this.error.set(null);
    call.subscribe({
      next: () => {
        this.cancel();
        this.load();
      },
      error: (problem: ProblemDetail) => this.error.set(problem),
    });
  }

  protected remove(book: Book): void {
    this.bookService.delete(book.id).subscribe({
      next: () => this.load(),
      error: (problem: ProblemDetail) => this.error.set(problem),
    });
  }

  /** Per provocare un errore: chiede un libro che non esiste. */
  protected loadMissing(): void {
    this.bookService.get(999).subscribe({
      next: (book) => console.log(book),
      error: (problem: ProblemDetail) => this.error.set(problem),
    });
  }
}
