package it.javalearning.basic.errore;

// --- ECCEZIONI ---------------------------------------------------------------
// Gestione centralizzata: un unico advice traduce le eccezioni in ProblemDetail
// (RFC 9457). Estendere ResponseEntityExceptionHandler fa ereditare la gestione delle
// eccezioni standard di Spring MVC (validazione, JSON malformato, 404 di routing, 405...).
//
// Contratto delle risposte prodotte qui:
// - type e code identificano il tipo di problema (per la macchina, mai tradotti);
// - title è fisso per tipo, detail descrive l'occorrenza (per la persona);
// - i dati dell'occorrenza (bookId, isbn, errors) sono estensioni: il client decide su
//   quelle, mai interpretando detail.
// - ogni errore di validazione dell'input, da qualunque meccanismo provenga, produce lo
//   stesso 400 VALIDATION_FAILED con errors = [{field, message}].
// Stato, type e title di ogni tipo di problema stanno nel catalogo ErrorCode.
//
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BookNotFoundException.class)
	ProblemDetail handleNotFound(BookNotFoundException ex) {
		log.error(ex.getMessage(), ex);
		ProblemDetail problem = problem(ErrorCode.BOOK_NOT_FOUND, ex.getMessage());
		problem.setProperty("bookId", ex.getBookId());
		return problem;
	}

	
	// Nel caso servono Header aggiuntivi si può utilizzare questo altro modo di implementare l'handler
	// specificando la ResppnseEntity come parametro di ritorno
	// --------------------------------------------------------------------------------------------------
	// @ExceptionHandler(BookNotFoundException.class)
	// ResponseEntity<ProblemDetail> handleNotFound(BookNotFoundException ex) {
	// 	ProblemDetail problem = problem(ErrorCode.BOOK_NOT_FOUND, ex.getMessage());
	// 	return ResponseEntity.status(problem.getStatus())
	// 			.header("Retry-After", "5") // un header extra, per esempio
	// 			.body(problem);
	// }

	@ExceptionHandler(DuplicateIsbnException.class)
	ProblemDetail handleDuplicateIsbn(DuplicateIsbnException ex) {
		ProblemDetail problem = problem(ErrorCode.DUPLICATE_ISBN, ex.getMessage());
		problem.setProperty("isbn", ex.getIsbn());
		return problem;
	}

	// Rete di sicurezza per la violazione del vincolo uk_book_isbn (es. update
	// concorrenti).
	// Nessuna estensione: il messaggio del database non va mai al client.
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		return problem(ErrorCode.DATA_CONFLICT, "Dati in conflitto");
	}

	// --- Validazione ----------------------------------------------------------
	// Tre meccanismi, una sola risposta (validationProblem):
	// 1. @Valid @RequestBody            → MethodArgumentNotValidException
	// 2. vincolo diretto su un parametro → HandlerMethodValidationException (tutto il metodo)
	// 3. @Validated su un bean (service) → ConstraintViolationException
	// Il valore di ritorno non valido (MethodValidationException) è invece un difetto del
	// server: 500 INTERNAL_ERROR, senza errors.

	// 1. Errori per campo del corpo, raccolti dal WebDataBinder.
	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> fieldError(e.getField(), e.getDefaultMessage()))
				.toList();
		return handleExceptionInternal(ex, validationProblem(ex.getBody(), errors), headers, status, request);
	}

	// 2. Method validation di Spring MVC: un risultato per parametro. Un oggetto validato a
	// cascata (@Valid @RequestBody) arriva come ParameterErrors con i suoi errori per campo;
	// un parametro semplice (@PathVariable, @RequestParam...) usa il proprio nome come field.
	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = new ArrayList<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			if (result instanceof ParameterErrors body) {
				body.getFieldErrors().forEach(e -> errors.add(fieldError(e.getField(), e.getDefaultMessage())));
			}
			else {
				String name = String.valueOf(result.getMethodParameter().getParameterName());
				result.getResolvableErrors().forEach(e -> errors.add(fieldError(name, e.getDefaultMessage())));
			}
		}
		return handleExceptionInternal(ex, validationProblem(ex.getBody(), errors), headers, status, request);
	}

	// Vincolo violato sul valore di ritorno di un controller: l'input era valido, il
	// difetto è del server. Stesso trattamento di handleUnexpected.
	@Override
	protected @Nullable ResponseEntity<Object> handleMethodValidationException(MethodValidationException ex,
			HttpHeaders headers, HttpStatus status, WebRequest request) {
		log.error("Valore di ritorno non valido", ex);
		ProblemDetail problem = problem(ErrorCode.INTERNAL_ERROR, "Errore inatteso");
		return handleExceptionInternal(ex, problem, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	// 3. Validazione AOP (@Validated su un bean): non è un'eccezione di Spring MVC, quindi
	// non la conosce ResponseEntityExceptionHandler e senza questo handler sarebbe un 500.
	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
		List<Map<String, String>> errors = ex.getConstraintViolations().stream()
				.map(v -> fieldError(field(v), v.getMessage()))
				.toList();
		return validationProblem(ProblemDetail.forStatus(ErrorCode.VALIDATION_FAILED.status()), errors);
	}

	// Ultima istanza: 500 senza dettagli interni (lo stack trace va nei log, non al
	// client).
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Errore inatteso", ex);
		return problem(ErrorCode.INTERNAL_ERROR, "Errore inatteso");
	}

	/**
	 * Nuovo ProblemDetail: lo stato viene dal catalogo, il detail dall'occorrenza.
	 */
	private static ProblemDetail problem(ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
		describe(problem, code);
		return problem;
	}

	/** Il 400 di validazione comune ai tre meccanismi: stesso detail, stesso errors. */
	private static ProblemDetail validationProblem(ProblemDetail problem, List<Map<String, String>> errors) {
		describe(problem, ErrorCode.VALIDATION_FAILED);
		problem.setDetail("Uno o più valori non sono validi");
		problem.setProperty("errors", errors);
		return problem;
	}

	private static Map<String, String> fieldError(String field, @Nullable String message) {
		return Map.of("field", field, "message", String.valueOf(message));
	}

	/**
	 * Nome del campo da un propertyPath di Bean Validation, allineato agli altri meccanismi:
	 * "update.request.isbn" → "isbn", "update.id" → "id". Il nodo del metodo si scarta, il
	 * parametro conta solo se non ci sono proprietà dopo di lui.
	 */
	private static String field(ConstraintViolation<?> violation) {
		String parameter = null;
		StringJoiner properties = new StringJoiner(".");
		for (Path.Node node : violation.getPropertyPath()) {
			switch (node.getKind()) {
				case PARAMETER -> parameter = node.getName();
				case PROPERTY -> properties.add(node.getName());
				default -> {
				}
			}
		}
		if (properties.length() > 0) {
			return properties.toString();
		}
		return (parameter != null) ? parameter : violation.getPropertyPath().toString();
	}

	/**
	 * type, title e code dal catalogo, anche su un ProblemDetail costruito da
	 * Spring.
	 */
	private static void describe(ProblemDetail problem, ErrorCode code) {
		problem.setType(code.type());
		problem.setTitle(code.title());
		problem.setProperty("code", code.name());
	}

}
