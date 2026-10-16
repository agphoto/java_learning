package it.javalearning.eccezioni.errore;

/**
 * Violazione di una regola del dominio: la richiesta è ben formata ma non può
 * essere soddisfatta. Non-sealed: il dominio può aggiungere sottotipi propri.
 */
public abstract non-sealed class DomainException extends ApplicationException {

	protected DomainException(ErrorCode code, Object... args) {
		super(code, args);
	}

}
