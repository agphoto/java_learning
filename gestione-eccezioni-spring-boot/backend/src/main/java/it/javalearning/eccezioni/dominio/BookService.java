package it.javalearning.eccezioni.dominio;

import it.javalearning.eccezioni.api.BookRequest;
import it.javalearning.eccezioni.errore.ErrorCode;
import it.javalearning.eccezioni.errore.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

	private final BookRepository books;

	public BookService(BookRepository books) {
		this.books = books;
	}

	@Transactional(readOnly = true)
	public Book findById(long id) {
		return this.books.findById(id).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOK_NOT_FOUND, id));
	}

	/**
	 * {@code saveAndFlush} forza l'INSERT dentro il metodo: un ISBN duplicato emerge
	 * qui come {@code DataIntegrityViolationException} e non al commit (capitolo 07).
	 */
	@Transactional
	public Book create(BookRequest request) {
		return this.books.saveAndFlush(new Book(request.isbn(), request.title(), request.author()));
	}

}
