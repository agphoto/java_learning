package it.javalearning.eccezioni.laboratorio.capitolo02;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Capitolo 02 – Questo controller vive fuori dal package {@code api}, quindi fuori
 * dall'ambito del gestore centrale: la sua eccezione non viene gestita da nessun
 * resolver e arriva al container servlet, che inoltra a /error.
 */
@RestController
public class UnhandledController {

	@GetMapping("/laboratorio/capitolo02/unhandled")
	public String unhandled() {
		throw new IllegalStateException("nessuno mi gestisce");
	}

}
