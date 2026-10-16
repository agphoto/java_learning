package it.javalearning.eccezioni.integrazione;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/** Capitolo 08 – Connessione rifiutata (ResourceAccessException) → 503. */
@SpringBootTest(properties = "catalog.base-url=http://localhost:1")
@AutoConfigureMockMvc
class CatalogoIrraggiungibileTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void connessioneRifiutataDiventa503() {
		var result = this.mvc.get().uri("/api/books/1/catalog-info?simulate=ok").exchange();

		assertThat(result).hasStatus(503);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("EXTERNAL_SERVICE_UNAVAILABLE");
		assertThat(result).bodyText().doesNotContain("localhost:1").doesNotContain("Connection refused");
	}

}
