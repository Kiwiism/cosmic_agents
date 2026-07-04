package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatBuffStateTest {
    @Test
    void storesCooldownsAndToggles() {
        AgentCombatBuffState state = new AgentCombatBuffState();

        assertTrue(state.skillBuffsEnabled());
        assertTrue(state.supportHealsEnabled());

        state.setSkillBuffsEnabled(false);
        state.setSupportHealsEnabled(false);

        assertFalse(state.skillBuffsEnabled());
        assertFalse(state.supportHealsEnabled());

        state.ensureNextBuffAt(100, 1_000L);
        state.ensureNextBuffAt(100, 2_000L);
        state.setNextSupportBuffAt(200, 3_000L);

        assertEquals(1_000L, state.nextBuffAt(100));
        assertEquals(0L, state.nextBuffAt(101));
        assertTrue(state.supportBuffOnCooldown(200, 2_999L));
        assertFalse(state.supportBuffOnCooldown(200, 3_000L));
        assertEquals(3_000L, state.nextSupportBuffAt(200));
    }
}
