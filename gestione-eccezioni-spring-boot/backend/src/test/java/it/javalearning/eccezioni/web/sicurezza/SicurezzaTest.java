package it.javalearning.eccezioni.web.sicurezza;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitolo 08 – Gli errori di Spring Security nascono in un filtro, prima di
 * DispatcherServlet: nessun advice li vede. Entry point e access denied handler
 * personalizzati producono lo stesso ProblemDetail dell'API.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SicurezzaTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void senzaCredenziali() {
		var result = this.mvc.get().uri("/api/admin/stats").exchange();

		assertThat(result).hasStatus(401).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Autenticazione richiesta");
		assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/admin/stats");
		assertThat(result).doesNotContainHeader(HttpHeaders.WWW_AUTHENTICATE);
	}

	@Test
	void credenzialiErrate() {
		var result = this.mvc.get().uri("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, basic("admin", "sbagliata")).exchange();

		assertThat(result).hasStatus(401);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
		assertThat(result).doesNotContainHeader(HttpHeaders.WWW_AUTHENTICATE);
	}

	@Test
	void utenteSenzaRuolo() {
		var result = this.mvc.get().uri("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, basic("utente", "utente")).exchange();

		assertThat(result).hasStatus(403).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
	}

	@Test
	void admin() {
		var result = this.mvc.get().uri("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, basic("admin", "admin")).exchange();

		assertThat(result).hasStatus(200);
		assertThat(result).bodyJson().extractingPath("$.books").isEqualTo(6);
	}

	@Test
	void inglese() {
		var result = this.mvc.get().uri("/api/admin/stats").header(HttpHeaders.ACCEPT_LANGUAGE, "en").exchange();

		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Authentication required");
	}

	@Test
	void restoDellApiPubblico() {
		assertThat(this.mvc.get().uri("/api/books/1")).hasStatus(200);
	}

	private static String basic(String user, String password) {
		return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
	}

}
