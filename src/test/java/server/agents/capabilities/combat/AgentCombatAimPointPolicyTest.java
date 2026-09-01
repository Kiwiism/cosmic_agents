package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.life.MonsterStats;

import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatAimPointPolicyTest {
    @Test
    void aimsAtNearestVisibleEdgeInsteadOfMultipartMobOrigin() {
        Rectangle leftClaw = new Rectangle(47, 119, 247, 119);
        Rectangle rightClaw = new Rectangle(629, 57, 187, 171);
        Rectangle head = new Rectangle(274, -171, 201, 168);
        Point sharedOrigin = new Point(412, 258);

        assertEquals(new Point(293, 237), AgentCombatAimPointPolicy.nearestPoint(
                new Point(360, 258), leftClaw, sharedOrigin));
        assertEquals(new Point(629, 227), AgentCombatAimPointPolicy.nearestPoint(
                new Point(560, 258), rightClaw, sharedOrigin));
        assertEquals(new Point(274, -70), AgentCombatAimPointPolicy.nearestPoint(
                new Point(220, -70), head, sharedOrigin));
    }

    @Test
    void unresolvedBoundsFallBackToAuthoredMobOrigin() {
        Point origin = new Point(412, 258);

        assertEquals(origin, AgentCombatAimPointPolicy.nearestPoint(
                new Point(360, 258), null, origin));
    }

    @Test
    void realNoFlipBalrogComponentsKeepTheirAuthoredLeftRightAndHeadBounds() {
        Character agent = mock(Character.class);
        Point origin = new Point(412, 258);
        Monster leftClaw = fixedNoFlipMob(8_830_008, origin, 4);
        Monster rightClaw = fixedNoFlipMob(8_830_009, origin, 5);
        Monster body = fixedNoFlipMob(8_830_007, origin, 4);

        when(agent.getPosition()).thenReturn(new Point(360, 258));
        assertEquals(new Point(293, 237),
                AgentCombatAimPointPolicy.aimPoint(agent, leftClaw));

        when(agent.getPosition()).thenReturn(new Point(560, 258));
        assertEquals(new Point(629, 227),
                AgentCombatAimPointPolicy.aimPoint(agent, rightClaw));

        when(agent.getPosition()).thenReturn(new Point(220, -70));
        assertEquals(new Point(274, -70),
                AgentCombatAimPointPolicy.aimPoint(agent, body));
    }

    private static Monster fixedNoFlipMob(int mobId, Point origin, int fixedStance) {
        Monster mob = mock(Monster.class);
        MonsterStats stats = mock(MonsterStats.class);
        when(mob.getId()).thenReturn(mobId);
        when(mob.getPosition()).thenReturn(origin);
        when(mob.getStats()).thenReturn(stats);
        when(stats.getFixedStance()).thenReturn(fixedStance);
        return mob;
    }
}
