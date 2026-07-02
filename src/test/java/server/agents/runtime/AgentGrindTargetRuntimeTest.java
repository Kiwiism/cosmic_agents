package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotGrindWanderStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentGrindTargetRuntimeTest {
    @Test
    void noGraphFallbackPreservesLegacyWanderDirectionThroughRuntimeBridge() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Point agentPosition = new Point(100, 100);

        Point target = AgentGrindTargetRuntime.resolveNoGrindTargetPosition(entry, agentPosition, null);
        int direction = AgentBotGrindWanderStateRuntime.wanderDirection(entry);

        assertTrue(direction == -1 || direction == 1);
        assertEquals(new Point(100 + direction * 200, 100), target);
    }
}
