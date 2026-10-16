package it.javalearning.eccezioni.web.errore;

import it.javalearning.eccezioni.errore.ErrorCode;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.jspecify.annotations.Nullable;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Assegna un {@link ErrorCode} alle eccezioni standard di Spring MVC più comuni.
 * Le altre restano senza codice di catalogo: ricevono {@code HTTP_<status>} e i
 * testi di Spring (chiavi {@code problemDetail.*}).
 */
final class StandardExceptionCodes {

	private StandardExceptionCodes() {
	}

	@Nullable
	static ErrorCode codeFor(Exception ex) {
		return switch (ex) {
			case HttpMessageNotReadableException e -> ErrorCode.MALFORMED_REQUEST;
			case TypeMismatchException e -> ErrorCode.TYPE_MISMATCH;
			case NoResourceFoundException e -> ErrorCode.RESOURCE_NOT_FOUND;
			case HttpRequestMethodNotSupportedException e -> ErrorCode.METHOD_NOT_ALLOWED;
			default -> null;
		};
	}

}
