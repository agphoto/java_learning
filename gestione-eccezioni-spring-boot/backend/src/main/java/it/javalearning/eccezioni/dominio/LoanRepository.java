package it.javalearning.eccezioni.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

	boolean existsByBookIdAndReturnedAtIsNull(Long bookId);

	long countByUserIdAndReturnedAtIsNull(Long userId);

}
