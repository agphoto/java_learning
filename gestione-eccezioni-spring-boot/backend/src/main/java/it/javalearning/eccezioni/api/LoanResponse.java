package it.javalearning.eccezioni.api;

import java.time.Instant;

import it.javalearning.eccezioni.dominio.Loan;

public record LoanResponse(Long id, Long bookId, Long userId, Instant loanedAt) {

	static LoanResponse from(Loan loan) {
		return new LoanResponse(loan.getId(), loan.getBook().getId(), loan.getUser().getId(), loan.getLoanedAt());
	}

}
