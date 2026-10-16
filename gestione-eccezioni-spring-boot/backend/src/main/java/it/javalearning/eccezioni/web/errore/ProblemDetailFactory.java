package it.javalearning.eccezioni.web.errore;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

import it.javalearning.eccezioni.errore.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Unico punto in cui nasce o viene completato un {@link ProblemDetail}: lo usano
 * gli advice MVC e i gestori di Spring Security, così il formato resta identico
 * ovunque l'errore abbia origine.
 * <p>
 * {@code title} e {@code detail} vengono dal {@link MessageSource}
 * (chiavi {@code error.<code>.title|detail}) nella lingua della richiesta.
 */
@Component
public class ProblemDetailFactory {

	private final MessageSource messageSource;

	private final TraceIdProvider traceIdProvider;

	public ProblemDetailFactory(MessageSource messageSource, TraceIdProvider traceIdProvider) {
		this.messageSource = messageSource;
		this.traceIdProvider = traceIdProvider;
	}

	/** Costruisce il ProblemDetail di un errore applicativo. */
	public ProblemDetail create(ErrorCode code, Object[] args, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatus(code.status());
		applyCatalog(problem, code, args);
		return enrich(problem, code.name(), request);
	}

	/**
	 * Completa un ProblemDetail prodotto da Spring per un'eccezione standard. Se
	 * l'eccezione ha un codice di catalogo, title/detail/type vengono dal catalogo;
	 * altrimenti restano quelli risolti da Spring e il code è {@code HTTP_<status>}.
	 */
	public ProblemDetail fromStandard(ProblemDetail problem, Exception ex, HttpStatusCode status,
			HttpServletRequest request) {
		ErrorCode code = StandardExceptionCodes.codeFor(ex);
		if (code == null) {
			return enrich(problem, "HTTP_" + status.value(), request);
		}
		applyCatalog(problem, code, new Object[0]);
		return enrich(problem, code.name(), request);
	}

	/** Aggiunge le estensioni del progetto: instance, code, timestamp, traceId. */
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

	private void applyCatalog(ProblemDetail problem, ErrorCode code, Object[] args) {
		Locale locale = LocaleContextHolder.getLocale();
		problem.setType(code.type());
		problem.setTitle(this.messageSource.getMessage(code.messageKey() + ".title", null, locale));
		problem.setDetail(this.messageSource.getMessage(code.messageKey() + ".detail", args, locale));
	}

}
