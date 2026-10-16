package it.javalearning.eccezioni.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "book", uniqueConstraints = @UniqueConstraint(name = Book.UK_ISBN, columnNames = "isbn"))
public class Book {

	/** Nome del vincolo di unicità: il gestore centrale lo riconosce (capitolo 07). */
	public static final String UK_ISBN = "uk_book_isbn";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String isbn;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String author;

	protected Book() {
	}

	public Book(String isbn, String title, String author) {
		this.isbn = isbn;
		this.title = title;
		this.author = author;
	}

	public Long getId() {
		return this.id;
	}

	public String getIsbn() {
		return this.isbn;
	}

	public String getTitle() {
		return this.title;
	}

	public String getAuthor() {
		return this.author;
	}

}
