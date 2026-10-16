package it.javalearning.eccezioni.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BookRequest(
		@NotBlank @Pattern(regexp = "\\d{13}") String isbn,
		@NotBlank @Size(max = 200) String title,
		@NotBlank @Size(max = 200) String author) {
}
