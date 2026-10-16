package it.javalearning.eccezioni.laboratorio.capitolo08;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * AsyncUncaughtExceptionHandler che registra gli errori dei metodi {@code @Async void}
 * e li rende osservabili (dai test, in questo laboratorio).
 */
@Component
public class RegistroErroriAsincroni implements AsyncUncaughtExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(RegistroErroriAsincroni.class);

	public record Errore(Throwable eccezione, String metodo, String thread) {
	}

	private final BlockingQueue<Errore> errori = new LinkedBlockingQueue<>();

	@Override
	public void handleUncaughtException(Throwable ex, Method method, Object... params) {
		log.error("Errore in un metodo @Async: {}", method.getName(), ex);
		this.errori.add(new Errore(ex, method.getName(), Thread.currentThread().getName()));
	}

	@Nullable
	public Errore attendi(Duration timeout) throws InterruptedException {
		return this.errori.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
	}

}
