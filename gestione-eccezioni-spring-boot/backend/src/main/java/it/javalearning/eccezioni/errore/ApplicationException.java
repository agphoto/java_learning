package it.javalearning.eccezioni.errore;

import java.util.Arrays;

/**
 * Radice delle eccezioni applicative.
 * <p>
 * È unchecked (vedi capitolo 01) e sealed: le uniche famiglie ammesse sono gli
 * errori di dominio e quelli di integrazione con sistemi esterni. Porta con sé un
 * {@link ErrorCode} stabile e gli argomenti per comporre il messaggio localizzato.
 * Il messaggio restituito da {@link #getMessage()} è tecnico, in inglese, destinato
 * ai log: non viene mai mostrato all'utente.
 */
public abstract sealed class ApplicationException extends RuntimeException
		permits DomainException, IntegrationException {

	private final ErrorCode code;

	private final Object[] args;

	protected ApplicationException(ErrorCode code, Object... args) {
		this(code, null, args);
	}

	protected ApplicationException(ErrorCode code, Throwable cause, Object... args) {
		super(code.name() + " args=" + Arrays.toString(args), cause);
		this.code = code;
		this.args = args.clone();
	}

	public ErrorCode code() {
		return this.code;
	}

	public Object[] args() {
		return this.args.clone();
	}

}
