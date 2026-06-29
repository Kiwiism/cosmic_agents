package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AgentSkillAttackPlannerTest {
    @Test
    void shouldPreserveSkillAttackPreflightOrderForEarlyStops() {
        AtomicBoolean costChecked = new AtomicBoolean(false);
        AtomicBoolean weaponChecked = new AtomicBoolean(false);

        assertEquals(AgentSkillAttackPlanner.SkillAttackReadiness.MISSING_SKILL_ID,
                AgentSkillAttackPlanner.skillAttackReadiness(
                        0, true, false, 0,
                        () -> {
                            costChecked.set(true);
                            return true;
                        },
                        () -> {
                            weaponChecked.set(true);
                            return true;
                        }));
        assertFalse(costChecked.get());
        assertFalse(weaponChecked.get());

        assertEquals(AgentSkillAttackPlanner.SkillAttackReadiness.SKILL_COOLDOWN,
                AgentSkillAttackPlanner.skillAttackReadiness(
                        1001004, true, false, 0,
                        () -> {
                            costChecked.set(true);
                            return true;
                        },
                        () -> {
                            weaponChecked.set(true);
                            return true;
                        }));
        assertFalse(costChecked.get());
        assertFalse(weaponChecked.get());
    }

    @Test
    void shouldRejectMissingSkillAndMissingLevelBeforeCostCheck() {
        AtomicBoolean costChecked = new AtomicBoolean(false);

        assertEquals(AgentSkillAttackPlanner.SkillAttackReadiness.SKILL_MISSING,
                AgentSkillAttackPlanner.skillAttackReadiness(
                        1001004, false, false, 0,
                        () -> {
                            costChecked.set(true);
                            return true;
                        },
                        () -> true));
        assertFalse(costChecked.get());

        assertEquals(AgentSkillAttackPlanner.SkillAttackReadiness.SKILL_LEVEL_MISSING,
                AgentSkillAttackPlanner.skillAttackReadiness(
                        1001004, false, true, 0,
                        () -> {
                            costChecked.set(true);
                            return true;
                        },
                        () -> true));
        assertFalse(costChecked.get());
    }

    @Test
    void shouldCheckCostBeforeWeaponCompatibility() {
        AtomicBoolean weaponChecked = new AtomicBoolean(false);

        assertEquals(AgentSkillAttackPlanner.SkillAttackReadiness.CANNOT_PAY_COST,
                AgentSkillAttackPlanner.skillAttackReadiness(
                        1001004, false, true, 1,
                        () -> false,
                        () -> {
                            weaponChecked.set(true);
                            return true;
                        }));
        assertFalse(weaponChecked.get());
    }

    @Test
    void shouldReturnWeaponIncompatibleOrReadyAfterCostPasses() {
        AtomicBoolean weaponChecked = new AtomicBoolean(false);

        assertEquals(AgentSkillAttackPlanner.SkillAttackReadiness.WEAPON_INCOMPATIBLE,
                AgentSkillAttackPlanner.skillAttackReadiness(
                        1001004, false, true, 1,
                        () -> true,
                        () -> {
                            weaponChecked.set(true);
                            return false;
                        }));
        assertTrue(weaponChecked.get());

        assertEquals(AgentSkillAttackPlanner.SkillAttackReadiness.READY,
                AgentSkillAttackPlanner.skillAttackReadiness(
                        1001004, false, true, 1, () -> true, () -> true));
    }
}
