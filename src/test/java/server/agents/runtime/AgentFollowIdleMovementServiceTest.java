package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentModeStateRuntime;
import server.agents.integration.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentMovementStateRuntime;
import server.agents.integration.AgentMovementStuckStateRuntime;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentOwnerMotionStateRuntime;
import server.agents.integration.AgentTickStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFollowIdleMovementServiceTest {
    @Test
    void parksFollowMovementBetweenPeriodicChecks() {
        Character agent = agentAt(new Point(80, 100));
        AgentRuntimeEntry entry = entry(agent);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStuckStateRuntime.addStuckMs(entry, 500);
        AgentMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(70, 100));

        assertTrue(AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry, agent, new Point(100, 100), 1_000L, 50, 10));
        assertEquals("idle-fast", AgentNavigationDebugStateRuntime.lastDecision(entry));
        assertEquals(0, AgentMovementStuckStateRuntime.stuckMs(entry));
        assertEquals(2_000L, AgentTickStateRuntime.nextFollowIdleMovementCheckAtMs(entry));

        assertTrue(AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry, agent, new Point(100, 100), 1_500L, 50, 10));
        assertFalse(AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry, agent, new Point(100, 100), 2_000L, 50, 10));
        assertEquals(3_000L, AgentTickStateRuntime.nextFollowIdleMovementCheckAtMs(entry));
    }

    @Test
    void rejectsWhenNotParkedNearTarget() {
        Character agent = agentAt(new Point(0, 100));
        AgentRuntimeEntry entry = entry(agent);
        AgentModeStateRuntime.setFollowing(entry, true);

        assertFalse(AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry, agent, new Point(100, 100), 1_000L, 50, 10));
    }

    @Test
    void rejectsWhenMovementOrOwnerMotionRequiresNormalResolution() {
        Character agent = agentAt(new Point(80, 100));
        AgentRuntimeEntry entry = entry(agent);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementVelocity(entry, 1, 0);

        assertFalse(AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry, agent, new Point(100, 100), 1_000L, 50, 10));

        AgentMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, new Point(10, 10));
        AgentOwnerMotionStateRuntime.updateObservedOwnerStep(entry, new Point(11, 10));

        assertFalse(AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry, agent, new Point(100, 100), 1_000L, 50, 10));
    }

    @Test
    void rejectsWhenOtherModesOrTargetsAreActive() {
        Character agent = agentAt(new Point(80, 100));
        AgentRuntimeEntry entry = entry(agent);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentModeStateRuntime.setGrinding(entry, true);

        assertFalse(AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry, agent, new Point(100, 100), 1_000L, 50, 10));

        AgentModeStateRuntime.setGrinding(entry, false);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 100), false);

        assertFalse(AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry, agent, new Point(100, 100), 1_000L, 50, 10));
    }

    private static AgentRuntimeEntry entry(Character agent) {
        return new AgentRuntimeEntry(agent, mock(Character.class), null);
    }

    private static Character agentAt(Point position) {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(position));
        return agent;
    }
}
