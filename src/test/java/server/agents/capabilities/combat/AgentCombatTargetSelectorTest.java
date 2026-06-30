package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import server.life.Monster;
import server.life.MonsterStats;
import server.maps.MapleMap;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatTargetSelectorTest {
    @Test
    void shouldReturnEmptyWhenPrimaryDoesNotIntersectHitbox() {
        Monster primary = monster(1, new Point(500, 500), true, false);

        assertTrue(AgentCombatTargetSelector.collectTargetsInHitBox(
                primary, new Rectangle(0, 0, 100, 100), 4, List.of(primary)).isEmpty());
    }

    @Test
    void shouldKeepPrimaryFirstThenNearestEligibleSecondariesWithinCap() {
        Monster primary = monster(1, new Point(100, 100), true, false);
        Monster near = monster(2, new Point(110, 100), true, false);
        Monster far = monster(3, new Point(180, 100), true, false);
        Monster dead = monster(4, new Point(105, 100), false, false);
        Monster friendly = monster(5, new Point(106, 100), true, true);
        Monster outside = monster(6, new Point(500, 100), true, false);

        List<Monster> targets = AgentCombatTargetSelector.collectTargetsInHitBox(
                primary, new Rectangle(0, 0, 250, 250), 3,
                List.of(far, dead, primary, outside, friendly, near));

        assertEquals(List.of(primary, near, far), targets);
    }

    @Test
    void shouldRespectMaxTargets() {
        Monster primary = monster(1, new Point(100, 100), true, false);
        Monster near = monster(2, new Point(110, 100), true, false);

        assertEquals(List.of(primary), AgentCombatTargetSelector.collectTargetsInHitBox(
                primary, new Rectangle(0, 0, 250, 250), 1, List.of(near)));
    }

    @Test
    void shouldCollectOnlyHostileLivingMonstersInsideRange() {
        Monster near = monster(1, new Point(110, 100), true, false);
        Monster far = monster(2, new Point(300, 100), true, false);
        Monster deadNear = monster(3, new Point(105, 100), false, false);
        Monster friendlyNear = monster(4, new Point(106, 100), true, true);

        assertEquals(List.of(near), AgentCombatTargetSelector.aliveMonstersInRange(
                List.of(far, deadNear, near, friendlyNear), new Point(100, 100), 50 * 50));
    }

    @Test
    void shouldCollectAliveMonstersFromAgentCurrentMap() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        Monster near = monster(1, new Point(110, 100), true, false);
        Monster far = monster(2, new Point(300, 100), true, false);

        when(agent.getMap()).thenReturn(map);
        when(map.getAllMonsters()).thenReturn(List.of(far, near));

        assertEquals(List.of(near), AgentCombatTargetSelector.aliveMonstersInRange(
                agent, new Point(100, 100), 50 * 50));
    }

    @Test
    void shouldResolveForwardProjectilePrimaryToClosestIntersectingTarget() {
        Monster fallback = monster(1, new Point(500, 100), true, false);
        Monster far = monster(2, new Point(220, 100), true, false);
        Monster near = monster(3, new Point(170, 100), true, false);
        Monster friendly = monster(4, new Point(160, 100), true, true);

        assertEquals(near, AgentCombatTargetSelector.resolveEffectivePrimary(
                new Point(100, 100), fallback, new Rectangle(150, 50, 120, 100),
                List.of(far, friendly, near)));
    }

    @Test
    void shouldKeepFallbackWhenHitboxIsNotForwardOrNoCandidateMatches() {
        Monster fallback = monster(1, new Point(120, 100), true, false);
        Monster far = monster(2, new Point(500, 100), true, false);

        assertEquals(fallback, AgentCombatTargetSelector.resolveEffectivePrimary(
                new Point(100, 100), fallback, new Rectangle(80, 50, 120, 100), List.of(far)));
        assertEquals(fallback, AgentCombatTargetSelector.resolveEffectivePrimary(
                new Point(100, 100), fallback, new Rectangle(150, 50, 120, 100), List.of(far)));
    }

    @Test
    void shouldFindClosestAliveMonsterInsideExclusiveRange() {
        Monster near = monster(1, new Point(110, 100), true, false);
        Monster farther = monster(2, new Point(140, 100), true, false);
        Monster boundary = monster(3, new Point(150, 100), true, false);
        Monster dead = monster(4, new Point(105, 100), false, false);

        assertEquals(near, AgentCombatTargetSelector.findClosestAliveMonster(
                List.of(farther, boundary, dead, near), new Point(100, 100), 50 * 50));
        assertNull(AgentCombatTargetSelector.findClosestAliveMonster(
                List.of(boundary), new Point(100, 100), 50 * 50));
    }

    @Test
    void shouldResolveReachableTargetOnOppositeFacingUsingMirroredPosition() {
        Monster original = monster(1, new Point(250, 100), true, false);
        Monster mirrored = monster(2, new Point(60, 100), true, false);
        AtomicReference<Point> requestedHitBoxPosition = new AtomicReference<>();
        Rectangle hitBox = new Rectangle(40, 50, 100, 100);

        Monster result = AgentCombatTargetSelector.findReachableOnOppositeFacing(
                new Point(100, 100),
                original,
                mirroredPosition -> {
                    requestedHitBoxPosition.set(mirroredPosition);
                    return hitBox;
                },
                rectangle -> rectangle == hitBox ? mirrored : original);

        assertEquals(new Point(-50, 100), requestedHitBoxPosition.get());
        assertEquals(mirrored, result);
    }

    @Test
    void shouldRejectOppositeFacingTargetWhenInputsMissingHitboxMissingOrTargetUnchanged() {
        Monster original = monster(1, new Point(250, 100), true, false);

        assertNull(AgentCombatTargetSelector.findReachableOnOppositeFacing(
                null, original, ignored -> new Rectangle(), ignored -> original));
        assertNull(AgentCombatTargetSelector.findReachableOnOppositeFacing(
                new Point(100, 100), null, ignored -> new Rectangle(), ignored -> original));
        assertNull(AgentCombatTargetSelector.findReachableOnOppositeFacing(
                new Point(100, 100), original, ignored -> null, ignored -> original));
        assertNull(AgentCombatTargetSelector.findReachableOnOppositeFacing(
                new Point(100, 100), original, ignored -> new Rectangle(), ignored -> original));
    }

    @Test
    void shouldResolveStrikePointPrimaryUsingBasicWeaponFacingAndReach() {
        Monster fallback = monster(1, new Point(50, 100), true, false);
        Monster replacement = monster(2, new Point(60, 100), true, false);
        AtomicReference<Boolean> facingLeft = new AtomicReference<>();
        Rectangle reach = new Rectangle(0, 0, 100, 100);

        Monster result = AgentCombatTargetSelector.resolveStrikePointPrimaryByBasicWeapon(
                new Point(100, 100),
                fallback,
                AgentAttackRoute.RANGED,
                left -> {
                    facingLeft.set(left);
                    return reach;
                },
                rectangle -> rectangle == reach ? replacement : fallback);

        assertTrue(facingLeft.get());
        assertEquals(replacement, result);
    }

    @Test
    void shouldKeepStrikePointFallbackForInvalidRouteInputsOrMissingReach() {
        Monster fallback = monster(1, new Point(50, 100), true, false);

        assertEquals(fallback, AgentCombatTargetSelector.resolveStrikePointPrimaryByBasicWeapon(
                null, fallback, AgentAttackRoute.RANGED, ignored -> new Rectangle(), ignored -> null));
        assertNull(AgentCombatTargetSelector.resolveStrikePointPrimaryByBasicWeapon(
                new Point(100, 100), null, AgentAttackRoute.RANGED, ignored -> new Rectangle(), ignored -> null));
        assertEquals(fallback, AgentCombatTargetSelector.resolveStrikePointPrimaryByBasicWeapon(
                new Point(100, 100), fallback, AgentAttackRoute.MAGIC, ignored -> new Rectangle(), ignored -> null));
        assertEquals(fallback, AgentCombatTargetSelector.resolveStrikePointPrimaryByBasicWeapon(
                new Point(100, 100), fallback, AgentAttackRoute.CLOSE, ignored -> null, ignored -> null));
    }

    @Test
    void shouldCollectAliveUndeadMobsForHealRangeUpToCap() {
        Monster firstUndead = monster(1, new Point(100, 100), true, false, true);
        Monster livingNonUndead = monster(2, new Point(110, 100), true, false, false);
        Monster deadUndead = monster(3, new Point(120, 100), false, false, true);
        Monster secondUndead = monster(4, new Point(130, 100), true, false, true);

        assertEquals(List.of(firstUndead), AgentCombatTargetSelector.collectUndeadMobsInHealRange(
                new Rectangle(0, 0, 250, 250),
                List.of(firstUndead, livingNonUndead, deadUndead, secondUndead), 1));
        assertEquals(List.of(firstUndead, secondUndead), AgentCombatTargetSelector.collectUndeadMobsInHealRange(
                new Rectangle(0, 0, 250, 250),
                List.of(firstUndead, livingNonUndead, deadUndead, secondUndead), 5));
    }

    @Test
    void shouldCollectUndeadHealTargetsFromAgentCurrentMap() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        StatEffect effect = mock(StatEffect.class);
        Rectangle bounds = new Rectangle(0, 0, 250, 250);
        Monster undead = monster(1, new Point(100, 100), true, false, true);
        Monster livingNonUndead = monster(2, new Point(110, 100), true, false, false);

        when(agent.getMap()).thenReturn(map);
        when(effect.getMobCount()).thenReturn(5);
        when(map.getMapObjectsInRect(eq(bounds), anyList())).thenReturn(List.of(livingNonUndead, undead));

        assertEquals(List.of(undead), AgentCombatTargetSelector.collectUndeadMobsInHealRange(
                agent, effect, bounds));
    }

    @Test
    void shouldReturnEmptyUndeadHealTargetsWhenBoundsMissing() {
        Monster undead = monster(1, new Point(100, 100), true, false, true);

        assertEquals(List.of(), AgentCombatTargetSelector.collectUndeadMobsInHealRange(null, List.of(undead), 5));
    }

    @Test
    void shouldReturnEmptyUndeadHealTargetsFromAgentWhenBoundsMissing() {
        Character agent = mock(Character.class);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getMobCount()).thenReturn(5);

        assertEquals(List.of(), AgentCombatTargetSelector.collectUndeadMobsInHealRange(agent, effect, null));
    }

    private static Monster monster(int objectId, Point position, boolean alive, boolean friendly) {
        return monster(objectId, position, alive, friendly, false);
    }

    private static Monster monster(int objectId, Point position, boolean alive, boolean friendly, boolean undead) {
        Monster monster = mock(Monster.class);
        MonsterStats stats = mock(MonsterStats.class);
        when(stats.isFriendly()).thenReturn(friendly);
        when(stats.isUndead()).thenReturn(undead);
        when(monster.getId()).thenReturn(90_000_000 + objectId);
        when(monster.getObjectId()).thenReturn(objectId);
        when(monster.getPosition()).thenReturn(position);
        when(monster.isFacingLeft()).thenReturn(false);
        when(monster.isAlive()).thenReturn(alive);
        when(monster.getStats()).thenReturn(stats);
        return monster;
    }
}
