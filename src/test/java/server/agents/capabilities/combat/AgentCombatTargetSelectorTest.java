package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.life.MonsterStats;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private static Monster monster(int objectId, Point position, boolean alive, boolean friendly) {
        Monster monster = mock(Monster.class);
        MonsterStats stats = mock(MonsterStats.class);
        when(stats.isFriendly()).thenReturn(friendly);
        when(monster.getId()).thenReturn(90_000_000 + objectId);
        when(monster.getObjectId()).thenReturn(objectId);
        when(monster.getPosition()).thenReturn(position);
        when(monster.isFacingLeft()).thenReturn(false);
        when(monster.isAlive()).thenReturn(alive);
        when(monster.getStats()).thenReturn(stats);
        return monster;
    }
}
