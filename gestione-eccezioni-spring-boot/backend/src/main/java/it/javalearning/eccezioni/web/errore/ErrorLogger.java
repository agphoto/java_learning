package it.javalearning.eccezioni.web.errore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Politica di log degli errori (capitolo 09), applicata in un solo punto:
 * <ul>
 * <li>5xx: ERROR con stack trace — è un problema del server, va indagato;</li>
 * <li>4xx: WARN su una riga, senza stack trace — è un errore del client, lo stack
 * trace non aggiunge nulla e riempirebbe i log.</li>
 * </ul>
 * Il traceId non va scritto nel messaggio: lo aggiunge il pattern di log di Spring Boot.
 */
@Component
public class ErrorLogger {

	private static final Logger log = LoggerFactory.getLogger(ErrorLogger.class);

	public void log(ProblemDetail problem, Throwable ex) {
		Object code = (problem.getProperties() != null) ? problem.getProperties().get("code") : null;
		if (problem.getStatus() >= 500) {
			log.error("{} {} code={}", problem.getStatus(), problem.getInstance(), code, ex);
		}
		else {
			log.warn("{} {} code={} cause={}", problem.getStatus(), problem.getInstance(), code, ex.toString());
		}
	}

}
