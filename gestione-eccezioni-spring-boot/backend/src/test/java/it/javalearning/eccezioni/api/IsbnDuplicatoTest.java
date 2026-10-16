package it.javalearning.eccezioni.api;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitolo 07 – La violazione del vincolo di unicità sull'ISBN viene tradotta da
 * Spring in DataIntegrityViolationException e dal gestore centrale in un 409,
 * senza esporre nulla del database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IsbnDuplicatoTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void isbnDuplicatoDiventaConflitto() {
		var result = this.mvc.post()
			.uri("/api/books")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"isbn\":\"9788804668237\",\"title\":\"Copia\",\"author\":\"Qualcuno\"}")
			.exchange();

		assertThat(result).hasStatus(409);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("DUPLICATE_ISBN");
		assertThat(result).bodyText()
			.doesNotContainIgnoringCase("constraint")
			.doesNotContainIgnoringCase("SQL")
			.doesNotContainIgnoringCase("uk_book_isbn")
			.doesNotContainIgnoringCase("PUBLIC.BOOK");
	}

}
