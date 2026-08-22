package server.agents.runtime.activity.delegation;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDelegatedActivityCoordinatorTest {
    @Test
    void childCannotOutliveOrChangeItsParentSession() {
        AgentRuntimeEntry entry = entry();
        AgentDelegatedActivityCoordinator coordinator = new AgentDelegatedActivityCoordinator();
        coordinator.attach(entry, "plan:field", AgentActivityKind.QUESTING, "plan-1",
                AgentActivityKind.HUNTING, "field-1", 1_000L, 5_000L, "quest field visit");

        assertTrue(coordinator.retainForParent(
                entry, AgentActivityKind.QUESTING, "plan-1", 1_100L));
        assertFalse(coordinator.retainForParent(
                entry, AgentActivityKind.QUESTING, "plan-2", 1_200L));
        assertFalse(entry.capabilityStates()
                .require(AgentDelegatedActivityState.STATE_KEY).active());
    }

    @Test
    void onlyOneDelegatedChildMayBeAttached() {
        AgentRuntimeEntry entry = entry();
        AgentDelegatedActivityCoordinator coordinator = new AgentDelegatedActivityCoordinator();
        coordinator.attach(entry, "plan:field", AgentActivityKind.QUESTING, "plan-1",
                AgentActivityKind.HUNTING, "field-1", 1_000L, 0L, "field");

        assertThrows(IllegalStateException.class, () -> coordinator.attach(
                entry, "plan:town", AgentActivityKind.QUESTING, "plan-1",
                AgentActivityKind.TOWN_LIFE, "town-1", 1_001L, 0L, "town"));
    }

    private static AgentRuntimeEntry entry() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        server.agents.runtime.state.AgentCapabilityStateRegistry states =
                new server.agents.runtime.state.AgentCapabilityStateRegistry();
        when(entry.capabilityStates()).thenReturn(states);
        return entry;
    }
}
