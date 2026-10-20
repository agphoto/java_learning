package it.javalearning.basic.errore;

// --- ECCEZIONI (da implementare) ---------------------------------------------
// Violazione di una regola di dominio: ISBN già presente. Usata in BookService.create
// e tradotta in 409 da GlobalExceptionHandler.
//
public class DuplicateIsbnException extends RuntimeException {

	private final String isbn;

	public DuplicateIsbnException(String isbn) {
		super("ISBN " + isbn + " già presente");
		this.isbn = isbn;
	}

	public String getIsbn() {
		return this.isbn;
	}

}
