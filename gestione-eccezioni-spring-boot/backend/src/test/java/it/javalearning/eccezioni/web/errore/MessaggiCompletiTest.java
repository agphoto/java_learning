package it.javalearning.eccezioni.web.errore;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

import it.javalearning.eccezioni.errore.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Capitolo 05 – Ogni ErrorCode ha title e detail in entrambe le lingue, e i testi
 * sopravvivono a MessageFormat: con spring.messages.always-use-message-format=true
 * ogni apostrofo nei file va scritto due volte ('').
 */
@SpringBootTest
class MessaggiCompletiTest {

	@Autowired
	MessageSource messageSource;

	@ParameterizedTest
	@ValueSource(strings = { "it", "en" })
	void ogniErrorCodeHaTitleEDetailInEntrambeLeLingue(String lang) {
		Locale locale = Locale.of(lang);
		for (ErrorCode code : ErrorCode.values()) {
			for (String suffix : new String[] { ".title", ".detail" }) {
				String key = code.messageKey() + suffix;
				String formatted = this.messageSource.getMessage(key, new Object[] { "X", "Y" }, null, locale);
				assertThat(formatted).as(key + " [" + lang + "]").isNotBlank().doesNotContain("{").doesNotContain("''");
			}
		}
	}

	private static final Pattern APOSTROFO_SINGOLO = Pattern.compile("(?<!')'(?!')");

	@ParameterizedTest
	@ValueSource(strings = { "messages.properties", "messages_en.properties" })
	void ogniApostrofoERaddoppiato(String file) throws IOException {
		Properties props = new Properties();
		try (var reader = new InputStreamReader(new ClassPathResource(file).getInputStream(), StandardCharsets.UTF_8)) {
			props.load(reader);
		}
		props.forEach((key, value) -> assertThat(APOSTROFO_SINGOLO.matcher((String) value).find())
			.as(file + " → " + key + " = " + value)
			.isFalse());
	}

}
