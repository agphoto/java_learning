package it.javalearning.eccezioni.laboratorio.capitolo02;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitolo 02 – Quando nessun HandlerExceptionResolver gestisce l'eccezione, questa
 * risale fino al container servlet, che fa un forward verso /error dove risponde
 * BasicErrorController. Serve un server reale: MockMvc non esegue il forward.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FallbackErrorControllerTest {

	@LocalServerPort
	int port;

	@Test
	void eccezioneNonGestitaArrivaABasicErrorController() {
		ResponseEntity<Map<String, Object>> response = RestClient.create("http://localhost:" + this.port)
			.get()
			.uri("/laboratorio/capitolo02/unhandled")
			.retrieve()
			.onStatus(status -> true, (req, res) -> { })
			.toEntity(new ParameterizedTypeReference<>() { });

		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody())
			.containsKeys("timestamp", "status", "error", "path")
			.containsEntry("path", "/laboratorio/capitolo02/unhandled")
			// Con le proprietà server.error.* di default né messaggio né stack trace.
			.doesNotContainKeys("trace", "message", "exception");
	}

}
