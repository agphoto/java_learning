package it.javalearning.eccezioni.web.errore;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Fornisce l'identificativo della traccia corrente (capitolo 09). È lo stesso valore
 * che Spring Boot inserisce nei log, quindi collega la risposta alle righe di log.
 */
@Component
public class TraceIdProvider {

	private final Tracer tracer;

	public TraceIdProvider(Tracer tracer) {
		this.tracer = tracer;
	}

	@Nullable
	public String currentTraceId() {
		Span span = this.tracer.currentSpan();
		return (span != null) ? span.context().traceId() : null;
	}

}
