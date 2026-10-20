package it.javalearning.basic.libro;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import it.javalearning.basic.TestOutcomeLogger;

/** Solo i casi di successo: i test sugli errori sono parte dell'esercizio. */
@SpringBootTest
@ExtendWith(TestOutcomeLogger.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookControllerTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void listReturnsSeedData() {
		assertThat(this.mvc.get().uri("/api/books")).hasStatusOk()
				.bodyJson()
				.extractingPath("$.length()")
				.isEqualTo(4);
	}

	@Test
	void getReturnsBook() {
		assertThat(this.mvc.get().uri("/api/books/1")).hasStatusOk()
				.bodyJson()
				.extractingPath("$.title")
				.isEqualTo("Il nome della rosa");
	}

	@Test
	void createReturns201WithLocation() {
		assertThat(this.mvc.post()
				.uri("/api/books")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"isbn":"9780201633610","title":"Design Patterns","author":"Erich Gamma"}
						"""))
				.hasStatus(HttpStatus.CREATED)
				.hasHeader("Location", "/api/books/5");
	}

	@Test
	void updateChangesBook() {
		assertThat(this.mvc.put()
				.uri("/api/books/4")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"isbn":"9780134685991","title":"Effective Java, 3rd ed.","author":"Joshua Bloch"}
						"""))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.title")
				.isEqualTo("Effective Java, 3rd ed.");
	}

	@Test
	void deleteReturns204() {
		assertThat(this.mvc.delete().uri("/api/books/1")).hasStatus(HttpStatus.NO_CONTENT);
	}

	// --- Errori 400 ------------------------------------------------------------

	/** Validazione: un elemento di errors per ogni campo non valido (i messaggi dipendono dal locale). */
	@Test
	void createWithInvalidFieldsReturns400WithFieldErrors() {
		assertThat(this.mvc.post()
			.uri("/api/books")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"isbn":"123","title":"","author":"X"}
					"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.hasPathSatisfying("$.status", status -> status.assertThat().isEqualTo(400))
			.hasPathSatisfying("$.instance", instance -> instance.assertThat().isEqualTo("/api/books"))
			.hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("VALIDATION_FAILED"))
			.extractingPath("$.errors[*].field")
			.asArray()
			.containsExactlyInAnyOrder("isbn", "title");
	}

	/**
	 * update ha @Positive sull'id: method validation (HandlerMethodValidationException), ma
	 * stessa risposta del caso @Valid @RequestBody.
	 */
	@Test
	void updateWithInvalidFieldsReturns400WithFieldErrors() {
		assertThat(this.mvc.put()
			.uri("/api/books/1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"isbn":"9788804668237","title":"Il nome della rosa","author":""}
					"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("VALIDATION_FAILED"))
			.extractingPath("$.errors[*].field")
			.asArray()
			.containsExactly("author");
	}

	/** Errori sul parametro e sul corpo nella stessa risposta: la method validation li raccoglie tutti. */
	@Test
	void updateWithInvalidIdAndFieldsReturnsAllErrors() {
		assertThat(this.mvc.put()
			.uri("/api/books/-1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"isbn":"123","title":"Il nome della rosa","author":"Umberto Eco"}
					"""))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.hasPathSatisfying("$.type",
					type -> type.assertThat().isEqualTo("https://java-learning.local/errors/validation-failed"))
			.hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("VALIDATION_FAILED"))
			.extractingPath("$.errors[*].field")
			.asArray()
			.containsExactlyInAnyOrder("id", "isbn");
	}

	/** JSON malformato: 400 ereditato da ResponseEntityExceptionHandler, senza errors. */
	@Test
	void createWithMalformedJsonReturns400Problem() {
		assertThat(this.mvc.post()
			.uri("/api/books")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"isbn\":"))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
			.bodyJson()
			.doesNotHavePath("$.errors");
	}

	/** Path variable non convertibile in long: TypeMismatchException, anch'essa ereditata. */
	@Test
	void getWithNonNumericIdReturns400Problem() {
		assertThat(this.mvc.get().uri("/api/books/abc")).hasStatus(HttpStatus.BAD_REQUEST)
			.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void getMissingReturns404Problem() {
		assertThat(this.mvc.get().uri("/api/books/999"))
				.hasStatus(HttpStatus.NOT_FOUND)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.hasPathSatisfying("$.type",
						type -> type.assertThat().isEqualTo("https://java-learning.local/errors/book-not-found"))
				.hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("BOOK_NOT_FOUND"))
				.hasPathSatisfying("$.bookId", bookId -> bookId.assertThat().isEqualTo(999))
				.extractingPath("$.detail")
				.isEqualTo("Libro 999 inesistente");
	}

}
