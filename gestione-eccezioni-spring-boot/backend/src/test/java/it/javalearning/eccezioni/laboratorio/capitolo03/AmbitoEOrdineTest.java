package it.javalearning.eccezioni.laboratorio.capitolo03;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitolo 03 – Regole di selezione degli @ExceptionHandler:
 * locale prima degli advice, poi gli advice nell'ordine di @Order, e dentro un
 * advice l'eccezione più specifica.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AmbitoEOrdineTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void handlerLocaleVinceSulGlobale() {
		assertThat(this.mvc.get().uri("/laboratorio/capitolo03/local")).hasBodyTextEqualTo("gestito localmente");
	}

	@Test
	void eccezionePiuSpecificaVinceDentroLoStessoAdvice() {
		assertThat(this.mvc.get().uri("/laboratorio/capitolo03/argument"))
			.hasBodyTextEqualTo("primo: IllegalArgumentException");
	}

	@Test
	void ordineDegliAdviceVinceSullaSpecificita() {
		// SecondoAdvice ha un handler più specifico (IllegalStateException), ma
		// PrimoAdvice viene interrogato prima e il suo handler RuntimeException basta.
		assertThat(this.mvc.get().uri("/laboratorio/capitolo03/state"))
			.hasBodyTextEqualTo("primo: RuntimeException");
	}

}
