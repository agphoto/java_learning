package it.javalearning.basic.libro;

public record BookResponse(Long id, String isbn, String title, String author) {

	static BookResponse from(Book book) {
		return new BookResponse(book.getId(), book.getIsbn(), book.getTitle(), book.getAuthor());
	}

}
