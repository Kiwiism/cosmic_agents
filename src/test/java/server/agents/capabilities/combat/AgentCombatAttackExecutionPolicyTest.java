package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AgentCombatAttackExecutionPolicyTest {
    @Test
    void shouldPreserveAttackExecutionPreflightOrder() {
        AtomicBoolean skillChecked = new AtomicBoolean(false);
        AtomicBoolean planChecked = new AtomicBoolean(false);

        assertEquals(AgentCombatAttackExecutionPolicy.AttackExecutionReadiness.ATTACK_COOLDOWN,
                AgentCombatAttackExecutionPolicy.attackExecutionReadiness(
                        true, true, 1001004,
                        () -> {
                            skillChecked.set(true);
                            return true;
                        },
                        () -> {
                            planChecked.set(true);
                            return true;
                        }));
        assertFalse(skillChecked.get());
        assertFalse(planChecked.get());

        assertEquals(AgentCombatAttackExecutionPolicy.AttackExecutionReadiness.NO_AMMO,
                AgentCombatAttackExecutionPolicy.attackExecutionReadiness(
                        false, true, 1001004,
                        () -> {
                            skillChecked.set(true);
                            return true;
                        },
                        () -> {
                            planChecked.set(true);
                            return true;
                        }));
        assertFalse(skillChecked.get());
        assertFalse(planChecked.get());
    }

    @Test
    void shouldSkipSkillCostCheckForBasicAttacksButStillCheckPlanReadiness() {
        AtomicBoolean skillChecked = new AtomicBoolean(false);
        AtomicBoolean planChecked = new AtomicBoolean(false);

        assertEquals(AgentCombatAttackExecutionPolicy.AttackExecutionReadiness.READY,
                AgentCombatAttackExecutionPolicy.attackExecutionReadiness(
                        false, false, 0,
                        () -> {
                            skillChecked.set(true);
                            return false;
                        },
                        () -> {
                            planChecked.set(true);
                            return true;
                        }));

        assertFalse(skillChecked.get());
        assertTrue(planChecked.get());
    }

    @Test
    void shouldRejectUnaffordableSkillsBeforeCheckingPlanReadiness() {
        AtomicBoolean planChecked = new AtomicBoolean(false);

        assertEquals(AgentCombatAttackExecutionPolicy.AttackExecutionReadiness.CANNOT_USE_SKILL,
                AgentCombatAttackExecutionPolicy.attackExecutionReadiness(
                        false, false, 1001004,
                        () -> false,
                        () -> {
                            planChecked.set(true);
                            return true;
                        }));

        assertFalse(planChecked.get());
    }

    @Test
    void shouldRejectBlockedPlansAfterSkillReadiness() {
        assertEquals(AgentCombatAttackExecutionPolicy.AttackExecutionReadiness.CANNOT_USE_ATTACK_PLAN,
                AgentCombatAttackExecutionPolicy.attackExecutionReadiness(
                        false, false, 1001004, () -> true, () -> false));
        assertEquals(AgentCombatAttackExecutionPolicy.AttackExecutionReadiness.READY,
                AgentCombatAttackExecutionPolicy.attackExecutionReadiness(
                        false, false, 1001004, () -> true, () -> true));
    }
}
