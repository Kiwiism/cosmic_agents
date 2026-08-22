package server.agents.runtime.activity.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void startupCanDiscoverAndReconcileAllRetainedHandoffs() {
        AgentPersistentActivityHandoffCoordinator coordinator =
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory));
        AgentActivityHandoffCoordinator.Handoff started = coordinator.begin(
                "restore-all", "world-director", AgentActivityKind.HUNTING,
                source(), (agentId, kind, nowMs) -> AgentActivityPreflightPort.Result.allowed(),
                1_000L, 10_000L);

        AgentPersistentActivityHandoffCoordinator restarted =
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory));
        assertEquals(java.util.List.of(started), restarted.restoreAll());

        AgentActivityHandoffCoordinator.Handoff reconciled = restarted.reconcile(
                started.handoffId(), idleTownSource(), idleHuntingTarget(), 1_500L);
        assertEquals(AgentActivityHandoffCoordinator.Phase.TRANSFER, reconciled.phase());
        assertTrue(reconciled.sourceReleased());
    }

    @Test
    void repeatedBeginIsIdempotentAndSecondHandoffForAgentIsRejected() {
        AgentPersistentActivityHandoffCoordinator coordinator =
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory));
        AgentActivityHandoffCoordinator.Handoff first = coordinator.begin(
                "same-request", "world-director", AgentActivityKind.HUNTING,
                source(), (agentId, kind, nowMs) -> AgentActivityPreflightPort.Result.allowed(),
                1_000L, 10_000L);

        assertEquals(first, coordinator.begin(
                "same-request", "world-director", AgentActivityKind.HUNTING,
                source(), (agentId, kind, nowMs) -> AgentActivityPreflightPort.Result.allowed(),
                1_100L, 10_000L));
        assertThrows(IllegalStateException.class, () -> coordinator.begin(
                "competing-request", "world-director", AgentActivityKind.COMMERCE,
                source(), (agentId, kind, nowMs) -> AgentActivityPreflightPort.Result.allowed(),
                1_200L, 10_000L));
    }

    @Test
    void rollbackPhaseSurvivesCoordinatorRestart() {
        AgentPersistentActivityHandoffCoordinator coordinator =
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory));
        FakeReleasableSource source = new FakeReleasableSource();
        coordinator.begin("rollback-restart", "world-director", AgentActivityKind.HUNTING,
                source, (agentId, kind, nowMs) -> AgentActivityPreflightPort.Result.allowed(),
                2_000L, 12_000L);
        AgentActivityRollbackPort rollback = (sessionId, nowMs) ->
                AgentActivityRollbackPort.Result.resumed("restored");
        coordinator.advance("rollback-restart", source,
                now -> AgentActivityTransferPort.Result.ready(),
                now -> AgentActivityAdmissionResult.rejected("capacity"), rollback, 2_000L);
        source.active = false;
        coordinator.advance("rollback-restart", source,
                now -> AgentActivityTransferPort.Result.ready(),
                now -> AgentActivityAdmissionResult.rejected("capacity"), rollback, 2_250L);
        coordinator.advance("rollback-restart", source,
                now -> AgentActivityTransferPort.Result.ready(),
                now -> AgentActivityAdmissionResult.rejected("capacity"), rollback, 2_250L);
        AgentActivityHandoffCoordinator.Handoff rollingBack = coordinator.advance(
                "rollback-restart", source, now -> AgentActivityTransferPort.Result.ready(),
                now -> AgentActivityAdmissionResult.rejected("capacity"), rollback, 2_250L);
        assertEquals(AgentActivityHandoffCoordinator.Phase.ROLLBACK_SOURCE,
                rollingBack.phase());

        AgentPersistentActivityHandoffCoordinator restarted =
                new AgentPersistentActivityHandoffCoordinator(
                        new AgentFileActivityHandoffStore(directory));
        AgentActivityHandoffCoordinator.Handoff restored = restarted.advance(
                "rollback-restart", source, now -> AgentActivityTransferPort.Result.ready(),
                now -> AgentActivityAdmissionResult.rejected("unused"), rollback, 2_251L);

        assertEquals(AgentActivityHandoffCoordinator.Phase.ROLLED_BACK, restored.phase());
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

    private static final class FakeReleasableSource implements AgentActivitySourcePort {
        private boolean active = true;

        @Override
        public AgentActivitySessionSnapshot snapshot(long nowMs) {
            return active
                    ? new AgentActivitySessionSnapshot(AgentActivityKind.TOWN_LIFE,
                    AgentActivityPhase.ACTIVE, "town-1", "request-1", "test", "42", 500L, "")
                    : AgentActivitySessionSnapshot.idle(AgentActivityKind.TOWN_LIFE, "42");
        }

        @Override
        public AgentActivityExitResult requestGracefulExit(
                String reason, long nowMs, long deadlineMs) {
            return AgentActivityExitResult.requested(reason);
        }
    }

    private static AgentActivitySourcePort idleTownSource() {
        return observer(AgentActivitySessionSnapshot.idle(AgentActivityKind.TOWN_LIFE, "42"));
    }

    private static AgentActivitySourcePort idleHuntingTarget() {
        return observer(AgentActivitySessionSnapshot.idle(AgentActivityKind.HUNTING, "42"));
    }

    private static AgentActivitySourcePort observer(AgentActivitySessionSnapshot snapshot) {
        return new AgentActivitySourcePort() {
            @Override public AgentActivitySessionSnapshot snapshot(long nowMs) { return snapshot; }
            @Override public AgentActivityExitResult requestGracefulExit(
                    String reason, long nowMs, long deadlineMs) {
                throw new AssertionError("read-only observer cannot receive exit requests");
            }
        };
    }
}
