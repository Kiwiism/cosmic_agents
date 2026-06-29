package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatConfigTest {
    @Test
    void listsAndReadsPublicConfigFields() {
        assertTrue(AgentCombatConfig.configFieldLines().stream()
                .anyMatch(line -> line.startsWith("ATTACK_RANGE_X = ")));
        assertEquals("ATTACK_RANGE_X = " + AgentCombatConfig.cfg.ATTACK_RANGE_X,
                AgentCombatConfig.configFieldLine("attack_range_x"));
        assertNull(AgentCombatConfig.configFieldLine("missing_field"));
    }

    @Test
    void setsConfigFieldsUsingLegacyParsingRules() {
        int originalRange = AgentCombatConfig.cfg.ATTACK_RANGE_X;
        boolean originalDebug = AgentCombatConfig.cfg.AOE_REPOSITION_DEBUG;
        try {
            assertEquals("OK: ATTACK_RANGE_X = 123",
                    AgentCombatConfig.setConfigField("attack_range_x", "123"));
            assertEquals(123, AgentCombatConfig.cfg.ATTACK_RANGE_X);

            assertEquals("OK: AOE_REPOSITION_DEBUG = true",
                    AgentCombatConfig.setConfigField("AOE_REPOSITION_DEBUG", "on"));
            assertTrue(AgentCombatConfig.cfg.AOE_REPOSITION_DEBUG);

            String badValue = AgentCombatConfig.setConfigField("ATTACK_RANGE_X", "abc");
            assertTrue(badValue.startsWith("bad value 'abc' for ATTACK_RANGE_X"));

            assertEquals("unknown field: NOT_A_FIELD",
                    AgentCombatConfig.setConfigField("NOT_A_FIELD", "1"));
            assertNotNull(AgentCombatConfig.configFieldLines());
        } finally {
            AgentCombatConfig.cfg.ATTACK_RANGE_X = originalRange;
            AgentCombatConfig.cfg.AOE_REPOSITION_DEBUG = originalDebug;
        }
    }
}
