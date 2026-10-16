package it.javalearning.eccezioni.web.sicurezza;

import java.io.IOException;

import it.javalearning.eccezioni.errore.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 come ProblemDetail. */
@Component
class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

	private final ProblemDetailWriter writer;

	ProblemDetailAccessDeniedHandler(ProblemDetailWriter writer) {
		this.writer = writer;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		this.writer.write(ErrorCode.ACCESS_DENIED, request, response);
	}

}
