package it.javalearning.eccezioni.errore;

/** La risorsa richiesta non esiste. */
public final class ResourceNotFoundException extends DomainException {

	public ResourceNotFoundException(ErrorCode code, Object... args) {
		super(code, args);
	}

}
