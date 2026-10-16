package it.javalearning.eccezioni.web.errore;

import it.javalearning.eccezioni.errore.ErrorCode;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Assegna un code stabile alle eccezioni standard di Spring MVC, che non portano
 * un {@link ErrorCode} con sé. Ciò che non è mappato riceve {@code HTTP_<status>}.
 */
final class StandardExceptionCodes {

	private StandardExceptionCodes() {
	}

	static String codeFor(Exception ex, HttpStatusCode status) {
		ErrorCode code = switch (ex) {
			case HttpMessageNotReadableException e -> ErrorCode.MALFORMED_REQUEST;
			case TypeMismatchException e -> ErrorCode.TYPE_MISMATCH;
			case NoResourceFoundException e -> ErrorCode.RESOURCE_NOT_FOUND;
			case HttpRequestMethodNotSupportedException e -> ErrorCode.METHOD_NOT_ALLOWED;
			default -> null;
		};
		return (code != null) ? code.name() : "HTTP_" + status.value();
	}

}
