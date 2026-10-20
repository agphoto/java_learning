package it.javalearning.basic.libro;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javalearning.basic.errore.BookNotFoundException;
import it.javalearning.basic.errore.DuplicateIsbnException;

@Service
@Transactional(readOnly = true)
public class BookService {

	private final BookRepository repository;

	public BookService(BookRepository repository) {
		this.repository = repository;
	}

	public List<Book> findAll() {
		return this.repository.findAll();
	}

	public Book findById(long id) {
		// --- ECCEZIONI (da implementare) -------------------------------------
		// Libro inesistente: un'eccezione di dominio con l'id, tradotta in 404 dal gestore.
		return this.repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));

		// Provvisorio: NoSuchElementException, che senza gestione diventa un 500.
		// return this.repository.findById(id).orElseThrow();
	}

	@Transactional
	public Book create(BookRequest request) {
		// --- ECCEZIONI (da implementare) -------------------------------------
		// ISBN già presente: controllo applicativo (409) prima del vincolo del database.
		if (this.repository.existsByIsbn(request.isbn())) {
			throw new DuplicateIsbnException(request.isbn());
		}

		// Provvisorio: il duplicato arriva al vincolo uk_book_isbn e diventa
		// DataIntegrityViolationException (500 senza gestione).
		return this.repository.save(new Book(request.isbn(), request.title(), request.author()));
	}

	@Transactional
	public Book update(long id, BookRequest request) {
		Book book = findById(id);
		book.update(request.isbn(), request.title(), request.author());
		// flush: la violazione del vincolo emerge qui e non al commit, fuori dal metodo.
		this.repository.flush();
		return book;
	}

	@Transactional
	public void delete(long id) {
		this.repository.delete(findById(id));
	}

}
