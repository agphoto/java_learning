package it.javalearning.eccezioni.web.errore;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Errori che nascono <em>prima</em> che esista un controller (capitolo 03).
 * <p>
 * Un URL inesistente o un metodo HTTP non supportato vengono scoperti durante la
 * ricerca dell'handler: non c'è un controller, quindi un advice con
 * {@code basePackages} non si applica. Questo advice non ha selettori e ha la
 * precedenza più bassa, così interviene solo per questi casi.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class RoutingErrorAdvice {

	private final ProblemDetailFactory problems;

	private final MessageSource messageSource;

	public RoutingErrorAdvice(ProblemDetailFactory problems, MessageSource messageSource) {
		this.problems = problems;
		this.messageSource = messageSource;
	}

	@ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class })
	ResponseEntity<ProblemDetail> handleRouting(Exception ex, HttpServletRequest request) {
		ErrorResponse error = (ErrorResponse) ex;
		ProblemDetail problem = error.updateAndGetBody(this.messageSource, LocaleContextHolder.getLocale());
		this.problems.fromStandard(problem, ex, error.getStatusCode(), request);
		return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders()).body(problem);
	}

}
