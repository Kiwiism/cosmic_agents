package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.operations.events.AgentMobDamagedEvent;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentRouteBlockerPolicyTest {
    @Test
    void corridorIncludesMobsBetweenAgentAndDestination() {
        assertTrue(AgentCombatTargetRuntime.insideRouteCorridor(
                new Point(0, 0), new Point(200, 0), new Point(80, 35), 40));
    }

    @Test
    void corridorRejectsMobsBehindBeyondOrFarFromRoute() {
        Point start = new Point(0, 0);
        Point end = new Point(200, 0);

        assertFalse(AgentCombatTargetRuntime.insideRouteCorridor(
                start, end, new Point(-1, 0), 40));
        assertFalse(AgentCombatTargetRuntime.insideRouteCorridor(
                start, end, new Point(201, 0), 40));
        assertFalse(AgentCombatTargetRuntime.insideRouteCorridor(
                start, end, new Point(80, 41), 40));
    }

    @Test
    void killBudgetOnlyCountsTheSelectedRouteBlocker() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentCombatDirectiveRuntime.assignPreferences(entry, Set.of(100), Set.of(200));
        AgentCombatDirectiveRuntime.state(entry).selected(
                1, -1, 200, AgentCombatCandidateClass.INCIDENTAL,
                AgentCombatDecisionReason.ROUTE_BLOCKER, 1_000L);
        AgentRouteBlockerState blocker =
                entry.capabilityStates().require(AgentRouteBlockerState.STATE_KEY);
        blocker.canInterrupt(new Point(100, 0), 1_000L);
        AgentCombatTacticalEventListener listener = new AgentCombatTacticalEventListener(entry);

        listener.onAgentEvent(new AgentMobKilledEvent(
                1, 1_001L, 1, 201, 2_001, 1, ""));
        assertEquals(AgentCombatPolicyConfig.routeBlockerMaxKills(),
                blocker.snapshot(1_001L).availableKills());

        listener.onAgentEvent(new AgentMobKilledEvent(
                1, 1_002L, 1, 200, 2_002, 1, ""));
        assertEquals(AgentCombatPolicyConfig.routeBlockerMaxKills() - 1,
                blocker.snapshot(1_002L).availableKills());
    }

    @Test
    void killBudgetRefillsOneTokenAtATimeWhileTravelContinues() {
        AgentRouteBlockerState blocker = new AgentRouteBlockerState();
        long startedAt = 1_000L;

        for (int index = 0; index < AgentCombatPolicyConfig.routeBlockerMaxKills(); index++) {
            Point waypoint = new Point(100 + index * 50, 0);
            assertTrue(blocker.canInterrupt(waypoint, startedAt));
            blocker.killed(startedAt);
        }

        Point nextMapWaypoint = new Point(20, 0);
        assertFalse(blocker.canInterrupt(nextMapWaypoint, startedAt));
        assertFalse(blocker.canInterrupt(nextMapWaypoint,
                startedAt + AgentCombatPolicyConfig.routeBlockerRefillIntervalMs() - 1));
        assertTrue(blocker.canInterrupt(nextMapWaypoint,
                startedAt + AgentCombatPolicyConfig.routeBlockerRefillIntervalMs()));
        assertEquals(1, blocker.snapshot(
                startedAt + AgentCombatPolicyConfig.routeBlockerRefillIntervalMs()).availableKills());
    }

    @Test
    void clearCorridorRestartsInterruptionTimerWithoutRestoringKillBudget() {
        AgentRouteBlockerState blocker = new AgentRouteBlockerState();
        Point waypoint = new Point(100, 0);

        assertTrue(blocker.canInterrupt(waypoint, 1_000L));
        blocker.killed(1_001L);
        blocker.resumeTravel();

        assertTrue(blocker.canInterrupt(waypoint,
                1_000L + AgentCombatPolicyConfig.routeBlockerTimeoutMs() + 1));
        assertEquals(AgentCombatPolicyConfig.routeBlockerMaxKills() - 1, blocker.snapshot(
                1_000L + AgentCombatPolicyConfig.routeBlockerTimeoutMs() + 1).availableKills());
    }

    @Test
    void acceptedDamageRenewsInactivityWindowButNotHardDeadline() {
        AgentRouteBlockerState blocker = new AgentRouteBlockerState();
        Point waypoint = new Point(100, 0);
        long startedAt = 1_000L;

        assertTrue(blocker.canInterrupt(waypoint, startedAt));
        long nearInactivityDeadline = startedAt
                + AgentCombatPolicyConfig.routeBlockerTimeoutMs() - 1L;
        blocker.damaged(nearInactivityDeadline);
        assertTrue(blocker.canInterrupt(waypoint,
                startedAt + AgentCombatPolicyConfig.routeBlockerTimeoutMs() + 1L));

        long hardDeadline = startedAt + AgentCombatPolicyConfig.routeBlockerHardTimeoutMs();
        blocker.damaged(hardDeadline - 1L);
        assertFalse(blocker.canInterrupt(waypoint, hardDeadline));
    }

    @Test
    void tacticalListenerRenewsBlockerOnlyForSelectedMobDamage() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentCombatDirectiveRuntime.assignPreferences(entry, Set.of(100), Set.of(200));
        AgentCombatDirectiveRuntime.state(entry).selected(
                1, -1, 200, AgentCombatCandidateClass.INCIDENTAL,
                AgentCombatDecisionReason.ROUTE_BLOCKER, 1_000L);
        AgentRouteBlockerState blocker =
                entry.capabilityStates().require(AgentRouteBlockerState.STATE_KEY);
        blocker.canInterrupt(new Point(100, 0), 1_000L);
        AgentCombatTacticalEventListener listener = new AgentCombatTacticalEventListener(entry);

        listener.onAgentEvent(new AgentMobDamagedEvent(
                1, 2_000L, 1, 201, 2_001, 5, ""));
        assertEquals(1_000L, blocker.snapshot(2_000L).lastProgressAtMs());

        listener.onAgentEvent(new AgentMobDamagedEvent(
                1, 2_100L, 1, 200, 2_002, 5, ""));
        assertEquals(2_100L, blocker.snapshot(2_100L).lastProgressAtMs());
    }
}
