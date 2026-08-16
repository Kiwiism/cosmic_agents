package server.agents.runtime.activity.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPersistentActivityHandoffCoordinatorTest {
    @TempDir
    Path directory;

    @Test
    void inFlightHandoffCanBeRestoredByANewCoordinator() {
        AgentFileActivityHandoffStore store = new AgentFileActivityHandoffStore(directory);
        AgentPersistentActivityHandoffCoordinator first =
                new AgentPersistentActivityHandoffCoordinator(store);
        AgentActivitySourcePort source = source();

        AgentActivityHandoffCoordinator.Handoff started = first.begin(
                "agent-42:town-to-hunt", "world-director", AgentActivityKind.HUNTING,
                source, (agentId, kind, nowMs) -> AgentActivityPreflightPort.Result.allowed(),
                1_000L, 10_000L);

        AgentPersistentActivityHandoffCoordinator restarted =
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory));
        AgentActivityHandoffCoordinator.Handoff restored = restarted
                .restore(started.handoffId()).orElseThrow();

        assertEquals(started, restored);
        assertEquals(AgentActivityHandoffCoordinator.Phase.REQUEST_SOURCE_EXIT,
                restored.phase());
    }

    @Test
    void terminalHandoffRequiresAcknowledgementBeforeDeletion() {
        AgentPersistentActivityHandoffCoordinator coordinator =
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory));
        AgentActivityHandoffCoordinator.Handoff failed = coordinator.begin(
                "blocked", "world-director", AgentActivityKind.COMMERCE, source(),
                (agentId, kind, nowMs) -> AgentActivityPreflightPort.Result.blocked("busy"),
                1_000L, 10_000L);

        assertTrue(failed.terminal());
        coordinator.acknowledgeTerminal("blocked");
        assertTrue(coordinator.restore("blocked").isEmpty());
    }

    private static AgentActivitySourcePort source() {
        return new AgentActivitySourcePort() {
            @Override
            public AgentActivitySessionSnapshot snapshot(long nowMs) {
                return new AgentActivitySessionSnapshot(
                        AgentActivityKind.TOWN_LIFE, AgentActivityPhase.ACTIVE,
                        "town-1", "request-1", "test", "42", 500L, "");
            }

            @Override
            public AgentActivityExitResult requestGracefulExit(
                    String reason, long nowMs, long deadlineMs) {
                return AgentActivityExitResult.requested(reason);
            }
        };
    }
}
