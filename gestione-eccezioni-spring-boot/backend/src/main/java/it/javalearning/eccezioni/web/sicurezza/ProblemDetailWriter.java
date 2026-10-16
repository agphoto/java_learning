package it.javalearning.eccezioni.web.sicurezza;

import java.io.IOException;

import it.javalearning.eccezioni.errore.ErrorCode;
import it.javalearning.eccezioni.web.errore.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Scrive un ProblemDetail direttamente sulla risposta servlet, per i punti in cui
 * non c'è Spring MVC a farlo (i filtri di Spring Security).
 * <p>
 * La lingua va risolta qui con il LocaleResolver dell'applicazione: nei filtri
 * LocaleContextHolder contiene {@code request.getLocale()}, impostato da
 * RequestContextFilter, che ignora le lingue supportate e il default italiano.
 * <p>
 * Si usa il JsonMapper configurato da Spring Boot, lo stesso dei converter MVC:
 * le proprietà di estensione escono al primo livello del JSON, come nell'API.
 */
@Component
class ProblemDetailWriter {

	private final ProblemDetailFactory problems;

	private final JsonMapper jsonMapper;

	private final LocaleResolver localeResolver;

	ProblemDetailWriter(ProblemDetailFactory problems, JsonMapper jsonMapper, LocaleResolver localeResolver) {
		this.problems = problems;
		this.localeResolver = localeResolver;
		this.jsonMapper = jsonMapper;
	}

	void write(ErrorCode code, HttpServletRequest request, HttpServletResponse response) throws IOException {
		ProblemDetail problem = this.problems.create(code, new Object[0], request,
				this.localeResolver.resolveLocale(request));
		response.setStatus(problem.getStatus());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		this.jsonMapper.writeValue(response.getOutputStream(), problem);
	}

}
