package it.javalearning.eccezioni.api;

import java.net.URI;

import it.javalearning.eccezioni.dominio.Loan;
import it.javalearning.eccezioni.dominio.LoanService;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

	private final LoanService loanService;

	public LoanController(LoanService loanService) {
		this.loanService = loanService;
	}

	@PostMapping
	public ResponseEntity<LoanResponse> borrow(@Valid @RequestBody LoanRequest request) {
		Loan loan = this.loanService.borrow(request.userId(), request.bookId());
		return ResponseEntity.created(URI.create("/api/loans/" + loan.getId())).body(LoanResponse.from(loan));
	}

}
