package it.javalearning.eccezioni.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "loan")
public class Loan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "book_id")
	private Book book;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(nullable = false)
	private Instant loanedAt;

	private Instant returnedAt;

	protected Loan() {
	}

	public Loan(Book book, User user, Instant loanedAt) {
		this.book = book;
		this.user = user;
		this.loanedAt = loanedAt;
	}

	public Long getId() {
		return this.id;
	}

	public Book getBook() {
		return this.book;
	}

	public User getUser() {
		return this.user;
	}

	public Instant getLoanedAt() {
		return this.loanedAt;
	}

	public Instant getReturnedAt() {
		return this.returnedAt;
	}

}
