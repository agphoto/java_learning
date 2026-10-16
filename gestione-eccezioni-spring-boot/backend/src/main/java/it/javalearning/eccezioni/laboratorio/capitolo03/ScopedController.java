package it.javalearning.eccezioni.laboratorio.capitolo03;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/laboratorio/capitolo03")
public class ScopedController {

	@GetMapping("/argument")
	public String argument() {
		throw new IllegalArgumentException("argomento");
	}

	@GetMapping("/state")
	public String state() {
		throw new IllegalStateException("stato");
	}

}
