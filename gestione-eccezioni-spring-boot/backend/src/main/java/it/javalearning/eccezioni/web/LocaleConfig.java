package it.javalearning.eccezioni.web;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * La lingua della risposta viene da Accept-Language. Sono supportate solo it ed en;
 * qualunque altra lingua (o l'assenza dell'header) ricade sull'italiano.
 */
@Configuration
public class LocaleConfig {

	public static final Locale ITALIANO = Locale.ITALIAN;

	@Bean
	LocaleResolver localeResolver() {
		AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
		resolver.setSupportedLocales(List.of(ITALIANO, Locale.ENGLISH));
		resolver.setDefaultLocale(ITALIANO);
		return resolver;
	}

}
