package server.agents.population;

import org.junit.jupiter.api.Test;
import server.agents.monitoring.AgentAsyncQueueMetrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AgentPopulationSchedulerTest {
    @Test
    void startIsIdempotentAndCloseCancelsSteadyAndFastStartTasks() throws IOException {
        FakeScheduler tasks = new FakeScheduler();
        AgentPopulationMetrics metrics = new AgentPopulationMetrics();
        FakeDispatcher dispatcher = new FakeDispatcher();
        AgentPopulationScheduler scheduler = new AgentPopulationScheduler(
                reconciler(metrics), metrics, 1000, tasks, dispatcher);

        scheduler.start();
        scheduler.start();
        scheduler.scheduleFastStart();

        assertEquals(1, tasks.periodic.size());
        assertEquals(7, tasks.delayed.size());
        assertEquals(7, metrics.snapshot().queuedCallbacks());
        scheduler.close();
        assertTrue(tasks.all().stream().allMatch(FakeFuture::isCancelled));
        assertEquals(0, metrics.snapshot().queuedCallbacks());
        assertTrue(dispatcher.shutdown);
    }

    @Test
    void completedFastStartCallbackLeavesBoundedQueueMetric() throws IOException {
        FakeScheduler tasks = new FakeScheduler();
        AgentPopulationMetrics metrics = new AgentPopulationMetrics();
        FakeDispatcher dispatcher = new FakeDispatcher();
        AgentPopulationScheduler scheduler = new AgentPopulationScheduler(
                reconciler(metrics), metrics, 1000, tasks, dispatcher);
        scheduler.scheduleFastStart();

        tasks.delayed.getFirst().run();

        assertEquals(6, metrics.snapshot().queuedCallbacks());
        scheduler.close();
    }

    @Test
    void timerCallbacksDispatchAndCoalesceBlockingPopulationWork() throws IOException {
        FakeScheduler tasks = new FakeScheduler();
        FakeDispatcher dispatcher = new FakeDispatcher();
        RecordingBackend backend = new RecordingBackend();
        AgentPopulationMetrics metrics = new AgentPopulationMetrics();
        AgentPopulationScheduler scheduler = new AgentPopulationScheduler(
                enabledReconciler(metrics, backend), metrics, 1000, tasks, dispatcher);
        scheduler.start();

        tasks.periodic.getFirst().run();
        tasks.periodic.getFirst().run();

        assertEquals(1, dispatcher.actions.size());
        assertTrue(backend.started.isEmpty(), "timer callback must not execute population I/O");

        dispatcher.runNext();

        assertEquals(List.of(1, 2), backend.started,
                "the coalesced wake-up must run one follow-up bounded sweep in stable order");
        assertTrue(AgentAsyncQueueMetrics.snapshot("population").completed() >= 1);
        assertTrue(AgentAsyncQueueMetrics.snapshot("population").coalesced() >= 1);
        scheduler.close();
    }

    @Test
    void closeWaitsForPopulationLifecycleLaneToStop() throws Exception {
        FakeScheduler tasks = new FakeScheduler();
        BlockingShutdownDispatcher dispatcher = new BlockingShutdownDispatcher();
        AgentPopulationScheduler scheduler = new AgentPopulationScheduler(
                reconciler(new AgentPopulationMetrics()),
                null,
                1000,
                tasks,
                dispatcher);

        CompletableFuture<Void> closing = CompletableFuture.runAsync(scheduler::close);

        assertTrue(dispatcher.shutdownStarted.await(5, TimeUnit.SECONDS));
        assertFalse(closing.isDone(), "close must wait for in-flight population lifecycle work");
        dispatcher.releaseShutdown.countDown();
        closing.get(5, TimeUnit.SECONDS);
    }

    private static AgentPopulationReconciler reconciler(AgentPopulationMetrics metrics) throws IOException {
        AgentPopulationStore store = new AgentPopulationStore() {
            @Override public AgentPopulationSnapshot load() { return AgentPopulationSnapshot.DISABLED; }
            @Override public void save(AgentPopulationSnapshot snapshot) { }
        };
        AgentPopulationSessionService sessions = new AgentPopulationSessionService(new AgentPopulationSessionService.Backend() {
            @Override public boolean isEligibleAgent(int characterId) { return false; }
            @Override public boolean isLive(int characterId) { return false; }
            @Override public boolean spawnSelfDirected(AgentPopulationRecord record) { return false; }
            @Override public boolean stop(int characterId) { return false; }
        });
        return new AgentPopulationReconciler(new AgentPopulationRegistry(store), new AgentPopulationCurve(),
                new AgentPopulationPolicy(), sessions, metrics);
    }

    private static AgentPopulationReconciler enabledReconciler(AgentPopulationMetrics metrics,
                                                                RecordingBackend backend) throws IOException {
        AgentPopulationStore store = new AgentPopulationStore() {
            @Override public AgentPopulationSnapshot load() {
                return new AgentPopulationSnapshot(true, 1.0, List.of(
                        new AgentPopulationRecord(2, "bravo", null),
                        new AgentPopulationRecord(1, "alpha", null),
                        new AgentPopulationRecord(3, "charlie", null)));
            }
            @Override public void save(AgentPopulationSnapshot snapshot) { }
        };
        return new AgentPopulationReconciler(
                new AgentPopulationRegistry(store),
                new AgentPopulationCurve(),
                new AgentPopulationPolicy(1),
                new AgentPopulationSessionService(backend),
                metrics);
    }

    private static final class FakeScheduler implements AgentPopulationScheduler.TaskScheduler {
        final List<FakeFuture> delayed = new ArrayList<>();
        final List<FakeFuture> periodic = new ArrayList<>();
        @Override public ScheduledFuture<?> schedule(Runnable action, long delayMs) {
            FakeFuture future = new FakeFuture(action); delayed.add(future); return future;
        }
        @Override public ScheduledFuture<?> register(Runnable action, long periodMs) {
            FakeFuture future = new FakeFuture(action); periodic.add(future); return future;
        }
        List<FakeFuture> all() { List<FakeFuture> all = new ArrayList<>(delayed); all.addAll(periodic); return all; }
    }

    private static final class FakeDispatcher implements AgentPopulationScheduler.SweepDispatcher {
        final List<Runnable> actions = new ArrayList<>();
        boolean shutdown;
        @Override public void dispatch(Runnable action) { actions.add(action); }
        @Override public boolean shutdownAndAwait(long timeoutMs) { shutdown = true; return true; }
        void runNext() { actions.removeFirst().run(); }
    }

    private static final class BlockingShutdownDispatcher implements AgentPopulationScheduler.SweepDispatcher {
        final CountDownLatch shutdownStarted = new CountDownLatch(1);
        final CountDownLatch releaseShutdown = new CountDownLatch(1);
        @Override public void dispatch(Runnable action) { }
        @Override public boolean shutdownAndAwait(long timeoutMs) {
            shutdownStarted.countDown();
            try {
                return releaseShutdown.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static final class RecordingBackend implements AgentPopulationSessionService.Backend {
        final Set<Integer> live = new HashSet<>();
        final List<Integer> started = new ArrayList<>();
        @Override public boolean isEligibleAgent(int characterId) { return true; }
        @Override public boolean isLive(int characterId) { return live.contains(characterId); }
        @Override public boolean spawnSelfDirected(AgentPopulationRecord record) {
            started.add(record.characterId());
            live.add(record.characterId());
            return true;
        }
        @Override public boolean stop(int characterId) { return live.remove(characterId); }
    }

    private static final class FakeFuture implements ScheduledFuture<Object> {
        private final Runnable action;
        private boolean cancelled;
        FakeFuture(Runnable action) { this.action = action; }
        void run() { action.run(); }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { cancelled = true; return true; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean isDone() { return cancelled; }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, TimeUnit unit) { return null; }
        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed other) { return 0; }
    }
}
