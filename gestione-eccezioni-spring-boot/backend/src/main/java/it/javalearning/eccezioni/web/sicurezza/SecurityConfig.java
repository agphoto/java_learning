package it.javalearning.eccezioni.web.sicurezza;

import it.javalearning.eccezioni.errore.ErrorCode;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.RequestRejectedHandler;

/**
 * Capitolo 08 – Solo /api/admin/** è protetto (ruolo ADMIN), con HTTP Basic.
 * Gli errori di autenticazione e autorizzazione producono lo stesso ProblemDetail
 * dell'API, tramite un entry point e un access denied handler dedicati.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailAuthenticationEntryPoint entryPoint,
			ProblemDetailAccessDeniedHandler accessDeniedHandler) {
		return http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.anyRequest().permitAll())
			// Basic con credenziali errate → stesso entry point (niente WWW-Authenticate)
			.httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(entryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable())
			.build();
	}

	/**
	 * URL rifiutati dallo StrictHttpFirewall ("//", ";jsessionid", "%2e%2e", metodi
	 * sconosciuti): il rifiuto avviene in FilterChainProxy, prima di ogni altro filtro.
	 * Senza questo handler la risposta arriverebbe da /error, fuori contratto.
	 */
	@Bean
	RequestRejectedHandler requestRejectedHandler(ProblemDetailWriter writer) {
		return (request, response, ex) -> writer.write(ErrorCode.MALFORMED_REQUEST, ex, request, response);
	}

	/** Due utenti di prova; {noop} = password in chiaro, accettabile solo in un laboratorio. */
	@Bean
	UserDetailsService users() {
		return new InMemoryUserDetailsManager(
				User.withUsername("utente").password("{noop}utente").roles("USER").build(),
				User.withUsername("admin").password("{noop}admin").roles("USER", "ADMIN").build());
	}

}
