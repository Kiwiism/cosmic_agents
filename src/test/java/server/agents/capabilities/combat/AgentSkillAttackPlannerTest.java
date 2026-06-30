package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import client.inventory.WeaponType;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import server.life.Monster;

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

    @Test
    void shouldRequireMaxBulletCostTimesHitMultiplierForRangedSkills() {
        assertEquals(AgentSkillAttackPlanner.SkillAmmoReadiness.INSUFFICIENT_AMMO,
                AgentSkillAttackPlanner.skillAmmoReadiness(1, 3, 2, AgentAttackRoute.RANGED, () -> 5));
        assertEquals(AgentSkillAttackPlanner.SkillAmmoReadiness.READY,
                AgentSkillAttackPlanner.skillAmmoReadiness(1, 3, 2, AgentAttackRoute.RANGED, () -> 6));
    }

    @Test
    void shouldSkipAmmoCountForNoCostOrNonRangedSkills() {
        AtomicBoolean ammoCounted = new AtomicBoolean(false);

        assertEquals(AgentSkillAttackPlanner.SkillAmmoReadiness.READY,
                AgentSkillAttackPlanner.skillAmmoReadiness(0, 0, 2, AgentAttackRoute.RANGED, () -> {
                    ammoCounted.set(true);
                    return 0;
                }));
        assertFalse(ammoCounted.get());

        assertEquals(AgentSkillAttackPlanner.SkillAmmoReadiness.READY,
                AgentSkillAttackPlanner.skillAmmoReadiness(1, 3, 2, AgentAttackRoute.CLOSE, () -> {
                    ammoCounted.set(true);
                    return 0;
                }));
        assertFalse(ammoCounted.get());
    }

    @Test
    void shouldRejectUnreachableStrikePointTargetBeforeResolvingEffectivePrimary() {
        Monster primary = mock(Monster.class);
        Rectangle hitBox = new Rectangle(0, 0, 10, 10);
        AtomicBoolean resolvedEffectivePrimary = new AtomicBoolean(false);
        AtomicBoolean checkedIntersection = new AtomicBoolean(false);

        assertNull(AgentSkillAttackPlanner.resolvePrimaryTargetAfterHitbox(
                true,
                primary,
                hitBox,
                () -> false,
                (candidate, candidateHitBox) -> {
                    resolvedEffectivePrimary.set(true);
                    return candidate;
                },
                (candidateHitBox, candidate) -> {
                    checkedIntersection.set(true);
                    return true;
                }));
        assertFalse(resolvedEffectivePrimary.get());
        assertFalse(checkedIntersection.get());
    }

    @Test
    void shouldKeepStrikePointPrimaryAndCheckIntersectionWhenReachable() {
        Monster primary = mock(Monster.class);
        Monster effective = mock(Monster.class);
        Rectangle hitBox = new Rectangle(0, 0, 10, 10);
        AtomicBoolean resolvedEffectivePrimary = new AtomicBoolean(false);

        AgentSkillAttackPlanner.SkillPrimaryTargetSelection selection =
                AgentSkillAttackPlanner.resolvePrimaryTargetAfterHitbox(
                        true,
                        primary,
                        hitBox,
                        () -> true,
                        (candidate, candidateHitBox) -> {
                            resolvedEffectivePrimary.set(true);
                            return effective;
                        },
                        (candidateHitBox, candidate) -> candidate == primary);

        assertEquals(primary, selection.target());
        assertFalse(resolvedEffectivePrimary.get());
    }

    @Test
    void shouldResolveEffectivePrimaryForNonStrikePointTargetWithoutReachGate() {
        Monster primary = mock(Monster.class);
        Monster effective = mock(Monster.class);
        Rectangle hitBox = new Rectangle(0, 0, 10, 10);
        AtomicBoolean checkedReach = new AtomicBoolean(false);

        AgentSkillAttackPlanner.SkillPrimaryTargetSelection selection =
                AgentSkillAttackPlanner.resolvePrimaryTargetAfterHitbox(
                        false,
                        primary,
                        hitBox,
                        () -> {
                            checkedReach.set(true);
                            return false;
                        },
                        (candidate, candidateHitBox) -> effective,
                        (candidateHitBox, candidate) -> candidate == effective);

        assertEquals(effective, selection.target());
        assertFalse(checkedReach.get());
    }

    @Test
    void shouldRejectResolvedPrimaryWhenHitboxDoesNotIntersect() {
        Monster primary = mock(Monster.class);
        Monster effective = mock(Monster.class);
        Rectangle hitBox = new Rectangle(0, 0, 10, 10);

        assertNull(AgentSkillAttackPlanner.resolvePrimaryTargetAfterHitbox(
                false,
                primary,
                hitBox,
                () -> true,
                (candidate, candidateHitBox) -> effective,
                (candidateHitBox, candidate) -> false));
    }

    @Test
    void shouldResolveCloseRangeSkillPacketFieldsWithLegacyCloseRangeMimic() {
        AgentAttackExecutionProvider.CloseRangePacketFields expected =
                AgentAttackExecutionProvider.mimicCloseRangePacketFields("alert2", "alert2", true);

        AgentSkillAttackPlanner.SkillAttackPacketFields fields =
                AgentSkillAttackPlanner.resolveSkillAttackPacketFields(
                        AgentAttackRoute.CLOSE,
                        WeaponType.BOW,
                        new Point(100, 100),
                        new Point(40, 100),
                        "alert2",
                        "alert2");

        assertEquals(expected.display(), fields.display());
        assertEquals(expected.bodyActionId(), fields.direction());
        assertEquals(expected.bodyActionId(), fields.rangedDirection());
        assertEquals(AgentAttackExecutionProvider.attackPacketStance(true), fields.stance());
    }

    @Test
    void shouldResolveNonCloseSkillPacketFieldsWithZeroDisplayAndSharedDirection() {
        int expectedDirection = AgentAttackExecutionProvider.bodyActionId("shoot1", "shoot1", WeaponType.BOW);

        AgentSkillAttackPlanner.SkillAttackPacketFields fields =
                AgentSkillAttackPlanner.resolveSkillAttackPacketFields(
                        AgentAttackRoute.RANGED,
                        WeaponType.BOW,
                        new Point(40, 100),
                        new Point(100, 100),
                        "shoot1",
                        "shoot1");

        assertEquals(0, fields.display());
        assertEquals(expectedDirection, fields.direction());
        assertEquals(expectedDirection, fields.rangedDirection());
        assertEquals(AgentAttackExecutionProvider.attackPacketStance(false), fields.stance());
    }
}
