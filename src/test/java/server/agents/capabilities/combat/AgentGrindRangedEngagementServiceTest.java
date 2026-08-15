package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGrindRangedEngagementServiceTest {
    @Test
    void standsStillWhenInRangeButAttackIsNotUsableAndNoRetreatIsNeeded() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = monsterAt(200, 100);
        AgentAttackPlan plan = plan(target, AgentAttackRoute.RANGED);
        Counters counters = new Counters();

        AgentGrindRangedEngagementService.Result result = AgentGrindRangedEngagementService.engage(
                entry,
                agent,
                new Point(100, 100),
                new Point(150, 100),
                target,
                target.getPosition(),
                plan,
                null,
                hooks(counters, true, false, false));

        assertTrue(result.consumedTick());
        assertEquals(new Point(150, 100), result.targetPos());
        assertEquals(1, counters.idleOnGround.get());
        assertEquals(1, counters.broadcast.get());
        assertEquals(0, counters.attack.get());
    }

    @Test
    void jumpsTowardJumpableMeleeTargetWhenNotInRange() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = monsterAt(220, 80);
        AgentAttackPlan plan = plan(target, AgentAttackRoute.CLOSE);
        Counters counters = new Counters();

        AgentGrindRangedEngagementService.Result result = AgentGrindRangedEngagementService.engage(
                entry,
                agent,
                new Point(100, 100),
                new Point(150, 100),
                target,
                target.getPosition(),
                plan,
                null,
                hooks(counters, false, false, true, WeaponType.SWORD1H, false));

        assertTrue(result.consumedTick());
        assertEquals(1, counters.jump.get());
        assertEquals(120, counters.jumpDx);
        assertEquals(0, counters.idleOnGround.get());
    }

    @Test
    void initiatesJumpShotForJumpableGunTarget() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = monsterAt(220, 80);
        AgentAttackPlan plan = plan(target, AgentAttackRoute.RANGED);
        Counters counters = new Counters();

        AgentGrindRangedEngagementService.Result result = AgentGrindRangedEngagementService.engage(
                entry, agent, new Point(100, 100), new Point(150, 100), target,
                target.getPosition(), plan, null,
                hooks(counters, false, false, true, WeaponType.GUN, true));

        assertTrue(result.consumedTick());
        assertEquals(1, counters.jump.get());
        assertEquals(120, counters.jumpDx);
    }

    @Test
    void crowdedGunStartsPhysicsProbedBackwardJumpBeforeCloseAttack() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = monsterAt(140, 100);
        AgentAttackPlan plan = plan(target, AgentAttackRoute.CLOSE);
        Counters counters = new Counters();
        Point landing = new Point(25, 100);

        AgentGrindRangedEngagementService.Result result = AgentGrindRangedEngagementService.engage(
                entry, agent, new Point(100, 100), new Point(140, 100), target,
                target.getPosition(), plan, null,
                hooks(counters, true, true, false, WeaponType.GUN, true,
                        landing, null));

        assertTrue(result.consumedTick());
        assertEquals(landing, result.targetPos());
        assertEquals(1, counters.fixedArcJump.get());
        assertEquals(-75, counters.fixedArcJumpDx);
        assertEquals(0, counters.attack.get());
    }

    @Test
    void successfulGroundedRangedShotCommitsSafeFiringPosition() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = monsterAt(220, 100);
        AgentAttackPlan plan = plan(target, AgentAttackRoute.RANGED);
        Counters counters = new Counters();
        Point firingPosition = new Point(20, 100);

        AgentGrindRangedEngagementService.Result result = AgentGrindRangedEngagementService.engage(
                entry, agent, new Point(100, 100), new Point(220, 100), target,
                target.getPosition(), plan, null,
                hooks(counters, true, true, false, WeaponType.GUN, true,
                        null, firingPosition));

        assertFalse(result.consumedTick());
        assertEquals(firingPosition, result.targetPos());
        assertEquals(firingPosition, result.crossRegionRetreatPos());
        assertEquals(1, counters.attack.get());
    }

    @Test
    void airborneGunWaitsForProjectileRangeInsteadOfWackingMidJump() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentMovementStateRuntime.setInAir(entry, true);
        Monster target = monsterAt(140, 100);
        AgentAttackPlan plan = plan(target, AgentAttackRoute.CLOSE);
        Counters counters = new Counters();

        AgentGrindRangedEngagementService.Result result = AgentGrindRangedEngagementService.engage(
                entry, agent, new Point(100, 100), new Point(140, 100), target,
                target.getPosition(), plan, null,
                hooks(counters, true, true, false, WeaponType.GUN, true));

        assertFalse(result.consumedTick());
        assertEquals(0, counters.attack.get());
    }

    private static AgentGrindRangedEngagementService.Hooks hooks(Counters counters,
                                                                boolean inRange,
                                                                boolean canUseAttack,
                                                                boolean jumpable) {
        return hooks(counters, inRange, canUseAttack, jumpable, WeaponType.CLAW, true);
    }

    private static AgentGrindRangedEngagementService.Hooks hooks(Counters counters,
                                                                boolean inRange,
                                                                boolean canUseAttack,
                                                                boolean jumpable,
                                                                WeaponType resolvedWeaponType,
                                                                boolean rangedAmmoWeapon) {
        return hooks(counters, inRange, canUseAttack, jumpable, resolvedWeaponType,
                rangedAmmoWeapon, null, null);
    }

    private static AgentGrindRangedEngagementService.Hooks hooks(Counters counters,
                                                                boolean inRange,
                                                                boolean canUseAttack,
                                                                boolean jumpable,
                                                                WeaponType resolvedWeaponType,
                                                                boolean rangedAmmoWeapon,
                                                                Point crowdJumpTarget,
                                                                Point firingPosition) {
        return new AgentGrindRangedEngagementService.Hooks(
                agent -> resolvedWeaponType,
                (weaponType, agentPosition, targetPosition) -> false,
                (weaponType, route, agentPosition, targetPosition) -> false,
                (entry, agentPosition, targetPosition) -> null,
                (attackPlan, agent, target) -> inRange,
                (entry, agent, target, attackPlan, agentPosition) -> null,
                (grounded, weaponType, route) -> canUseAttack,
                (entry, agent, attackPlan) -> {
                    counters.attack.incrementAndGet();
                    AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, 100);
                    return AgentAttackTransactionResult.committed(
                            100000000, attackPlan.skillId, List.of(), 1, 0, 1L);
                },
                ignored -> rangedAmmoWeapon,
                (entry, agent, weaponType, agentPosition, targetPosition) -> crowdJumpTarget,
                (movementProfile, weaponType, route, agentPosition, targetPosition, maxJumpHeight) -> jumpable,
                movementProfile -> 100.0f,
                (entry, agent, dx) -> {
                    counters.jump.incrementAndGet();
                    counters.jumpDx = dx;
                },
                (entry, agent, dx) -> {
                    counters.fixedArcJump.incrementAndGet();
                    counters.fixedArcJumpDx = dx;
                },
                (entry, agentPosition, targetPosition) -> firingPosition,
                (entry, agent) -> counters.idleOnGround.incrementAndGet(),
                entry -> counters.broadcast.incrementAndGet());
    }

    private static AgentAttackPlan plan(Monster target, AgentAttackRoute route) {
        return new AgentAttackPlan(
                0,
                0,
                1,
                new Rectangle(0, 0, 300, 300),
                List.of(target),
                route,
                0,
                1,
                1,
                0,
                6,
                0,
                0,
                WeaponType.CLAW);
    }

    private static Monster monsterAt(int x, int y) {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(x, y));
        return monster;
    }

    private static final class Counters {
        private final AtomicInteger attack = new AtomicInteger();
        private final AtomicInteger jump = new AtomicInteger();
        private final AtomicInteger fixedArcJump = new AtomicInteger();
        private final AtomicInteger idleOnGround = new AtomicInteger();
        private final AtomicInteger broadcast = new AtomicInteger();
        private int jumpDx;
        private int fixedArcJumpDx;
    }
}
