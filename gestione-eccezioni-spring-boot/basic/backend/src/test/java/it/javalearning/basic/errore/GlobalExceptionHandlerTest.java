package it.javalearning.basic.errore;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.context.request.ServletWebRequest;

import it.javalearning.basic.TestOutcomeLogger;
import it.javalearning.basic.libro.BookController;
import it.javalearning.basic.libro.BookRequest;

/**
 * I casi di validazione che nessun endpoint di basic produce: si invoca l'handler
 * direttamente, con eccezioni costruite come le costruirebbe il framework.
 */
@ExtendWith(TestOutcomeLogger.class)
class GlobalExceptionHandlerTest {

	private static ValidatorFactory factory;

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@BeforeAll
	static void createValidator() {
		factory = Validation.buildDefaultValidatorFactory();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	/** Le violazioni di un @Validated sul metodo update: "update.id" e "update.request.isbn". */
	@Test
	void constraintViolationBecomesValidationProblemWithFieldNames() throws NoSuchMethodException {
		Method update = BookController.class.getMethod("update", long.class, BookRequest.class);
		Set<ConstraintViolation<BookController>> violations = factory.getValidator()
			.forExecutables()
			.validateParameters(new BookController(null), update,
					new Object[] { -1L, new BookRequest("123", "Titolo", "Autore") });

		ProblemDetail problem = this.handler.handleConstraintViolation(new ConstraintViolationException(violations));

		assertThat(problem.getStatus()).isEqualTo(400);
		assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_FAILED");
		assertThat(errorFields(problem)).containsExactlyInAnyOrder("id", "isbn");
	}

	/** Il valore di ritorno non valido è un difetto del server: 500 del catalogo, senza errors. */
	@Test
	void returnValueViolationBecomesInternalError() {
		MethodValidationException ex = new MethodValidationException(MethodValidationResult.emptyResult());
		ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse());

		ResponseEntity<Object> response = this.handler.handleMethodValidationException(ex, new HttpHeaders(),
				HttpStatus.INTERNAL_SERVER_ERROR, request);

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		ProblemDetail problem = (ProblemDetail) response.getBody();
		assertThat(problem).isNotNull();
		assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR").doesNotContainKey("errors");
	}

	@SuppressWarnings("unchecked")
	private static List<String> errorFields(ProblemDetail problem) {
		Map<String, Object> properties = problem.getProperties();
		assertThat(properties).isNotNull();
		return ((List<Map<String, String>>) properties.get("errors")).stream().map(e -> e.get("field")).toList();
	}

}
