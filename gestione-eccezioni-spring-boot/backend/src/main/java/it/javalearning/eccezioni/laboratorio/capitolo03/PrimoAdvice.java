package it.javalearning.eccezioni.laboratorio.capitolo03;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = { ScopedController.class, LocalHandlerController.class })
@Order(1)
public class PrimoAdvice {

	@ExceptionHandler(RuntimeException.class)
	ResponseEntity<String> runtime(RuntimeException ex) {
		return ResponseEntity.badRequest().body("primo: RuntimeException");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<String> illegalArgument(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body("primo: IllegalArgumentException");
	}

}
