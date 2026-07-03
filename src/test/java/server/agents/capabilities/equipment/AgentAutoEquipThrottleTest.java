package server.agents.capabilities.equipment;

import client.Character;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAutoEquipThrottleTest {
    @BeforeEach
    void resetThrottle() {
        AgentAutoEquipThrottle.clearForTest();
    }

    @Test
    void shouldThrottleRepeatedAutoEquipUnlessForcedOrExpired() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(9_876_543);

        assertTrue(AgentAutoEquipThrottle.shouldRun(agent, 1_000L, false));
        assertFalse(AgentAutoEquipThrottle.shouldRun(agent, 5_000L, false),
                "duplicate mode-command triggers should not rerun the optimizer");
        assertTrue(AgentAutoEquipThrottle.shouldRun(agent, 6_000L, true),
                "explicit autoequip command should bypass the throttle");
        assertFalse(AgentAutoEquipThrottle.shouldRun(agent, 7_000L, false),
                "forced runs still refresh the normal throttle window");
        assertTrue(AgentAutoEquipThrottle.shouldRun(agent, 36_001L, false));
    }

    @Test
    void shouldAllowNullAgentForLegacyCallers() {
        assertTrue(AgentAutoEquipThrottle.shouldRun(null, 1_000L, false));
    }
}
