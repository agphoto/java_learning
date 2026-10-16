package it.javalearning.eccezioni.api;

import java.net.URI;

import it.javalearning.eccezioni.dominio.Book;
import it.javalearning.eccezioni.dominio.BookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

	private final BookService bookService;

	public BookController(BookService bookService) {
		this.bookService = bookService;
	}

	@GetMapping("/{id}")
	public BookResponse get(@PathVariable @Positive long id) {
		return BookResponse.from(this.bookService.findById(id));
	}

	@PostMapping
	public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
		Book book = this.bookService.create(request);
		return ResponseEntity.created(URI.create("/api/books/" + book.getId())).body(BookResponse.from(book));
	}

}
