package it.javalearning.eccezioni.laboratorio.capitolo03;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Un @ExceptionHandler dichiarato nel controller ha la precedenza su ogni advice. */
@RestController
public class LocalHandlerController {

	@GetMapping("/laboratorio/capitolo03/local")
	public String local() {
		throw new IllegalArgumentException("locale");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<String> handleLocally(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body("gestito localmente");
	}

}
