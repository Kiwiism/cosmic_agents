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
                        + server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_SPEED_PERCENT,
                AgentCombatConfig.configFieldLine("mob_physics_speed_percent"));
    }

    @Test
    void setsConfigFieldsUsingLegacyParsingRules() {
        int originalRange = AgentCombatConfig.cfg.ATTACK_RANGE_X;
        boolean originalDebug = AgentCombatConfig.cfg.AOE_REPOSITION_DEBUG;
        boolean originalVirtualObserverStress =
                server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS;
        try {
            assertEquals("OK: ATTACK_RANGE_X = 123",
                    AgentCombatConfig.setConfigField("attack_range_x", "123"));
            assertEquals(123, AgentCombatConfig.cfg.ATTACK_RANGE_X);

            assertEquals("OK: AOE_REPOSITION_DEBUG = true",
                    AgentCombatConfig.setConfigField("AOE_REPOSITION_DEBUG", "on"));
            assertTrue(AgentCombatConfig.cfg.AOE_REPOSITION_DEBUG);

            assertEquals("OK: MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS = true",
                    AgentCombatConfig.setConfigField(
                            "MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS", "on"));
            assertTrue(server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS);

            String badValue = AgentCombatConfig.setConfigField("ATTACK_RANGE_X", "abc");
            assertTrue(badValue.startsWith("bad value 'abc' for ATTACK_RANGE_X"));

            assertEquals("unknown field: NOT_A_FIELD",
                    AgentCombatConfig.setConfigField("NOT_A_FIELD", "1"));
            assertNotNull(AgentCombatConfig.configFieldLines());
        } finally {
            AgentCombatConfig.cfg.ATTACK_RANGE_X = originalRange;
            AgentCombatConfig.cfg.AOE_REPOSITION_DEBUG = originalDebug;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS =
                    originalVirtualObserverStress;
        }
    }

    @Test
    void reactionModeSupportsCaseInsensitiveLiveValues() {
        AgentMobReactionMode originalMode = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.AGENT_MOB_REACTION_MODE;
        try {
            assertEquals("OK: AGENT_MOB_REACTION_MODE = OFF",
                    AgentCombatConfig.setConfigField(
                            "AGENT_MOB_REACTION_MODE", "off"));
            assertEquals(AgentMobReactionMode.OFF, server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.AGENT_MOB_REACTION_MODE);

            assertTrue(AgentCombatConfig.setConfigField(
                    "AGENT_MOB_REACTION_MODE", "synthetic").startsWith("bad value"));
            assertTrue(AgentCombatConfig.setConfigField(
                    "AGENT_MOB_REACTION_MODE", "invalid").startsWith("bad value"));
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.AGENT_MOB_REACTION_MODE = originalMode;
        }
    }

    @Test
    void rejectsUnsafeOrContradictoryLivePhysicsValues() {
        int originalSpeed = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_SPEED_PERCENT;
        int originalChance = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT;
        int originalStop = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STOP_DISTANCE_X;
        int originalWarmup = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_OBSERVER_WARMUP_MS;
        int originalAggroTimeout = server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_AGGRO_TIMEOUT_MS;
        try {
            assertEquals("value for MOB_PHYSICS_SPEED_PERCENT must be between 0 and 300",
                    AgentCombatConfig.setConfigField("MOB_PHYSICS_SPEED_PERCENT", "301"));
            assertEquals(originalSpeed, server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_SPEED_PERCENT);

            assertEquals("value for MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT must be between 0 and 100",
                    AgentCombatConfig.setConfigField(
                            "MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT", "101"));
            assertEquals(originalChance,
                    server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT);

            assertEquals("MOB_PHYSICS_STOP_DISTANCE_X cannot exceed MOB_PHYSICS_RESUME_DISTANCE_X",
                    AgentCombatConfig.setConfigField("MOB_PHYSICS_STOP_DISTANCE_X",
                            Integer.toString(server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RESUME_DISTANCE_X + 1)));
            assertEquals(originalStop, server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STOP_DISTANCE_X);

            assertEquals("value for MOB_PHYSICS_OBSERVER_WARMUP_MS must be between 0 and 60000",
                    AgentCombatConfig.setConfigField("MOB_PHYSICS_OBSERVER_WARMUP_MS", "60001"));
            assertEquals(originalWarmup, server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_OBSERVER_WARMUP_MS);

            assertEquals("value for MOB_PHYSICS_AGGRO_TIMEOUT_MS must be between 0 and 60000",
                    AgentCombatConfig.setConfigField("MOB_PHYSICS_AGGRO_TIMEOUT_MS", "60001"));
            assertEquals(originalAggroTimeout,
                    server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_AGGRO_TIMEOUT_MS);
        } finally {
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_SPEED_PERCENT = originalSpeed;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT = originalChance;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STOP_DISTANCE_X = originalStop;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_OBSERVER_WARMUP_MS = originalWarmup;
            server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig.cfg.MOB_PHYSICS_AGGRO_TIMEOUT_MS = originalAggroTimeout;
        }
    }
}
