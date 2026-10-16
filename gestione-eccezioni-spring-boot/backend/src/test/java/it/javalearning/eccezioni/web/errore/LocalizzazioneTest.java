package it.javalearning.eccezioni.web.errore;

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
 * Capitolo 05 – title e detail sono localizzati in base ad Accept-Language;
 * il code resta invariato. Lingue supportate: it (default e fallback) ed en.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LocalizzazioneTest {

	private static final String LIMITE = "{\"userId\":2,\"bookId\":1}";

	@Autowired
	MockMvcTester mvc;

	@Test
	void italianoDefault() {
		var result = postLoan(null);

		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Limite di prestiti superato");
		assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("Hai già 3 libri");
	}

	@Test
	void inglese() {
		var result = postLoan("en");

		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Loan limit exceeded");
		assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("You already have 3 books");
	}

	@Test
	void ingleseConRegioneEQualita() {
		assertThat(postLoan("en-US,en;q=0.9")).bodyJson().extractingPath("$.title").isEqualTo("Loan limit exceeded");
	}

	@Test
	void linguaNonSupportataRicadeSullItaliano() {
		assertThat(postLoan("fr-FR,fr;q=0.9")).bodyJson()
			.extractingPath("$.title")
			.isEqualTo("Limite di prestiti superato");
	}

	@Test
	void codeNonTradotto() {
		assertThat(postLoan("it")).bodyJson().extractingPath("$.code").isEqualTo("LOAN_LIMIT_EXCEEDED");
		assertThat(postLoan("en")).bodyJson().extractingPath("$.code").isEqualTo("LOAN_LIMIT_EXCEEDED");
	}

	@Test
	void eccezioneStandardMappataUsaIlCatalogo() {
		var result = this.mvc.get().uri("/api/books/abc").header(HttpHeaders.ACCEPT_LANGUAGE, "en").exchange();

		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TYPE_MISMATCH");
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid parameter");
	}

	@Test
	void eccezioneStandardNonMappataUsaLeChiaviDiSpring() {
		var result = this.mvc.post()
			.uri("/api/books")
			.header(HttpHeaders.ACCEPT_LANGUAGE, "it")
			.contentType(MediaType.TEXT_PLAIN)
			.content("ciao")
			.exchange();

		assertThat(result).hasStatus(415);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("HTTP_415");
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Tipo di contenuto non supportato");
	}

	@Test
	void erroreDiRoutingLocalizzato() {
		var result = this.mvc.get().uri("/api/nonexistent").header(HttpHeaders.ACCEPT_LANGUAGE, "en").exchange();

		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Resource not found");
	}

	private MvcTestResult postLoan(String acceptLanguage) {
		var request = this.mvc.post().uri("/api/loans").contentType(MediaType.APPLICATION_JSON).content(LIMITE);
		if (acceptLanguage != null) {
			request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
		}
		return request.exchange();
	}

}
