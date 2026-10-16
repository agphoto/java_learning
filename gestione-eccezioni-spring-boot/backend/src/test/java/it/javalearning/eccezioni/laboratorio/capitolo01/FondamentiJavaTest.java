package it.javalearning.eccezioni.laboratorio.capitolo01;

import it.javalearning.eccezioni.errore.ApplicationException;
import it.javalearning.eccezioni.errore.BusinessRuleException;
import it.javalearning.eccezioni.errore.DomainException;
import it.javalearning.eccezioni.errore.ErrorCode;
import it.javalearning.eccezioni.errore.IntegrationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Capitolo 01 – Fondamenti Java.
 * Ogni test dimostra un comportamento del linguaggio descritto nel capitolo.
 */
class FondamentiJavaTest {

	/** Una risorsa la cui close() fallisce sempre. */
	static final class RisorsaFragile implements AutoCloseable {
		@Override
		public void close() {
			throw new IllegalStateException("close fallita");
		}
	}

	@Test
	void soppresseInTryWithResources() {
		Throwable t = catchThrowable(() -> {
			try (var r = new RisorsaFragile()) {
				throw new IllegalArgumentException("errore nel corpo");
			}
		});

		// L'eccezione primaria è quella del corpo; quella di close() viene "soppressa".
		assertThat(t).isInstanceOf(IllegalArgumentException.class);
		assertThat(t.getSuppressed()).hasSize(1);
		assertThat(t.getSuppressed()[0]).hasMessage("close fallita");
	}

	@Test
	void catenaDelleCause() {
		var radice = new java.io.IOException("socket chiuso");
		var applicativa = new IntegrationException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, radice);

		assertThat(applicativa.getCause()).isSameAs(radice);
	}

	/** Eccezione "leggera": nessuno stack trace catturato. */
	static final class SenzaStackTrace extends RuntimeException {
		SenzaStackTrace() {
			super("leggera", null, false, false);
		}
	}

	@Test
	void stackTraceNonScrivibile() {
		assertThat(new SenzaStackTrace().getStackTrace()).isEmpty();
		assertThat(new RuntimeException().getStackTrace()).isNotEmpty();
	}

	static String classifica(ApplicationException ex) {
		// Nessun default: il compilatore verifica che i casi coprano la gerarchia sealed.
		return switch (ex) {
			case DomainException d -> "dominio:" + d.code();
			case IntegrationException i -> "integrazione:" + i.code();
		};
	}

	@Test
	void switchSuGerarchiaSealed() {
		assertThat(classifica(new BusinessRuleException(ErrorCode.BOOK_ALREADY_ON_LOAN, 5)))
				.isEqualTo("dominio:BOOK_ALREADY_ON_LOAN");
		assertThat(classifica(new IntegrationException(ErrorCode.EXTERNAL_SERVICE_ERROR)))
				.isEqualTo("integrazione:EXTERNAL_SERVICE_ERROR");
	}

}
