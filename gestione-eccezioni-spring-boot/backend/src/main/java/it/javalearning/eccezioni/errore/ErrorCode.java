package it.javalearning.eccezioni.errore;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;

/**
 * Catalogo dei codici d'errore stabili dell'applicazione.
 * <p>
 * Il nome della costante è il {@code code} esposto al client: non va mai tradotto
 * né rinominato, perché il client può basarci la propria logica. Da ogni codice
 * derivano lo stato HTTP, la chiave dei messaggi e l'URI del {@code type} RFC 9457.
 * <p>
 * Tenere {@link HttpStatus} qui è un compromesso consapevole (vedi capitolo 01):
 * il dominio "conosce" HTTP, in cambio di un unico punto di verità.
 */
public enum ErrorCode {

	BOOK_NOT_FOUND(HttpStatus.NOT_FOUND),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND),
	BOOK_ALREADY_ON_LOAN(HttpStatus.CONFLICT),
	LOAN_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT),
	DUPLICATE_ISBN(HttpStatus.CONFLICT),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
	TYPE_MISMATCH(HttpStatus.BAD_REQUEST),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
	ACCESS_DENIED(HttpStatus.FORBIDDEN),
	EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY),
	EXTERNAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private static final String TYPE_BASE = "https://java-learning.local/errors/";

	private final HttpStatus status;

	private final String kebab;

	ErrorCode(HttpStatus status) {
		this.status = status;
		this.kebab = name().toLowerCase(Locale.ROOT).replace('_', '-');
	}

	public HttpStatus status() {
		return this.status;
	}

	/** Prefisso delle chiavi {@code .title} e {@code .detail} in {@code messages*.properties}. */
	public String messageKey() {
		return "error." + this.kebab;
	}

	/** Identificatore RFC 9457 del tipo di problema. Non deve necessariamente rispondere. */
	public URI type() {
		return URI.create(TYPE_BASE + this.kebab);
	}

}
