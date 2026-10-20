package it.javalearning.basic.libro;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

	@GetMapping
	public List<BookResponse> list() {
		return this.bookService.findAll().stream().map(BookResponse::from).toList();
	}

	@GetMapping("/{id}")
	public BookResponse get(@PathVariable long id) {
		return BookResponse.from(this.bookService.findById(id));
	}

	@PostMapping
	public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
		Book book = this.bookService.create(request);
		return ResponseEntity.created(URI.create("/api/books/" + book.getId())).body(BookResponse.from(book));
	}

	// @Positive è un vincolo diretto su un parametro: attiva la method validation per tutto
	// il metodo, corpo compreso, e gli errori arrivano come HandlerMethodValidationException.
	@PutMapping("/{id}")
	public BookResponse update(@PathVariable @Positive long id, @Valid @RequestBody BookRequest request) {
		return BookResponse.from(this.bookService.update(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable long id) {
		this.bookService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
