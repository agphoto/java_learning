package it.javalearning.eccezioni.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint che simula un bug, per osservare la gestione di un errore inatteso (500). */
@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

	@GetMapping("/unexpected-error")
	public String unexpectedError() {
		throw new IllegalStateException("dettaglio interno segreto");
	}

}
