package it.javalearning.basic.errore;

// --- ECCEZIONI (da implementare) ---------------------------------------------
// Eccezione di dominio per un libro inesistente: unchecked, porta con sé l'id per il
// messaggio. Usata in BookService.findById e tradotta in 404 da GlobalExceptionHandler.
//
public class BookNotFoundException extends RuntimeException {

	private final long bookId;

	public BookNotFoundException(long bookId) {
		super("Libro " + bookId + " inesistente");
		this.bookId = bookId;
	}

	public long getBookId() {
		return this.bookId;
	}

}

