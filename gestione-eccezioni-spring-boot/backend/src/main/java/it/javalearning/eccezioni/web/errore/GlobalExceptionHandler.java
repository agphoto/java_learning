package it.javalearning.eccezioni.web.errore;

import it.javalearning.eccezioni.errore.ApplicationException;
import it.javalearning.eccezioni.errore.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Gestore centrale degli errori dell'API (capitolo 03).
 * <p>
 * Estende {@link ResponseEntityExceptionHandler}, che traduce già le eccezioni
 * standard di Spring MVC in {@link ProblemDetail}; qui si aggiungono le eccezioni
 * applicative e la rete di sicurezza per quelle inattese. L'ambito è limitato al
 * package {@code api}: i controller del laboratorio restano fuori.
 */
@RestControllerAdvice(basePackages = "it.javalearning.eccezioni.api")
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final ProblemDetailFactory problems;

	public GlobalExceptionHandler(ProblemDetailFactory problems) {
		this.problems = problems;
	}

	@ExceptionHandler(ApplicationException.class)
	ResponseEntity<ProblemDetail> handleApplication(ApplicationException ex, HttpServletRequest request) {
		ProblemDetail problem = this.problems.create(ex.code(), ex.args(), request);
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	/** Rete di sicurezza: qualunque eccezione non prevista diventa un 500 generico. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Errore inatteso su {}", request.getRequestURI(), ex);
		ProblemDetail problem = this.problems.create(ErrorCode.INTERNAL_ERROR, new Object[0], request);
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem
				&& request instanceof NativeWebRequest nativeRequest) {
			HttpServletRequest servletRequest = nativeRequest.getNativeRequest(HttpServletRequest.class);
			this.problems.fromStandard(problem, ex, statusCode, servletRequest);
		}
		return response;
	}

}
