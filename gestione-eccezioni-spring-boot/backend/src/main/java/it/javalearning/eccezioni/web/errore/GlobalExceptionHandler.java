package it.javalearning.eccezioni.web.errore;

import it.javalearning.eccezioni.dominio.Book;
import it.javalearning.eccezioni.errore.ApplicationException;
import it.javalearning.eccezioni.errore.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;

import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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

	private final MessageSource messageSource;

	public GlobalExceptionHandler(ProblemDetailFactory problems, MessageSource messageSource) {
		this.problems = problems;
		this.messageSource = messageSource;
	}

	@ExceptionHandler(ApplicationException.class)
	ResponseEntity<ProblemDetail> handleApplication(ApplicationException ex, HttpServletRequest request) {
		ProblemDetail problem = this.problems.create(ex.code(), ex.args(), request);
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	/**
	 * Violazione di un vincolo del database (capitolo 07). Solo i vincoli noti hanno
	 * un significato per l'utente; gli altri sono bug e diventano un 500.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		if (violatesConstraint(ex, Book.UK_ISBN)) {
			ProblemDetail problem = this.problems.create(ErrorCode.DUPLICATE_ISBN, new Object[0], request);
			return ResponseEntity.status(problem.getStatus()).body(problem);
		}
		return handleUnexpected(ex, request);
	}

	/** Cerca nella catena delle cause il nome del vincolo estratto da Hibernate. */
	private static boolean violatesConstraint(Throwable ex, String constraintName) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null
					&& cve.getConstraintName().toLowerCase(Locale.ROOT).contains(constraintName)) {
				return true;
			}
		}
		return false;
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

	/** {@code @Valid @RequestBody} non valido (capitolo 06). */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Locale locale = LocaleContextHolder.getLocale();
		List<FieldErrorItem> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new FieldErrorItem(error.getField(), error.getCode(),
					this.messageSource.getMessage(error, locale)))
			.toList();
		return validationFailed(errors, request);
	}

	/** Vincoli sui parametri del metodo: path variable, request param… (capitolo 06). */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Locale locale = LocaleContextHolder.getLocale();
		List<FieldErrorItem> errors = ex.getParameterValidationResults()
			.stream()
			.flatMap(result -> result.getResolvableErrors()
				.stream()
				.map(error -> new FieldErrorItem(result.getMethodParameter().getParameterName(), constraintName(error),
						this.messageSource.getMessage(error, locale))))
			.toList();
		return validationFailed(errors, request);
	}

	private ResponseEntity<Object> validationFailed(List<FieldErrorItem> errors, WebRequest request) {
		HttpServletRequest servletRequest = ((NativeWebRequest) request).getNativeRequest(HttpServletRequest.class);
		ProblemDetail problem = this.problems.create(ErrorCode.VALIDATION_FAILED, new Object[0], servletRequest);
		problem.setProperty("errors", errors);
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	/** L'ultimo dei codici di un errore di validazione è il nome semplice del vincolo. */
	private static String constraintName(MessageSourceResolvable error) {
		String[] codes = error.getCodes();
		return (codes != null && codes.length > 0) ? codes[codes.length - 1] : "Invalid";
	}

}
