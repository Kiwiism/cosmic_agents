package server.agents.population;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentPopulationReconcilerTest {
    @Test
    void disabledPopulationLeavesExistingSessionsUntouched() throws IOException {
        Fixture fixture = new Fixture(false, 1.0, 10);
        fixture.backend.live.add(1);

        AgentPopulationReconciler.Result result = fixture.reconciler.reconcile();

        assertFalse(result.enabled());
        assertEquals(1, result.liveAfter());
        assertTrue(fixture.backend.stopped.isEmpty());
    }

    @Test
    void startsInStableOrderAndBoundsWorkPerSweep() throws IOException {
        Fixture fixture = new Fixture(true, 1.0, 2);

        AgentPopulationReconciler.Result result = fixture.reconciler.reconcile();

        assertEquals(3, result.target());
        assertEquals(2, result.started());
        assertEquals(List.of(1, 2), fixture.backend.started);
    }

    @Test
    void oneAgentFailureDoesNotPreventLaterAgent() throws IOException {
        Fixture fixture = new Fixture(true, 1.0, 3);
        fixture.backend.failStart.add(1);

        AgentPopulationReconciler.Result result = fixture.reconciler.reconcile();

        assertEquals(List.of(2, 3), fixture.backend.started);
        assertEquals(1, result.failed());
        assertEquals(1, fixture.metrics.snapshot().failures());
    }

    @Test
    void excessSessionsStopInReverseStableOrder() throws IOException {
        Fixture fixture = new Fixture(true, 1.0 / 3.0, 5);
        fixture.backend.live.addAll(List.of(1, 2, 3));

        AgentPopulationReconciler.Result result = fixture.reconciler.reconcile();

        assertEquals(1, result.target());
        assertEquals(List.of(3, 2), fixture.backend.stopped);
    }

    private static final class Fixture {
        final FakeBackend backend = new FakeBackend();
        final AgentPopulationMetrics metrics = new AgentPopulationMetrics();
        final AgentPopulationReconciler reconciler;

        Fixture(boolean enabled, double multiplier, int budget) throws IOException {
            AgentPopulationStore store = new MemoryStore(new AgentPopulationSnapshot(enabled, multiplier, List.of(
                    new AgentPopulationRecord(3, "charlie", null),
                    new AgentPopulationRecord(1, "alpha", null),
                    new AgentPopulationRecord(2, "bravo", null))));
            AgentPopulationRegistry registry = new AgentPopulationRegistry(store);
            reconciler = new AgentPopulationReconciler(registry, new AgentPopulationCurve(),
                    new AgentPopulationPolicy(budget), new AgentPopulationSessionService(backend), metrics);
        }
    }

    private static final class FakeBackend implements AgentPopulationSessionService.Backend {
        final Set<Integer> live = new HashSet<>();
        final Set<Integer> failStart = new HashSet<>();
        final List<Integer> started = new ArrayList<>();
        final List<Integer> stopped = new ArrayList<>();

        @Override public boolean isEligibleAgent(int characterId) { return true; }
        @Override public boolean isLive(int characterId) { return live.contains(characterId); }
        @Override public boolean spawnSelfDirected(AgentPopulationRecord record) throws Exception {
            if (failStart.contains(record.characterId())) throw new Exception("failed");
            started.add(record.characterId()); live.add(record.characterId()); return true;
        }
        @Override public boolean stop(int characterId) {
            stopped.add(characterId); live.remove(characterId); return true;
        }
    }

    private static final class MemoryStore implements AgentPopulationStore {
        private AgentPopulationSnapshot snapshot;
        private MemoryStore(AgentPopulationSnapshot snapshot) { this.snapshot = snapshot; }
        @Override public AgentPopulationSnapshot load() { return snapshot; }
        @Override public void save(AgentPopulationSnapshot snapshot) { this.snapshot = snapshot; }
    }
}
