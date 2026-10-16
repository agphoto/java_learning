package it.javalearning.eccezioni.web.errore;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitoli 03-04 – Contratto delle risposte d'errore: ogni errore dell'API diventa
 * un ProblemDetail (application/problem+json) con un code stabile.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProblemDetailContractTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void libroInesistente() {
		var result = this.mvc.get().uri("/api/books/999").exchange();

		assertThat(result).hasStatus(404).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("BOOK_NOT_FOUND");
		assertThat(result).bodyJson()
			.extractingPath("$.type")
			.isEqualTo("https://java-learning.local/errors/book-not-found");
		assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/books/999");
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(404);
		assertThat(result).bodyJson().hasPath("$.timestamp");
	}

	@Test
	void limiteSuperato() {
		var result = postLoan("{\"userId\":2,\"bookId\":1}");

		assertThat(result).hasStatus(422);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LOAN_LIMIT_EXCEEDED");
	}

	@Test
	void libroGiaInPrestito() {
		var result = postLoan("{\"userId\":1,\"bookId\":5}");

		assertThat(result).hasStatus(409);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("BOOK_ALREADY_ON_LOAN");
	}

	@Test
	void erroreInattesoNonEsponeDettagli() {
		var result = this.mvc.get().uri("/api/diagnostics/unexpected-error").exchange();

		assertThat(result).hasStatus(500).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
		assertThat(result).bodyText().doesNotContain("dettaglio interno segreto").doesNotContain("IllegalStateException");
	}

	@Test
	void jsonMalformato() {
		var result = postLoan("{\"userId\":");

		assertThat(result).hasStatus(400);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
	}

	@Test
	void tipoSbagliatoNelCorpo() {
		var result = postLoan("{\"userId\":1,\"bookId\":\"abc\"}");

		assertThat(result).hasStatus(400);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
	}

	@Test
	void pathVariableNonNumerica() {
		var result = this.mvc.get().uri("/api/books/abc").exchange();

		assertThat(result).hasStatus(400);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TYPE_MISMATCH");
	}

	@Test
	void urlInesistente() {
		var result = this.mvc.get().uri("/api/nonexistent").exchange();

		assertThat(result).hasStatus(404).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("RESOURCE_NOT_FOUND");
	}

	@Test
	void metodoNonSupportato() {
		var result = this.mvc.delete().uri("/api/books/1").exchange();

		assertThat(result).hasStatus(405).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
		assertThat(result).hasHeader("Allow", "GET");
	}

	private org.springframework.test.web.servlet.assertj.MvcTestResult postLoan(String json) {
		return this.mvc.post().uri("/api/loans").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

}
