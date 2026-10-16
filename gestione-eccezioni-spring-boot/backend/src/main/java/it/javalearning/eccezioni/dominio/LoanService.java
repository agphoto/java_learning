package it.javalearning.eccezioni.dominio;

import java.time.Instant;

import it.javalearning.eccezioni.errore.BusinessRuleException;
import it.javalearning.eccezioni.errore.ErrorCode;
import it.javalearning.eccezioni.errore.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {

	public static final int MAX_ACTIVE_LOANS = 3;

	private final LoanRepository loans;

	private final UserRepository users;

	private final BookService bookService;

	public LoanService(LoanRepository loans, UserRepository users, BookService bookService) {
		this.loans = loans;
		this.users = users;
		this.bookService = bookService;
	}

	@Transactional
	public Loan borrow(long userId, long bookId) {
		User user = this.users.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
		Book book = this.bookService.findById(bookId);
		if (this.loans.existsByBookIdAndReturnedAtIsNull(bookId)) {
			throw new BusinessRuleException(ErrorCode.BOOK_ALREADY_ON_LOAN, bookId);
		}
		long active = this.loans.countByUserIdAndReturnedAtIsNull(userId);
		if (active >= MAX_ACTIVE_LOANS) {
			throw new BusinessRuleException(ErrorCode.LOAN_LIMIT_EXCEEDED, active, MAX_ACTIVE_LOANS);
		}
		return this.loans.save(new Loan(book, user, Instant.now()));
	}

}
