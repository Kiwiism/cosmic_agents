package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import client.Character;
import client.inventory.WeaponType;
import java.awt.Point;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.life.Monster;

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

    @Test
    void shouldBuildCloseBasicWeaponReachRectangleForFacingDirection() {
        Character bot = characterAt(new Point(100, 200));

        assertEquals(new Rectangle(100, 200 - AgentCombatConfig.cfg.ATTACK_RANGE_Y,
                        AgentCombatConfig.cfg.ATTACK_RANGE_X,
                        AgentCombatConfig.cfg.ATTACK_RANGE_Y + AgentCombatConfig.cfg.ATTACK_DOWN_MAX),
                AgentCombatRangePolicy.basicWeaponReachRect(bot, false, AgentAttackRoute.CLOSE));
        assertEquals(new Rectangle(100 - AgentCombatConfig.cfg.ATTACK_RANGE_X,
                        200 - AgentCombatConfig.cfg.ATTACK_RANGE_Y,
                        AgentCombatConfig.cfg.ATTACK_RANGE_X,
                        AgentCombatConfig.cfg.ATTACK_RANGE_Y + AgentCombatConfig.cfg.ATTACK_DOWN_MAX),
                AgentCombatRangePolicy.basicWeaponReachRect(bot, true, AgentAttackRoute.CLOSE));
        assertNull(AgentCombatRangePolicy.basicWeaponReachRect(bot, false, AgentAttackRoute.MAGIC));
    }

    @Test
    void shouldResolvePrimaryReachabilityWithBasicWeaponRectangle() {
        Character bot = characterAt(new Point(100, 200));
        Monster reachable = monsterAt(120, 200);
        Monster unreachable = monsterAt(260, 200);

        assertTrue(AgentCombatRangePolicy.isPrimaryReachableByBasicWeapon(bot, reachable, AgentAttackRoute.CLOSE));
        assertFalse(AgentCombatRangePolicy.isPrimaryReachableByBasicWeapon(bot, unreachable, AgentAttackRoute.CLOSE));
        assertTrue(AgentCombatRangePolicy.isPrimaryReachableByBasicWeapon(bot, unreachable, AgentAttackRoute.MAGIC));
        assertFalse(AgentCombatRangePolicy.isPrimaryReachableByBasicWeapon(null, reachable, AgentAttackRoute.CLOSE));
    }

    private static Character characterAt(Point position) {
        Character bot = mock(Character.class);
        when(bot.getPosition()).thenReturn(position);
        return bot;
    }

    private static Monster monsterAt(int x, int y) {
        Monster monster = mock(Monster.class);
        when(monster.getId()).thenReturn(91_000_000 + x + y);
        when(monster.getPosition()).thenReturn(new Point(x, y));
        when(monster.isFacingLeft()).thenReturn(false);
        return monster;
    }
}
