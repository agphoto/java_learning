package it.javalearning.eccezioni.api;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitolo 06 – Gli errori di validazione diventano un 400 VALIDATION_FAILED con un
 * elemento in "errors" per ogni campo, con messaggio localizzato.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ValidazioneTest {

	private static final String LIBRO_NON_VALIDO = "{\"isbn\":\"\",\"title\":\"\",\"author\":\"X\"}";

	@Autowired
	MockMvcTester mvc;

	@Test
	void corpoNonValido() {
		var result = postBook(LIBRO_NON_VALIDO, "it");

		assertThat(result).hasStatus(400);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Dati non validi");
		assertThat(result).bodyJson()
			.extractingPath("$.errors[?(@.field == 'title')].code")
			.asArray()
			.containsExactly("NotBlank");
		assertThat(result).bodyJson()
			.extractingPath("$.errors[?(@.field == 'title')].message")
			.asArray()
			.containsExactly("Il campo è obbligatorio.");
		// L'ISBN vuoto viola sia NotBlank sia Pattern: due elementi per lo stesso campo.
		assertThat(result).bodyJson()
			.extractingPath("$.errors[?(@.field == 'isbn')].code")
			.asArray()
			.containsExactlyInAnyOrder("NotBlank", "Pattern");
		assertThat(result).bodyJson().extractingPath("$.errors[?(@.field == 'author')]").asArray().isEmpty();
	}

	@Test
	void messaggiInInglese() {
		var result = postBook(LIBRO_NON_VALIDO, "en");

		assertThat(result).bodyJson()
			.extractingPath("$.errors[?(@.field == 'title')].message")
			.asArray()
			.containsExactly("This field is required.");
	}

	@Test
	void messaggioSpecificoPerCampoEArgomentiDelVincolo() {
		String titoloLungo = "x".repeat(201);
		var result = postBook("{\"isbn\":\"123\",\"title\":\"" + titoloLungo + "\",\"author\":\"X\"}", "it");

		assertThat(result).bodyJson()
			.extractingPath("$.errors[?(@.field == 'isbn')].message")
			.asArray()
			.containsExactly("L'ISBN deve essere composto da 13 cifre.");
		assertThat(result).bodyJson()
			.extractingPath("$.errors[?(@.field == 'title')].message")
			.asArray()
			.containsExactly("Il testo può contenere al massimo 200 caratteri.");
	}

	@Test
	void corpoDelPrestitoNonValido() {
		var result = this.mvc.post()
			.uri("/api/loans")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"bookId\":-1}")
			.exchange();

		assertThat(result).hasStatus(400);
		assertThat(result).bodyJson().extractingPath("$.errors[?(@.field == 'userId')].code").asArray().containsExactly("NotNull");
		assertThat(result).bodyJson().extractingPath("$.errors[?(@.field == 'bookId')].code").asArray().containsExactly("Positive");
	}

	@Test
	void parametroNonValido() {
		var result = this.mvc.get().uri("/api/books/0").exchange();

		assertThat(result).hasStatus(400);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("id");
		assertThat(result).bodyJson().extractingPath("$.errors[0].code").isEqualTo("Positive");
		assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("Il valore deve essere positivo.");
	}

	private MvcTestResult postBook(String json, String lang) {
		return this.mvc.post()
			.uri("/api/books")
			.header(HttpHeaders.ACCEPT_LANGUAGE, lang)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

}
