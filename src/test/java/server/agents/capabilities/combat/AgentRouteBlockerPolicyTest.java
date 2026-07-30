package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentRouteBlockerPolicyTest {
    @Test
    void corridorIncludesMobsBetweenAgentAndDestination() {
        assertTrue(AgentCombatTargetRuntime.insideRouteCorridor(
                new Point(0, 0), new Point(200, 0), new Point(80, 35), 40));
    }

    @Test
    void corridorRejectsMobsBehindBeyondOrFarFromRoute() {
        Point start = new Point(0, 0);
        Point end = new Point(200, 0);

        assertFalse(AgentCombatTargetRuntime.insideRouteCorridor(
                start, end, new Point(-1, 0), 40));
        assertFalse(AgentCombatTargetRuntime.insideRouteCorridor(
                start, end, new Point(201, 0), 40));
        assertFalse(AgentCombatTargetRuntime.insideRouteCorridor(
                start, end, new Point(80, 41), 40));
    }

    @Test
    void killBudgetOnlyCountsTheSelectedRouteBlocker() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentCombatDirectiveRuntime.assignPreferences(entry, Set.of(100), Set.of(200));
        AgentCombatDirectiveRuntime.state(entry).selected(
                1, -1, 200, AgentCombatCandidateClass.INCIDENTAL,
                AgentCombatDecisionReason.ROUTE_BLOCKER, 1_000L);
        AgentRouteBlockerState blocker =
                entry.capabilityStates().require(AgentRouteBlockerState.STATE_KEY);
        blocker.canInterrupt(new Point(100, 0), 1_000L);
        AgentCombatTacticalEventListener listener = new AgentCombatTacticalEventListener(entry);

        listener.onAgentEvent(new AgentMobKilledEvent(
                1, 1_001L, 1, 201, 2_001, 1, ""));
        assertEquals(0, blocker.snapshot(1_001L).kills());

        listener.onAgentEvent(new AgentMobKilledEvent(
                1, 1_002L, 1, 200, 2_002, 1, ""));
        assertEquals(1, blocker.snapshot(1_002L).kills());
    }
}
