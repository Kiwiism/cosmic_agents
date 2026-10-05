package server.agents.integration.typesafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.monitoring.ThrottledLogger;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-wide facade over the TypeSafe System One API.
 *
 * <p>{@link #ask(JevRequest)} never throws and never blocks longer than the configured timeout
 * (plus one retry); every failure path yields {@link Optional#empty()} so callers fall back to
 * their existing deterministic behaviour. Callers that run on game threads must use
 * {@link #askAsync(JevRequest)} or the Agent async gateway instead of {@code ask}.
 */
public final class JevClient {
    private static final Logger log = LoggerFactory.getLogger(JevClient.class);
    private static final int ASYNC_THREADS = config.AgentTuning.intValue(
            "server.agents.integration.typesafe.JevClient.ASYNC_THREADS");
    private static final int ASYNC_QUEUE_CAPACITY = config.AgentTuning.intValue(
            "server.agents.integration.typesafe.JevClient.ASYNC_QUEUE_CAPACITY");
    private static volatile JevClient runtime;

    private final TypeSafeSettings settings;
    private final JevTransport transport;
    private final JevCircuitBreaker breaker;
    private final JevUsageMeter meter;
    private volatile ExecutorService asyncExecutor;

    public JevClient(TypeSafeSettings settings, JevTransport transport) {
        this(settings, transport, new JevCircuitBreaker(), new JevUsageMeter());
    }

    public JevClient(TypeSafeSettings settings, JevTransport transport,
                     JevCircuitBreaker breaker, JevUsageMeter meter) {
        this.settings = settings;
        this.transport = transport;
        this.breaker = breaker;
        this.meter = meter;
    }

    /** The shared client, configured from the environment on first use. */
    public static JevClient runtime() {
        JevClient current = runtime;
        if (current == null) {
            synchronized (JevClient.class) {
                current = runtime;
                if (current == null) {
                    current = new JevClient(TypeSafeSettings.runtime(), new JevHttpTransport());
                    runtime = current;
                    log.info("TypeSafe System One client initialised: {}", current.settings);
                }
            }
        }
        return current;
    }

    /** Replaces the shared client; intended for tests and for operator-driven reconfiguration. */
    public static void installRuntime(JevClient client) {
        synchronized (JevClient.class) {
            runtime = client;
        }
    }

    public TypeSafeSettings settings() {
        return settings;
    }

    public JevUsageMeter meter() {
        return meter;
    }

    public boolean configured() {
        return settings.configured();
    }

    /** True when a request made now would actually reach the API. */
    public boolean available() {
        return settings.configured() && !breaker.isOpen(System.currentTimeMillis());
    }

    /** Blocking call. Empty on any failure, when unconfigured, or while the breaker is open. */
    public Optional<JevResponse> ask(JevRequest request) {
        if (!settings.configured()) {
            meter.recordRefusedUnconfigured();
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        if (breaker.isOpen(now)) {
            meter.recordRefusedCircuitOpen();
            return Optional.empty();
        }
        try {
            JevResponse response = transport.send(request, settings);
            breaker.recordSuccess();
            meter.recordSuccess(request.kind(), response);
            return Optional.of(response);
        } catch (JevTransportException failure) {
            breaker.recordFailure(System.currentTimeMillis());
            meter.recordFailure(request.kind());
            ThrottledLogger.warn("typesafe:" + failure.statusCode(), log,
                    "System One request failed (status {}, retryable {})", failure,
                    failure.statusCode(), failure.retryable());
            return Optional.empty();
        } catch (RuntimeException failure) {
            breaker.recordFailure(System.currentTimeMillis());
            meter.recordFailure(request.kind());
            ThrottledLogger.warn("typesafe:runtime", log, "System One request failed unexpectedly", failure);
            return Optional.empty();
        }
    }

    /**
     * Non-blocking call on a small dedicated pool for callers outside the Agent runtime (name
     * screening, report triage). Agent code should prefer the Agent async gateway so its
     * per-session bookkeeping and metrics apply.
     */
    public CompletableFuture<Optional<JevResponse>> askAsync(JevRequest request) {
        if (!available()) {
            return CompletableFuture.completedFuture(ask(request));
        }
        try {
            return CompletableFuture.supplyAsync(() -> ask(request), executor());
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    public String diagnostics() {
        return "typesafe " + (settings.configured() ? "configured" : "unconfigured")
                + " model=" + settings.model()
                + " breakerOpen=" + breaker.isOpen(System.currentTimeMillis())
                + ' ' + meter.diagnostics();
    }

    private ExecutorService executor() {
        ExecutorService current = asyncExecutor;
        if (current == null) {
            synchronized (this) {
                current = asyncExecutor;
                if (current == null) {
                    AtomicInteger counter = new AtomicInteger();
                    current = new ThreadPoolExecutor(
                            ASYNC_THREADS, ASYNC_THREADS, 30L, TimeUnit.SECONDS,
                            new ArrayBlockingQueue<>(ASYNC_QUEUE_CAPACITY),
                            runnable -> {
                                Thread thread = new Thread(runnable, "typesafe-jev-" + counter.incrementAndGet());
                                thread.setDaemon(true);
                                return thread;
                            });
                    asyncExecutor = current;
                }
            }
        }
        return current;
    }
}
