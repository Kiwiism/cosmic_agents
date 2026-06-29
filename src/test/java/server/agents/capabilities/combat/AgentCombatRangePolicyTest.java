package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import client.inventory.WeaponType;
import java.awt.Point;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;

class AgentCombatRangePolicyTest {
    @Test
    void shouldAcceptBasicTargetsWithinLegacyHorizontalAndVerticalRange() {
        assertTrue(AgentCombatRangePolicy.isBasicAttackInRange(new Point(100, 200), new Point(170, 160)));
    }

    @Test
    void shouldRejectBasicTargetsOutsideLegacyHorizontalOrVerticalRange() {
        assertFalse(AgentCombatRangePolicy.isBasicAttackInRange(new Point(100, 200), new Point(181, 160)));
        assertFalse(AgentCombatRangePolicy.isBasicAttackInRange(new Point(100, 200), new Point(120, 80)));
        assertFalse(AgentCombatRangePolicy.isBasicAttackInRange(new Point(100, 200), new Point(120, 221)));
    }

    @Test
    void shouldAllowDiagonalJumpAttackForCloseRangeTargetsSlightlyAbove() {
        assertTrue(AgentCombatRangePolicy.isTargetJumpable(
                AgentMovementProfile.base(), true, new Point(100, 200), new Point(230, 135), 80.0));
    }

    @Test
    void shouldRejectJumpAttackForNonCloseRoutesOrTargetsTooFarOrHigh() {
        assertFalse(AgentCombatRangePolicy.isTargetJumpable(
                AgentMovementProfile.base(), false, new Point(100, 200), new Point(170, 135), 80.0));
        assertFalse(AgentCombatRangePolicy.isTargetJumpable(
                AgentMovementProfile.base(), true, new Point(100, 200), new Point(241, 135), 80.0));
        assertFalse(AgentCombatRangePolicy.isTargetJumpable(
                AgentMovementProfile.base(), true, new Point(100, 200), new Point(170, 60), 80.0));
    }

    @Test
    void shouldRejectAirborneRangedAttackPlansForBowCrossbowAndGunOnly() {
        assertFalse(AgentCombatRangePolicy.canUseAttackPlanNow(false, WeaponType.BOW, AgentAttackRoute.RANGED));
        assertFalse(AgentCombatRangePolicy.canUseAttackPlanNow(false, WeaponType.CROSSBOW, AgentAttackRoute.RANGED));
        assertFalse(AgentCombatRangePolicy.canUseAttackPlanNow(false, WeaponType.GUN, AgentAttackRoute.RANGED));
        assertTrue(AgentCombatRangePolicy.canUseAttackPlanNow(false, WeaponType.CLAW, AgentAttackRoute.RANGED));
        assertTrue(AgentCombatRangePolicy.canUseAttackPlanNow(false, WeaponType.BOW, AgentAttackRoute.CLOSE));
        assertTrue(AgentCombatRangePolicy.canUseAttackPlanNow(true, WeaponType.BOW, AgentAttackRoute.RANGED));
    }
}
