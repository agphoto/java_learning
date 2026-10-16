package it.javalearning.eccezioni.laboratorio.capitolo08;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

	private final RegistroErroriAsincroni registro;

	public AsyncConfig(RegistroErroriAsincroni registro) {
		this.registro = registro;
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return this.registro;
	}

}
