package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentGrindNavigationConfiguredTest {
    @Test
    void preservesSelectorFallbacksForMissingRuntimeInputs() {
        Point agentPosition = new Point(10, 20);
        Point combatTargetPosition = new Point(30, 40);

        assertEquals(combatTargetPosition,
                AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(null, agentPosition, combatTargetPosition));
        assertEquals(combatTargetPosition,
                AgentGrindNavigationTargetSelector.selectGrindNavigationTarget(null, agentPosition, combatTargetPosition, true));
        assertNull(AgentGrindNavigationTargetSelector.selectCrossRegionRetreatTarget(null, agentPosition, combatTargetPosition));
        assertFalse(AgentGrindNavigationTargetSelector.shouldUseLocalCombatRetreatTarget(
                null, agentPosition, combatTargetPosition, new Point(5, 20)));
    }
}
