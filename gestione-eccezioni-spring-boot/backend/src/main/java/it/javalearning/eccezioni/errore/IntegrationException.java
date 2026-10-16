package it.javalearning.eccezioni.errore;

/** Un sistema esterno ha fallito o non è raggiungibile. */
public final class IntegrationException extends ApplicationException {

	public IntegrationException(ErrorCode code, Object... args) {
		super(code, args);
	}

	public IntegrationException(ErrorCode code, Throwable cause, Object... args) {
		super(code, cause, args);
	}

}
