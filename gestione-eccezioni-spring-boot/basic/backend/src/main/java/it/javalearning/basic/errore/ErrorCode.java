package it.javalearning.basic.errore;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;

/**
 * Catalogo dei tipi di problema esposti dall'API. Ogni costante lega in un solo punto
 * il code (per la macchina, mai tradotto), lo stato HTTP e il title (per la persona).
 * Il type della RFC 9457 è derivato dal nome: BOOK_NOT_FOUND → .../errors/book-not-found.
 */
public enum ErrorCode {

	BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "Libro inesistente"),
	DUPLICATE_ISBN(HttpStatus.CONFLICT, "ISBN già presente"),
	DATA_CONFLICT(HttpStatus.CONFLICT, "Dati in conflitto"),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dati non validi"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno");

	/** Base dei type: identificatori stabili, non indirizzi da visitare (dominio .local). */
	private static final String TYPE_BASE = "https://java-learning.local/errors/";

	private final HttpStatus status;

	private final String title;

	private final URI type;

	ErrorCode(HttpStatus status, String title) {
		this.status = status;
		this.title = title;
		this.type = URI.create(TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-'));
	}

	public HttpStatus status() {
		return this.status;
	}

	public String title() {
		return this.title;
	}

	public URI type() {
		return this.type;
	}

}
