package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatGroundRuntimeTest {
    @Test
    void findGroundFootholdKeepsNullSafeAdapterBehavior() {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(null);

        assertNull(AgentCombatGroundRuntime.findGroundFoothold(new Point(100, 200), agent));
        assertNull(AgentCombatGroundRuntime.findGroundFoothold(null, agent));
        assertNull(AgentCombatGroundRuntime.findGroundFoothold(new Point(100, 200), null));
    }
}
