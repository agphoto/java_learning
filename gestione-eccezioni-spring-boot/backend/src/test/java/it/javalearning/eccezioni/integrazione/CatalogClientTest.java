package it.javalearning.eccezioni.integrazione;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitolo 08 – Gli errori di un servizio esterno (qui simulato dalla stessa
 * applicazione) vengono tradotti in 502/503 senza esporre i dettagli della chiamata.
 * Serve un server reale: il client HTTP chiama davvero il catalogo simulato.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CatalogClientTest {

	@LocalServerPort
	int port;

	@Test
	void catalogoRisponde() {
		ResponseEntity<String> response = get("ok");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).contains("9788804668237");
	}

	@Test
	void catalogoInErroreDiventa502() {
		ResponseEntity<String> response = get("error");

		assertThat(response.getStatusCode().value()).isEqualTo(502);
		assertThat(response.getBody()).contains("\"code\":\"EXTERNAL_SERVICE_ERROR\"");
		assertNessunDettaglioDellaChiamata(response.getBody());
	}

	@Test
	void catalogoNonDisponibileDiventa503() {
		ResponseEntity<String> response = get("unavailable");

		assertThat(response.getStatusCode().value()).isEqualTo(503);
		assertThat(response.getBody()).contains("\"code\":\"EXTERNAL_SERVICE_UNAVAILABLE\"");
		assertNessunDettaglioDellaChiamata(response.getBody());
	}

	private ResponseEntity<String> get(String simulate) {
		return RestClient.create("http://localhost:" + this.port)
			.get()
			.uri("/api/books/1/catalog-info?simulate={s}", simulate)
			.retrieve()
			.onStatus(status -> true, (req, res) -> { })
			.toEntity(String.class);
	}

	private static void assertNessunDettaglioDellaChiamata(String body) {
		assertThat(body).doesNotContain("simulated-catalog").doesNotContain("localhost:");
	}

}
