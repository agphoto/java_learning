package it.javalearning.eccezioni.integrazione;

import it.javalearning.eccezioni.errore.ErrorCode;
import it.javalearning.eccezioni.errore.IntegrationException;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Client di un catalogo esterno (capitolo 08). Traduce ogni guasto della chiamata
 * in una {@link IntegrationException}: il chiamante non vede mai eccezioni HTTP.
 */
@Component
public class CatalogClient {

	private final RestClient restClient;

	private final Environment environment;

	public CatalogClient(RestClient.Builder builder, Environment environment) {
		this.restClient = builder.build();
		this.environment = environment;
	}

	public CatalogInfo fetchInfo(String isbn, String simulate) {
		// Letta a ogni chiamata: nei test la porta del server è nota solo dopo l'avvio.
		String baseUrl = this.environment.getRequiredProperty("catalog.base-url");
		try {
			return this.restClient.get()
				.uri(baseUrl + "/simulated-catalog/{isbn}?simulate={simulate}", isbn, simulate)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, response) -> {
					throw new IntegrationException(codeFor(response.getStatusCode()), response.getStatusCode().value());
				})
				.body(CatalogInfo.class);
		}
		catch (ResourceAccessException ex) {
			// Connessione rifiutata, timeout, DNS: il servizio non è raggiungibile.
			throw new IntegrationException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, ex);
		}
	}

	private static ErrorCode codeFor(HttpStatusCode status) {
		return (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) ? ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE
				: ErrorCode.EXTERNAL_SERVICE_ERROR;
	}

}
