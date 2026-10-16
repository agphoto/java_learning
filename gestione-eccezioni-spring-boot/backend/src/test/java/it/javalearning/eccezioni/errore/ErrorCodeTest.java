package it.javalearning.eccezioni.errore;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

	@Test
	void ogniCodiceConosceStatoChiaveETipo() {
		ErrorCode code = ErrorCode.LOAN_LIMIT_EXCEEDED;

		assertThat(code.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(code.messageKey()).isEqualTo("error.loan-limit-exceeded");
		assertThat(code.type()).hasToString("https://java-learning.local/errors/loan-limit-exceeded");
	}

	@Test
	void ilMessaggioTecnicoContieneCodiceEArgomenti() {
		var ex = new BusinessRuleException(ErrorCode.LOAN_LIMIT_EXCEEDED, 3, 3);

		assertThat(ex.code()).isEqualTo(ErrorCode.LOAN_LIMIT_EXCEEDED);
		assertThat(ex.args()).containsExactly(3, 3);
		assertThat(ex.getMessage()).isEqualTo("LOAN_LIMIT_EXCEEDED args=[3, 3]");
	}

}
