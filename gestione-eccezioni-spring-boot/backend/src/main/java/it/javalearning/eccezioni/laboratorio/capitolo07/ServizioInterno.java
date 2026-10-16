package it.javalearning.eccezioni.laboratorio.capitolo07;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServizioInterno {

	/**
	 * Partecipa alla transazione del chiamante (REQUIRED). Uscendo con un'eccezione
	 * unchecked, il proxy marca l'intera transazione come rollback-only, anche se il
	 * chiamante poi cattura l'eccezione.
	 */
	@Transactional
	public void fallisci() {
		throw new IllegalStateException("fallimento interno");
	}

}
