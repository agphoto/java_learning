package it.javalearning.eccezioni.web.errore;

import java.net.URI;
import java.time.Instant;

import it.javalearning.eccezioni.errore.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Unico punto in cui nasce o viene completato un {@link ProblemDetail}: lo usano
 * gli advice MVC e i gestori di Spring Security, così il formato resta identico
 * ovunque l'errore abbia origine.
 */
@Component
public class ProblemDetailFactory {

	private final TraceIdProvider traceIdProvider;

	public ProblemDetailFactory(TraceIdProvider traceIdProvider) {
		this.traceIdProvider = traceIdProvider;
	}

	/** Costruisce il ProblemDetail di un errore applicativo. */
	public ProblemDetail create(ErrorCode code, Object[] args, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatus(code.status());
		problem.setType(code.type());
		problem.setTitle(code.status().getReasonPhrase());
		return enrich(problem, code.name(), request);
	}

	/** Aggiunge le estensioni del progetto a un ProblemDetail (anche prodotto da Spring). */
	public ProblemDetail enrich(ProblemDetail problem, String code, HttpServletRequest request) {
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("code", code);
		problem.setProperty("timestamp", Instant.now());
		String traceId = this.traceIdProvider.currentTraceId();
		if (traceId != null) {
			problem.setProperty("traceId", traceId);
		}
		return problem;
	}

}
