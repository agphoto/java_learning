package it.javalearning.eccezioni.api;

import java.util.Map;

import it.javalearning.eccezioni.dominio.BookRepository;
import it.javalearning.eccezioni.dominio.LoanRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint riservato al ruolo ADMIN, per provocare 401 e 403 (capitolo 08). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final BookRepository books;

	private final LoanRepository loans;

	public AdminController(BookRepository books, LoanRepository loans) {
		this.books = books;
		this.loans = loans;
	}

	@GetMapping("/stats")
	public Map<String, Long> stats() {
		return Map.of("books", this.books.count(), "loans", this.loans.count());
	}

}
