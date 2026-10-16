package it.javalearning.eccezioni.web.errore;

/**
 * Un errore di validazione su un singolo campo o parametro (capitolo 06).
 *
 * @param field nome del campo (o del parametro) così come lo vede il client
 * @param code nome del vincolo violato, es. {@code NotBlank}: stabile, non tradotto
 * @param message testo localizzato per l'utente
 */
public record FieldErrorItem(String field, String code, String message) {
}
