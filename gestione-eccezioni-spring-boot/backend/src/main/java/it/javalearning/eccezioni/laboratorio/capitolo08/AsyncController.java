package it.javalearning.eccezioni.laboratorio.capitolo08;

import java.util.concurrent.CompletableFuture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AsyncController {

	/**
	 * Spring MVC sospende la richiesta e la riprende quando il future si completa;
	 * se si completa con un'eccezione, questa entra nella catena dei resolver.
	 */
	@GetMapping("/laboratorio/capitolo08/future")
	public CompletableFuture<String> future() {
		return CompletableFuture.supplyAsync(() -> {
			throw new IllegalStateException("future fallito");
		});
	}

}
