package it.javalearning.eccezioni.web.errore;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitolo 09 – Ogni risposta d'errore porta il traceId della richiesta, lo stesso
 * che compare nei log. Ogni errore viene registrato una sola volta: ERROR con stack
 * trace per i 5xx, una riga WARN senza stack trace per i 4xx.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class TracciamentoTest {

	private static final String TRACE_ID = "[0-9a-f]{32}";

	@Autowired
	MockMvcTester mvc;

	@Test
	void traceIdNelProblemDetail() {
		var result = this.mvc.get().uri("/api/diagnostics/unexpected-error").exchange();

		assertThat(result).bodyJson().extractingPath("$.traceId").asString().matches(TRACE_ID);
	}

	@Test
	void traceIdAncheSu4xx() {
		var result = this.mvc.get().uri("/api/books/999").exchange();

		assertThat(result).bodyJson().extractingPath("$.traceId").asString().matches(TRACE_ID);
	}

	@Test
	void erroreInattesoRegistratoUnaVoltaComeErrorConLoStessoTraceId(CapturedOutput output) throws Exception {
		var result = this.mvc.get().uri("/api/diagnostics/unexpected-error").exchange();
		String traceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");

		List<String> errorLines = righe(output, " ERROR ");
		assertThat(errorLines).hasSize(1);
		assertThat(errorLines.getFirst()).contains(traceId).contains("INTERNAL_ERROR");
		assertThat(output.getOut()).contains("java.lang.IllegalStateException: dettaglio interno segreto")
			.contains("\tat it.javalearning.eccezioni.api.DiagnosticsController");
	}

	@Test
	void erroreDelClienteRegistratoComeWarnSenzaStackTrace(CapturedOutput output) {
		this.mvc.get().uri("/api/books/999").exchange();

		List<String> warnLines = righe(output, " WARN ");
		assertThat(warnLines).hasSize(1);
		assertThat(warnLines.getFirst()).contains("404").contains("BOOK_NOT_FOUND").contains("/api/books/999");
		assertThat(righe(output, " ERROR ")).isEmpty();
		assertThat(output.getOut()).doesNotContain("\tat ");
	}

	private static List<String> righe(CapturedOutput output, String livello) {
		return output.getOut().lines().filter(line -> line.contains(livello)).toList();
	}

}
