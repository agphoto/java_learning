package it.javalearning.eccezioni.laboratorio.capitolo08;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AsyncController.class)
public class AsyncAdvice {

	/** Arriva la causa: Spring "scarta" la CompletionException che la avvolge. */
	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<String> handle(IllegalStateException ex) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("gestito dall'advice: " + ex.getMessage());
	}

}
