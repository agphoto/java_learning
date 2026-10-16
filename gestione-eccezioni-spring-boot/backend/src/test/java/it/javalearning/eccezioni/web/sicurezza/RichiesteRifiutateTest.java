package it.javalearning.eccezioni.web.sicurezza;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitolo 08 – Lo StrictHttpFirewall di Spring Security rifiuta gli URL sospetti
 * prima di ogni altro filtro. Senza un RequestRejectedHandler la risposta arriva da
 * /error nel formato di BasicErrorController, fuori contratto. Server reale e
 * java.net.http per non far normalizzare l'URL al client.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RichiesteRifiutateTest {

	@LocalServerPort
	int port;

	@ParameterizedTest
	@ValueSource(strings = { "/api/books//1", "/api/books/1;jsessionid=x" })
	void urlRifiutatoDalFirewallEUnProblemDetail(String path) throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path)).build(),
					HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/problem+json"));
		assertThat(response.body()).contains("\"code\":\"MALFORMED_REQUEST\"").contains("\"traceId\"");
	}

}
