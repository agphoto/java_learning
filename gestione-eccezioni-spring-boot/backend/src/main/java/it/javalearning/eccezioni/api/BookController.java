package it.javalearning.eccezioni.api;

import java.net.URI;

import it.javalearning.eccezioni.dominio.Book;
import it.javalearning.eccezioni.dominio.BookService;
import it.javalearning.eccezioni.integrazione.CatalogClient;
import it.javalearning.eccezioni.integrazione.CatalogInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

	private final BookService bookService;

	private final CatalogClient catalogClient;

	public BookController(BookService bookService, CatalogClient catalogClient) {
		this.bookService = bookService;
		this.catalogClient = catalogClient;
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

	/** Interroga il catalogo esterno; {@code simulate} = ok | error | unavailable (capitolo 08). */
	@GetMapping("/{id}/catalog-info")
	public CatalogInfo catalogInfo(@PathVariable @Positive long id, @RequestParam(defaultValue = "ok") String simulate) {
		return this.catalogClient.fetchInfo(this.bookService.findById(id).getIsbn(), simulate);
	}

}
