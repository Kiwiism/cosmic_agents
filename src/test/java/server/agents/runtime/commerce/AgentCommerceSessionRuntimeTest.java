package server.agents.runtime.commerce;

import org.junit.jupiter.api.Test;
import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.session.EconomySessionPort;
import server.agents.runtime.activity.session.AgentActivityPhase;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCommerceSessionRuntimeTest {
    @Test
    void persistsAdmissionAndCompletesAfterCommerceRequestsRelease() {
        FakeSessions sessions = new FakeSessions();
        MemoryStore store = new MemoryStore();
        AgentCommerceSessionRuntime runtime = new AgentCommerceSessionRuntime(
                sessions, store, request("visit-1"));

        assertEquals(server.agents.runtime.activity.session.AgentActivityAdmissionResult.Status.ACCEPTED,
                runtime.requestEntry(1_000L).status());
        assertEquals(AgentActivityPhase.ACTIVE, store.value.phase());

        sessions.directive = EconomySessionPort.Directive.release(
                Instant.ofEpochMilli(2_000L), "market work complete");
        assertTrue(runtime.tick(2_000L));

        assertEquals(AgentActivityPhase.COMPLETED, store.value.phase());
        var terminal = runtime.terminalOutcome(2_000L);
        assertNotNull(terminal);
        assertEquals(terminal, runtime.terminalOutcome(9_000L));
        runtime.acknowledgeTerminal();
        assertNull(store.value);
    }

    @Test
    void restoresAnOwningVisitWithoutReplayingAdmission() {
        FakeSessions sessions = new FakeSessions();
        MemoryStore store = new MemoryStore();
        AgentCommerceSessionRuntime first = new AgentCommerceSessionRuntime(
                sessions, store, request("visit-restore"));
        first.requestEntry(1_000L);

        AgentCommerceSessionRuntime restored = new AgentCommerceSessionRuntime(
                sessions, store, request("visit-restore"));

        assertEquals(server.agents.runtime.activity.session.AgentActivityAdmissionResult.Status.ACCEPTED,
                restored.requestEntry(1_500L).status());
        assertEquals(1, sessions.entries);
        assertEquals(AgentActivityPhase.ACTIVE, restored.snapshot(1_500L).phase());
    }

    @Test
    void keepsDrainingUntilProtectedCommerceStateReleases() {
        FakeSessions sessions = new FakeSessions();
        sessions.deferRelease = true;
        MemoryStore store = new MemoryStore();
        AgentCommerceSessionRuntime runtime = new AgentCommerceSessionRuntime(
                sessions, store, request("visit-drain"));
        runtime.requestEntry(1_000L);

        var exit = runtime.requestGracefulExit("handoff", 2_000L, 5_000L);
        assertEquals(server.agents.runtime.activity.session.AgentActivityExitResult.Status.DEFERRED,
                exit.status());
        assertEquals(AgentActivityPhase.DRAINING, store.value.phase());

        sessions.deferRelease = false;
        assertTrue(runtime.tick(3_000L));
        assertEquals(AgentActivityPhase.COMPLETED, store.value.phase());
    }

    private static AgentCommerceVisitRequest request(String requestId) {
        return new AgentCommerceVisitRequest(requestId, "world-director",
                new CommerceParticipant("agent-1", "warrior", .5, .5, .5, .5,
                        .5, .5, 24, .5, .5),
                AgentCommerceVisitRequest.Purpose.PERIODIC_MARKET_VISIT,
                30_000L, 5_000L, Map.of());
    }

    private static final class MemoryStore implements AgentCommerceSessionStore {
        private AgentCommerceSessionCheckpoint value;

        @Override public void save(AgentCommerceSessionCheckpoint checkpoint) { value = checkpoint; }
        @Override public Optional<AgentCommerceSessionCheckpoint> load(String agentId) {
            return Optional.ofNullable(value);
        }
        @Override public void delete(String agentId) { value = null; }
    }

    private static final class FakeSessions implements EconomySessionPort {
        private final UUID sessionId = UUID.randomUUID();
        private Directive directive = Directive.revisit(
                Instant.ofEpochMilli(2_000L), false, "continue");
        private boolean deferRelease;
        private int entries;

        @Override
        public EntryResult requestEntry(
                CommerceParticipant profile, EntryRequest request, Instant logicalAt) {
            entries++;
            return EntryResult.accepted(sessionId, logicalAt.plusSeconds(30), "accepted");
        }

        @Override
        public Directive performMarketCycle(
                UUID sessionId, CommerceParticipant profile, Instant logicalAt) {
            return directive;
        }

        @Override
        public ReleaseResult release(UUID sessionId, CommerceParticipant profile,
                                     Instant logicalAt, String reason) {
            return deferRelease
                    ? ReleaseResult.deferred("shop still draining", logicalAt.plusSeconds(1))
                    : ReleaseResult.released(reason);
        }
    }
}
