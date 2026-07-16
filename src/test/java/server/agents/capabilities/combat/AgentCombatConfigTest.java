package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.mobcontrol.AgentMobReactionMode;

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
        assertEquals("MOB_PHYSICS_SPEED_PERCENT = "
                        + AgentCombatConfig.cfg.MOB_PHYSICS_SPEED_PERCENT,
                AgentCombatConfig.configFieldLine("mob_physics_speed_percent"));
    }

    @Test
    void setsConfigFieldsUsingLegacyParsingRules() {
        int originalRange = AgentCombatConfig.cfg.ATTACK_RANGE_X;
        boolean originalDebug = AgentCombatConfig.cfg.AOE_REPOSITION_DEBUG;
        boolean originalHit1 = AgentCombatConfig.cfg.MOB_PHYSICS_HIT1_ENABLED;
        try {
            assertEquals("OK: ATTACK_RANGE_X = 123",
                    AgentCombatConfig.setConfigField("attack_range_x", "123"));
            assertEquals(123, AgentCombatConfig.cfg.ATTACK_RANGE_X);

            assertEquals("OK: AOE_REPOSITION_DEBUG = true",
                    AgentCombatConfig.setConfigField("AOE_REPOSITION_DEBUG", "on"));
            assertTrue(AgentCombatConfig.cfg.AOE_REPOSITION_DEBUG);

            assertEquals("OK: MOB_PHYSICS_HIT1_ENABLED = false",
                    AgentCombatConfig.setConfigField("MOB_PHYSICS_HIT1_ENABLED", "off"));
            assertEquals(false, AgentCombatConfig.cfg.MOB_PHYSICS_HIT1_ENABLED);

            String badValue = AgentCombatConfig.setConfigField("ATTACK_RANGE_X", "abc");
            assertTrue(badValue.startsWith("bad value 'abc' for ATTACK_RANGE_X"));

            assertEquals("unknown field: NOT_A_FIELD",
                    AgentCombatConfig.setConfigField("NOT_A_FIELD", "1"));
            assertNotNull(AgentCombatConfig.configFieldLines());
        } finally {
            AgentCombatConfig.cfg.ATTACK_RANGE_X = originalRange;
            AgentCombatConfig.cfg.AOE_REPOSITION_DEBUG = originalDebug;
            AgentCombatConfig.cfg.MOB_PHYSICS_HIT1_ENABLED = originalHit1;
        }
    }

    @Test
    void reactionModeSupportsCaseInsensitiveLiveValues() {
        AgentMobReactionMode originalMode = AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE;
        try {
            assertEquals("OK: AGENT_MOB_REACTION_MODE = OFF",
                    AgentCombatConfig.setConfigField(
                            "AGENT_MOB_REACTION_MODE", "off"));
            assertEquals(AgentMobReactionMode.OFF, AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE);

            assertEquals("OK: AGENT_MOB_REACTION_MODE = SYNTHETIC",
                    AgentCombatConfig.setConfigField(
                            "AGENT_MOB_REACTION_MODE", "synthetic"));
            assertEquals(AgentMobReactionMode.SYNTHETIC,
                    AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE);
            assertTrue(AgentCombatConfig.setConfigField(
                    "AGENT_MOB_REACTION_MODE", "invalid").startsWith("bad value"));
        } finally {
            AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = originalMode;
        }
    }

    @Test
    void rejectsUnsafeOrContradictoryLivePhysicsValues() {
        int originalSpeed = AgentCombatConfig.cfg.MOB_PHYSICS_SPEED_PERCENT;
        int originalChance = AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT;
        int originalStop = AgentCombatConfig.cfg.MOB_PHYSICS_STOP_DISTANCE_X;
        int originalWarmup = AgentCombatConfig.cfg.MOB_PHYSICS_OBSERVER_WARMUP_MS;
        int originalAggroTimeout = AgentCombatConfig.cfg.MOB_PHYSICS_AGGRO_TIMEOUT_MS;
        try {
            assertEquals("value for MOB_PHYSICS_SPEED_PERCENT must be between 0 and 300",
                    AgentCombatConfig.setConfigField("MOB_PHYSICS_SPEED_PERCENT", "301"));
            assertEquals(originalSpeed, AgentCombatConfig.cfg.MOB_PHYSICS_SPEED_PERCENT);

            assertEquals("value for MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT must be between 0 and 100",
                    AgentCombatConfig.setConfigField(
                            "MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT", "101"));
            assertEquals(originalChance,
                    AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT);

            assertEquals("MOB_PHYSICS_STOP_DISTANCE_X cannot exceed MOB_PHYSICS_RESUME_DISTANCE_X",
                    AgentCombatConfig.setConfigField("MOB_PHYSICS_STOP_DISTANCE_X",
                            Integer.toString(AgentCombatConfig.cfg.MOB_PHYSICS_RESUME_DISTANCE_X + 1)));
            assertEquals(originalStop, AgentCombatConfig.cfg.MOB_PHYSICS_STOP_DISTANCE_X);

            assertEquals("value for MOB_PHYSICS_OBSERVER_WARMUP_MS must be between 0 and 60000",
                    AgentCombatConfig.setConfigField("MOB_PHYSICS_OBSERVER_WARMUP_MS", "60001"));
            assertEquals(originalWarmup, AgentCombatConfig.cfg.MOB_PHYSICS_OBSERVER_WARMUP_MS);

            assertEquals("value for MOB_PHYSICS_AGGRO_TIMEOUT_MS must be between 0 and 60000",
                    AgentCombatConfig.setConfigField("MOB_PHYSICS_AGGRO_TIMEOUT_MS", "60001"));
            assertEquals(originalAggroTimeout,
                    AgentCombatConfig.cfg.MOB_PHYSICS_AGGRO_TIMEOUT_MS);
        } finally {
            AgentCombatConfig.cfg.MOB_PHYSICS_SPEED_PERCENT = originalSpeed;
            AgentCombatConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT = originalChance;
            AgentCombatConfig.cfg.MOB_PHYSICS_STOP_DISTANCE_X = originalStop;
            AgentCombatConfig.cfg.MOB_PHYSICS_OBSERVER_WARMUP_MS = originalWarmup;
            AgentCombatConfig.cfg.MOB_PHYSICS_AGGRO_TIMEOUT_MS = originalAggroTimeout;
        }
    }
}
