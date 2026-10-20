package it.javalearning.basic.libro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo di creazione e modifica. I vincoli sono attivi: senza un gestore dedicato, una
 * violazione produce la risposta di default di Spring Boot (400, corpo generico).
 */
public record BookRequest(
		@NotBlank @Pattern(regexp = "\\d{13}") String isbn,
		@NotBlank @Size(max = 200) String title,
		@NotBlank @Size(max = 100) String author) {
}
