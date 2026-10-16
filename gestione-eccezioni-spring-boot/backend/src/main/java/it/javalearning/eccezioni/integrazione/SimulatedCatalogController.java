package it.javalearning.eccezioni.integrazione;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simula il catalogo esterno dentro la stessa applicazione: risponde bene, con un
 * errore interno o come "non disponibile", in base al parametro {@code simulate}.
 */
@RestController
public class SimulatedCatalogController {

	@GetMapping("/simulated-catalog/{isbn}")
	public ResponseEntity<?> info(@PathVariable String isbn, @RequestParam(defaultValue = "ok") String simulate) {
		return switch (simulate) {
			case "error" -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("boom: NullPointerException at Catalog.java:42");
			case "unavailable" -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("maintenance");
			default -> ResponseEntity.ok(new CatalogInfo(isbn, "Scheda del catalogo per " + isbn));
		};
	}

}
