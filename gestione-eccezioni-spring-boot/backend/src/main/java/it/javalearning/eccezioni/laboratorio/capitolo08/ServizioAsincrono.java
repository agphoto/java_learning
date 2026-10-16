package it.javalearning.eccezioni.laboratorio.capitolo08;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ServizioAsincrono {

	/** Il chiamante riceve il controllo subito: l'eccezione non può raggiungerlo. */
	@Async
	public void fuocoEDimentica() {
		throw new IllegalStateException("fallito in background");
	}

}
