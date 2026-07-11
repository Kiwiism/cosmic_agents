package server.agents.population;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AgentPopulationSchedulerTest {
    @Test
    void startIsIdempotentAndCloseCancelsSteadyAndFastStartTasks() throws IOException {
        FakeScheduler tasks = new FakeScheduler();
        AgentPopulationMetrics metrics = new AgentPopulationMetrics();
        AgentPopulationScheduler scheduler = new AgentPopulationScheduler(
                reconciler(metrics), metrics, 1000, tasks);

        scheduler.start();
        scheduler.start();
        scheduler.scheduleFastStart();

        assertEquals(1, tasks.periodic.size());
        assertEquals(7, tasks.delayed.size());
        assertEquals(7, metrics.snapshot().queuedCallbacks());
        scheduler.close();
        assertTrue(tasks.all().stream().allMatch(FakeFuture::isCancelled));
        assertEquals(0, metrics.snapshot().queuedCallbacks());
    }

    @Test
    void completedFastStartCallbackLeavesBoundedQueueMetric() throws IOException {
        FakeScheduler tasks = new FakeScheduler();
        AgentPopulationMetrics metrics = new AgentPopulationMetrics();
        AgentPopulationScheduler scheduler = new AgentPopulationScheduler(
                reconciler(metrics), metrics, 1000, tasks);
        scheduler.scheduleFastStart();

        tasks.delayed.getFirst().run();

        assertEquals(6, metrics.snapshot().queuedCallbacks());
        scheduler.close();
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
