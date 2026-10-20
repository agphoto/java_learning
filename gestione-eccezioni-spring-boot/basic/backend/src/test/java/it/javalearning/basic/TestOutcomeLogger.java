package it.javalearning.basic;

import java.util.Optional;

import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logga l'inizio di ogni test e, alla fine, l'esito con la durata. Si attiva con
 * {@code @ExtendWith(TestOutcomeLogger.class)} sulla classe di test.
 */
public class TestOutcomeLogger implements BeforeTestExecutionCallback, TestWatcher {

	private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
		.create(TestOutcomeLogger.class);

	private static final String START = "start";

	@Override
	public void beforeTestExecution(ExtensionContext context) {
		context.getStore(NAMESPACE).put(START, System.nanoTime());
		logger(context).info("▶ INIZIO  {}", context.getDisplayName());
	}

	@Override
	public void testSuccessful(ExtensionContext context) {
		logger(context).info("✔ SUCCESSO {} ({} ms)", context.getDisplayName(), elapsedMillis(context));
	}

	@Override
	public void testFailed(ExtensionContext context, Throwable cause) {
		logger(context).error("✘ FALLITO {} ({} ms): {}", context.getDisplayName(), elapsedMillis(context),
				cause.getMessage());
	}

	@Override
	public void testAborted(ExtensionContext context, Throwable cause) {
		logger(context).warn("⚠ INTERROTTO {} ({} ms): {}", context.getDisplayName(), elapsedMillis(context),
				cause.getMessage());
	}

	@Override
	public void testDisabled(ExtensionContext context, Optional<String> reason) {
		logger(context).info("○ DISABILITATO {}: {}", context.getDisplayName(), reason.orElse("-"));
	}

	/** Logger intestato alla classe di test, così in console si vede da dove arriva la riga. */
	private static Logger logger(ExtensionContext context) {
		return LoggerFactory.getLogger(context.getRequiredTestClass());
	}

	/** Il TestWatcher è invocato dopo il @AfterEach: la durata include anche il teardown. */
	private static long elapsedMillis(ExtensionContext context) {
		Long start = context.getStore(NAMESPACE).get(START, Long.class);
		return start == null ? 0 : (System.nanoTime() - start) / 1_000_000;
	}

}
