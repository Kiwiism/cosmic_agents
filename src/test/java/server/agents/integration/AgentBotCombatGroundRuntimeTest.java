package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotCombatGroundRuntimeTest {
    @Test
    void findGroundFootholdKeepsNullSafeAdapterBehavior() {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(null);

        assertNull(AgentBotCombatGroundRuntime.findGroundFoothold(new Point(100, 200), agent));
        assertNull(AgentBotCombatGroundRuntime.findGroundFoothold(null, agent));
        assertNull(AgentBotCombatGroundRuntime.findGroundFoothold(new Point(100, 200), null));
    }
}
