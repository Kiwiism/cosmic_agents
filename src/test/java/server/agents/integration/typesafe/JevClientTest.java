package server.agents.integration.typesafe;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JevClientTest {
    private static final JevRequest REQUEST = JevRequest.builder("hello")
            .noul("greeting", "Is `state` a greeting?")
            .build();

    @Test
    void unconfiguredClientRefusesLocallyWithoutTouchingTransport() {
        AtomicInteger calls = new AtomicInteger();
        JevClient client = new JevClient(TypeSafeSettings.disabled(), (request, settings) -> {
            calls.incrementAndGet();
            return response();
        });

        assertFalse(client.configured());
        assertFalse(client.available());
        assertTrue(client.ask(REQUEST).isEmpty());
        assertEquals(0, calls.get());
        assertTrue(client.diagnostics().contains("unconfigured"));
    }

    @Test
    void successfulAskRecordsUsage() {
        JevClient client = new JevClient(configured(), (request, settings) -> response());

        Optional<JevResponse> answer = client.ask(REQUEST);

        assertTrue(answer.isPresent());
        assertEquals(0.9d, answer.get().yesProbability("greeting"), 1e-9);
        assertEquals(1L, client.meter().successes());
        assertEquals(42L, client.meter().inputTokens());
    }

    @Test
    void consecutiveFailuresOpenTheBreakerAndSuccessResetsIt() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger failuresToInject = new AtomicInteger(2);
        JevCircuitBreaker breaker = new JevCircuitBreaker(2, 60_000L);
        JevClient client = new JevClient(configured(), (request, settings) -> {
            calls.incrementAndGet();
            if (failuresToInject.getAndDecrement() > 0) {
                throw new JevTransportException("boom", 529, true);
            }
            return response();
        }, breaker, new JevUsageMeter());

        assertTrue(client.ask(REQUEST).isEmpty());
        assertTrue(client.ask(REQUEST).isEmpty());
        assertFalse(client.available(), "two failures reach the threshold and open the breaker");
        assertTrue(client.ask(REQUEST).isEmpty(), "refused locally while open");
        assertEquals(2, calls.get(), "no network call while the breaker is open");
        assertEquals(2L, client.meter().failures());

        breaker.recordSuccess();
        assertTrue(client.available());
        assertTrue(client.ask(REQUEST).isPresent());
        assertEquals(3, calls.get());
    }

    @Test
    void askAsyncCompletesWithTheSameResultOffThread() throws Exception {
        JevClient client = new JevClient(configured(), (request, settings) -> response());

        Optional<JevResponse> answer = client.askAsync(REQUEST).get();

        assertTrue(answer.isPresent());
    }

    private static TypeSafeSettings configured() {
        return new TypeSafeSettings("key", TypeSafeSettings.DEFAULT_ENDPOINT, TypeSafeSettings.DEFAULT_MODEL, 1_000);
    }

    private static JevResponse response() {
        return new JevResponse("jev-1.13.0",
                Map.of("greeting", new JevAnswer("noul", null, Map.of(), 0.9d, null, Map.of(), null)),
                42L, 0L, 7L);
    }
}
