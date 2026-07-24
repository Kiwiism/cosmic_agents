package server.agents.runtime.decision;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBehaviorVersionRouterTest {
    @Test
    void routeIsStableForAgentAndRecordsItsReason() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(42);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentBehaviorRoute first = AgentBehaviorVersionRouter.route(
                entry, "combat-targeting", "v1", "v2",
                AgentBehaviorRouteMode.CANARY, 25, 100L, "objective:1");
        AgentBehaviorRoute second = AgentBehaviorVersionRouter.route(
                entry, "combat-targeting", "v1", "v2",
                AgentBehaviorRouteMode.CANARY, 25, 200L, "objective:1");

        assertEquals(first.executionVersion(), second.executionVersion());
        assertEquals(2L, entry.capabilityStates()
                .require(AgentDecisionProvenanceState.STATE_KEY).sequence());
        assertEquals(first.executionVersion(), entry.capabilityStates()
                .require(AgentDecisionProvenanceState.STATE_KEY).latest().choice());
    }
}
