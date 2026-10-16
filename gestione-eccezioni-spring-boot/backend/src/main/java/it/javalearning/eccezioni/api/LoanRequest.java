package it.javalearning.eccezioni.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LoanRequest(@NotNull @Positive Long userId, @NotNull @Positive Long bookId) {
}
