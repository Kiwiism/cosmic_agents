package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMovementSkillModeTest {
    @Test
    void separatesOffShadowAndActive() {
        AgentMovementSkillMode off = AgentMovementSkillMode.parse("off", "mode");
        AgentMovementSkillMode shadow = AgentMovementSkillMode.parse("shadow", "mode");
        AgentMovementSkillMode active = AgentMovementSkillMode.parse("active", "mode");

        assertFalse(off.visibleToShadowRouting());
        assertTrue(shadow.visibleToShadowRouting());
        assertFalse(shadow.active());
        assertTrue(active.visibleToShadowRouting());
        assertTrue(active.active());
        assertThrows(IllegalStateException.class,
                () -> AgentMovementSkillMode.parse("enabled", "mode"));
    }
}
