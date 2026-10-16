package it.javalearning.eccezioni.laboratorio.capitolo03;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ScopedController.class)
@Order(2)
public class SecondoAdvice {

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<String> illegalState(IllegalStateException ex) {
		return ResponseEntity.badRequest().body("secondo: IllegalStateException");
	}

}
