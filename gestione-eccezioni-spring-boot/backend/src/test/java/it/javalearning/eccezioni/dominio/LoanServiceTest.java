package it.javalearning.eccezioni.dominio;

import it.javalearning.eccezioni.errore.BusinessRuleException;
import it.javalearning.eccezioni.errore.ErrorCode;
import it.javalearning.eccezioni.errore.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DataJpaTest
@Import({ LoanService.class, BookService.class })
class LoanServiceTest {

	@Autowired
	LoanService loanService;

	@Test
	void prestitoRiuscito() {
		Loan loan = loanService.borrow(1, 1);

		assertThat(loan.getId()).isNotNull();
		assertThat(loan.getReturnedAt()).isNull();
	}

	@Test
	void libroInesistente() {
		var ex = catchThrowableOfType(ResourceNotFoundException.class, () -> loanService.borrow(1, 999));

		assertThat(ex.code()).isEqualTo(ErrorCode.BOOK_NOT_FOUND);
	}

	@Test
	void utenteInesistente() {
		var ex = catchThrowableOfType(ResourceNotFoundException.class, () -> loanService.borrow(999, 1));

		assertThat(ex.code()).isEqualTo(ErrorCode.USER_NOT_FOUND);
	}

	@Test
	void libroGiaInPrestito() {
		var ex = catchThrowableOfType(BusinessRuleException.class, () -> loanService.borrow(1, 5));

		assertThat(ex.code()).isEqualTo(ErrorCode.BOOK_ALREADY_ON_LOAN);
	}

	@Test
	void limiteSuperato() {
		var ex = catchThrowableOfType(BusinessRuleException.class, () -> loanService.borrow(2, 1));

		assertThat(ex.code()).isEqualTo(ErrorCode.LOAN_LIMIT_EXCEEDED);
		assertThat(ex.args()).containsExactly(3L, 3);
	}

}
