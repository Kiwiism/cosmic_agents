package server.agents.runtime.activity.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentActivityHandoffCoordinatorTest {
    private final AgentActivityHandoffCoordinator coordinator =
            new AgentActivityHandoffCoordinator();

    @Test
    void drainsSourceBeforeTravelAndDestinationAdmission() {
        FakeSource source = new FakeSource();
        AgentActivityHandoffCoordinator.Handoff handoff = coordinator.begin(
                "handoff-1", "world-selector", AgentActivityKind.HUNTING,
                source, ready(), 1_000L, 10_000L);
        CountingTransfer transfer = new CountingTransfer();
        CountingTarget target = new CountingTarget();

        handoff = coordinator.advance(handoff, source, transfer, target, 1_000L);
        assertEquals(AgentActivityHandoffCoordinator.Phase.WAIT_SOURCE_RELEASE, handoff.phase());
        assertEquals(0, transfer.calls);
        assertEquals(0, target.calls);

        handoff = coordinator.advance(handoff, source, transfer, target, 1_250L);
        assertEquals(AgentActivityHandoffCoordinator.Phase.WAIT_SOURCE_RELEASE, handoff.phase());
        assertEquals(0, transfer.calls);

        source.release();
        handoff = coordinator.advance(handoff, source, transfer, target, 1_500L);
        assertEquals(AgentActivityHandoffCoordinator.Phase.TRANSFER, handoff.phase());
        assertEquals(0, transfer.calls);

        handoff = coordinator.advance(handoff, source, transfer, target, 1_500L);
        assertEquals(AgentActivityHandoffCoordinator.Phase.REQUEST_TARGET_ENTRY, handoff.phase());
        assertEquals(1, transfer.calls);

        handoff = coordinator.advance(handoff, source, transfer, target, 1_500L);
        assertTrue(handoff.terminal());
        assertEquals(AgentActivityHandoffCoordinator.Phase.COMPLETED, handoff.phase());
        assertEquals(1, target.calls);
    }

    @Test
    void deferredAdmissionRetriesWithoutRestartingTravel() {
        FakeSource source = new FakeSource();
        AgentActivityHandoffCoordinator.Handoff handoff = coordinator.begin(
                "handoff-2", "world-selector", AgentActivityKind.HUNTING,
                source, ready(), 2_000L, 12_000L);
        CountingTransfer transfer = new CountingTransfer();
        CountingTarget target = new CountingTarget();
        target.deferOnce = true;

        handoff = coordinator.advance(handoff, source, transfer, target, 2_000L);
        source.release();
        handoff = coordinator.advance(handoff, source, transfer, target, 2_250L);
        handoff = coordinator.advance(handoff, source, transfer, target, 2_250L);
        handoff = coordinator.advance(handoff, source, transfer, target, 2_250L);

        assertEquals(AgentActivityHandoffCoordinator.Phase.REQUEST_TARGET_ENTRY, handoff.phase());
        assertFalse(handoff.terminal());
        assertEquals(1, transfer.calls);
        assertEquals(1, target.calls);

        handoff = coordinator.advance(handoff, source, transfer, target, 2_750L);
        assertEquals(AgentActivityHandoffCoordinator.Phase.COMPLETED, handoff.phase());
        assertEquals(1, transfer.calls);
        assertEquals(2, target.calls);
    }

    @Test
    void failsClosedWhenSourceOwnershipChanges() {
        FakeSource source = new FakeSource();
        AgentActivityHandoffCoordinator.Handoff handoff = coordinator.begin(
                "handoff-3", "world-selector", AgentActivityKind.COMMERCE,
                source, ready(), 3_000L, 13_000L);
        handoff = coordinator.advance(
                handoff, source, now -> AgentActivityTransferPort.Result.ready(),
                now -> AgentActivityAdmissionResult.rejected("unused"), 3_000L);

        source.sessionId = "different-owner";
        handoff = coordinator.advance(
                handoff, source, now -> AgentActivityTransferPort.Result.ready(),
                now -> AgentActivityAdmissionResult.rejected("unused"), 3_250L);

        assertEquals(AgentActivityHandoffCoordinator.Phase.FAILED, handoff.phase());
        assertTrue(handoff.reason().contains("ownership changed"));
    }

    @Test
    void blockedPreflightLeavesSourceUntouched() {
        FakeSource source = new FakeSource();
        AgentActivityHandoffCoordinator.Handoff handoff = coordinator.begin(
                "handoff-4", "world-selector", AgentActivityKind.HUNTING,
                source, (agentId, kind, nowMs) ->
                        AgentActivityPreflightPort.Result.blocked("map is full"),
                4_000L, 14_000L);

        assertEquals(AgentActivityHandoffCoordinator.Phase.FAILED, handoff.phase());
        assertEquals(0, source.exitRequests);
        assertTrue(source.active);
    }

    private static AgentActivityPreflightPort ready() {
        return (agentId, kind, nowMs) -> AgentActivityPreflightPort.Result.allowed();
    }

    private static final class FakeSource implements AgentActivitySourcePort {
        private boolean active = true;
        private String sessionId = "town-session";
        private int exitRequests;

        @Override
        public AgentActivitySessionSnapshot snapshot(long nowMs) {
            return active
                    ? new AgentActivitySessionSnapshot(
                    AgentActivityKind.TOWN_LIFE, AgentActivityPhase.DRAINING,
                    sessionId, "town-request", "world-selector", "42", 500L, "")
                    : AgentActivitySessionSnapshot.idle(AgentActivityKind.TOWN_LIFE, "42");
        }

        @Override
        public AgentActivityExitResult requestGracefulExit(
                String reason, long nowMs, long deadlineMs) {
            exitRequests++;
            return AgentActivityExitResult.requested(reason);
        }

        void release() { active = false; }
    }

    private static final class CountingTransfer implements AgentActivityTransferPort {
        private int calls;

        @Override
        public Result advance(long nowMs) {
            calls++;
            return Result.ready();
        }
    }

    private static final class CountingTarget implements AgentActivityTargetPort {
        private int calls;
        private boolean deferOnce;

        @Override
        public AgentActivityAdmissionResult requestEntry(long nowMs) {
            calls++;
            if (deferOnce && calls == 1) {
                return AgentActivityAdmissionResult.deferred("capacity", nowMs + 500L);
            }
            return AgentActivityAdmissionResult.accepted(new AgentActivitySessionSnapshot(
                    AgentActivityKind.HUNTING, AgentActivityPhase.ACTIVE,
                    "field-session", "field-request", "world-selector", "42", nowMs, ""));
        }
    }
}
