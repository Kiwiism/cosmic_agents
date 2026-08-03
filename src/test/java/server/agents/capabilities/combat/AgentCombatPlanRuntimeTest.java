package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatPlanRuntimeTest {
    @Test
    void magicSkillMakesBasicWandAttackAFallbackRatherThanCompetitor() {
        AgentAttackPlan energyBolt = plan(2001004, AgentAttackRoute.MAGIC);
        AgentAttackPlan wandSwing = plan(0, AgentAttackRoute.CLOSE);

        assertTrue(AgentCombatPlanRuntime.hasUsableMagicSkill(List.of(energyBolt, wandSwing)));
    }

    @Test
    void rangedAndCloseSkillsDoNotSuppressBasicFallback() {
        assertFalse(AgentCombatPlanRuntime.hasUsableMagicSkill(List.of(
                plan(3001004, AgentAttackRoute.RANGED),
                plan(1001004, AgentAttackRoute.CLOSE))));
    }

    private static AgentAttackPlan plan(int skillId, AgentAttackRoute route) {
        return new AgentAttackPlan(skillId, 1, 1, null, List.of(), route,
                0, 0, 0, 0, 0, 1, 1, WeaponType.WAND);
    }
}
