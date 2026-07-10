package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.combat.AgentGrindWanderStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentGrindTargetConfiguredTest {
    @Test
    void noGraphFallbackPreservesLegacyWanderDirectionThroughRuntimeBridge() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Point agentPosition = new Point(100, 100);

        Point target = AgentGrindTargetPositionService.resolveNoGrindTargetPosition(entry, agentPosition, null);
        int direction = AgentGrindWanderStateRuntime.wanderDirection(entry);

        assertTrue(direction == -1 || direction == 1);
        assertEquals(new Point(100 + direction * 200, 100), target);
    }
}
