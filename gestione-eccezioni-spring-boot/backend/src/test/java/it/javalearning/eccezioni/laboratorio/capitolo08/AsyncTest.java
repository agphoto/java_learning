package it.javalearning.eccezioni.laboratorio.capitolo08;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitolo 08 – Eccezioni asincrone: un @Async void non ha un chiamante a cui
 * restituire l'errore (va all'AsyncUncaughtExceptionHandler); un CompletableFuture
 * restituito da un controller torna invece nel flusso MVC e raggiunge l'advice.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AsyncTest {

	@Autowired
	ServizioAsincrono servizio;

	@Autowired
	RegistroErroriAsincroni registro;

	@Autowired
	MockMvcTester mvc;

	@Test
	void eccezioneInVoidAsyncVaAlHandler() throws InterruptedException {
		String threadDelTest = Thread.currentThread().getName();

		this.servizio.fuocoEDimentica();   // ritorna subito, senza eccezioni

		RegistroErroriAsincroni.Errore errore = this.registro.attendi(Duration.ofSeconds(5));
		assertThat(errore).isNotNull();
		assertThat(errore.eccezione()).hasMessage("fallito in background");
		assertThat(errore.metodo()).isEqualTo("fuocoEDimentica");
		assertThat(errore.thread()).isNotEqualTo(threadDelTest);
	}

	@Test
	void futureCompletatoConEccezioneArrivaAllAdvice() {
		var result = this.mvc.get().uri("/laboratorio/capitolo08/future").exchange();

		assertThat(result).hasStatus(503);
		assertThat(result).hasBodyTextEqualTo("gestito dall'advice: future fallito");
	}

}
