package server.agents.runtime.activity;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AgentActivityOwnershipReconcilerTest {
    @Test
    void blocksAmbiguousMultipleOwnersWithoutStoppingEither() {
        FakeController town = new FakeController("town", AgentActivityKind.TOWN_LIFE);
        FakeController hunt = new FakeController("hunt", AgentActivityKind.HUNTING);
        AgentActivityOwnershipReconciliation result = reconciler(town, hunt)
                .reconcile(mock(AgentRuntimeEntry.class), mock(Character.class), null, 1_000L);

        assertEquals(AgentActivityOwnershipReconciliation.Status.BLOCKED, result.status());
        assertEquals(0, town.stopRequests + hunt.stopRequests);
    }

    @Test
    void retainsHandoffOwnerAndGracefullyStopsConflict() {
        FakeController town = new FakeController("town", AgentActivityKind.TOWN_LIFE);
        FakeController hunt = new FakeController("hunt", AgentActivityKind.HUNTING);
        AgentActivityOwnershipReconciliation result = reconciler(town, hunt)
                .reconcile(mock(AgentRuntimeEntry.class), mock(Character.class),
                        AgentActivityKind.HUNTING, 1_000L);

        assertEquals(AgentActivityOwnershipReconciliation.Status.RECONCILED, result.status());
        assertEquals(1, town.stopRequests);
        assertFalse(town.active);
    }

    private static AgentActivityOwnershipReconciler reconciler(FakeController... controllers) {
        return new AgentActivityOwnershipReconciler(
                new AgentActivityControllerRegistry(List.of(controllers)));
    }

    private static final class FakeController implements AgentActivityController {
        private final String id;
        private final AgentActivityKind kind;
        private boolean active = true;
        private int stopRequests;

        private FakeController(String id, AgentActivityKind kind) {
            this.id = id;
            this.kind = kind;
        }

        @Override public String id() { return id; }
        @Override public int precedence() { return 1; }
        @Override public AgentActivityRole role() { return AgentActivityRole.PRIMARY; }
        @Override public AgentActivityKind activityKind() { return kind; }
        @Override public boolean active(AgentRuntimeEntry entry, Character agent) { return active; }
        @Override public AgentActivityTick tick(
                AgentRuntimeEntry entry, Character agent, long nowMs) { return AgentActivityTick.IDLE; }
        @Override public boolean requestStop(
                AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
            stopRequests++;
            active = false;
            return true;
        }
    }
}
