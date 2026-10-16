package it.javalearning.eccezioni.web.sicurezza;

import java.io.IOException;

import it.javalearning.eccezioni.errore.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 come ProblemDetail. Volutamente NON invia {@code WWW-Authenticate: Basic}:
 * quell'header farebbe aprire al browser il suo popup di login (capitolo 08).
 */
@Component
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ProblemDetailWriter writer;

	ProblemDetailAuthenticationEntryPoint(ProblemDetailWriter writer) {
		this.writer = writer;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		this.writer.write(ErrorCode.UNAUTHENTICATED, authException, request, response);
	}

}
