package it.javalearning.eccezioni.web.errore;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/** Fornisce l'identificativo della traccia corrente, se esiste (capitolo 09). */
@Component
public class TraceIdProvider {

	@Nullable
	public String currentTraceId() {
		return null;
	}

}
