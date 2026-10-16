package it.javalearning.eccezioni.errore;

/** Una regola di business impedisce l'operazione (conflitto, limite superato…). */
public final class BusinessRuleException extends DomainException {

	public BusinessRuleException(ErrorCode code, Object... args) {
		super(code, args);
	}

}
