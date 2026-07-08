package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentClimbStateRuntime;
import server.agents.integration.AgentModeStateRuntime;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFollowOpportunityTickServiceTest {
    @Test
    void fallsThroughWhenAgentIsNotFollowing() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        Point target = new Point(10, 20);
        AtomicInteger attacks = new AtomicInteger();

        AgentFollowOpportunityTickService.Result result = AgentFollowOpportunityTickService.tickFollowOpportunity(
                entry,
                agent,
                new Point(0, 0),
                target,
                new Point(5, 0),
                leader,
                true,
                hooks(attacks, new Point(99, 99), false));

        assertFalse(result.consumedTick());
        assertEquals(target, result.targetPos());
        assertEquals(0, attacks.get());
    }

    @Test
    void fallsThroughWhenAgentIsClimbing() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentClimbStateRuntime.setClimbingOnRope(entry, mock(server.maps.Rope.class));
        AtomicInteger attacks = new AtomicInteger();

        AgentFollowOpportunityTickService.Result result = AgentFollowOpportunityTickService.tickFollowOpportunity(
                entry,
                agent,
                new Point(0, 0),
                new Point(10, 20),
                new Point(5, 0),
                leader,
                true,
                hooks(attacks, new Point(99, 99), false));

        assertFalse(result.consumedTick());
        assertEquals(0, attacks.get());
    }

    @Test
    void runsOpportunityAttackWhenFollowingAnchorNearbyOnSameMap() {
        Character agent = mock(Character.class);
        Character leader = mock(Character.class);
        when(agent.getMapId()).thenReturn(100);
        when(leader.getMapId()).thenReturn(100);
        when(leader.getPosition()).thenReturn(new Point(40, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        Point newTarget = new Point(80, 0);
        AtomicInteger attacks = new AtomicInteger();

        AgentFollowOpportunityTickService.Result result = AgentFollowOpportunityTickService.tickFollowOpportunity(
                entry,
                agent,
                new Point(0, 0),
                new Point(10, 20),
                new Point(5, 0),
                leader,
                true,
                hooks(attacks, newTarget, true));

        assertTrue(result.consumedTick());
        assertEquals(newTarget, result.targetPos());
        assertEquals(1, attacks.get());
    }

    private static AgentFollowOpportunityTickService.Hooks hooks(AtomicInteger attacks,
                                                                 Point targetPos,
                                                                 boolean consumed) {
        return new AgentFollowOpportunityTickService.Hooks(
                (entry, agent, agentPosition, currentTargetPosition, followTargetPosition) -> {
                    attacks.incrementAndGet();
                    return new AgentFollowOpportunityTickService.Result(consumed, targetPos);
                },
                10);
    }
}
