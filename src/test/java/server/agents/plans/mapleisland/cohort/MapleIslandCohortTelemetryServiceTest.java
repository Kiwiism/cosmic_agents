package server.agents.plans.mapleisland.cohort;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.runtime.AgentCapabilityJournalEvent;
import server.agents.capabilities.runtime.AgentCapabilityJournalEventType;
import server.agents.capabilities.runtime.AgentCapabilityReasonCode;
import server.agents.plans.amherst.AmherstPlanObservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapleIslandCohortTelemetryServiceTest {
    @Test
    void milestoneTimesAreRelativeToEachAgentsOwnSpawn() {
        MapleIslandCohortTelemetryService service = new MapleIslandCohortTelemetryService();
        Character first = agent(1, "First");
        Character second = agent(2, "Second");
        service.beginSession("session", MapleIslandCohortRealismMode.LIGHT);
        service.register("session", MapleIslandCohortRealismMode.LIGHT, first, 1_000L);
        service.register("session", MapleIslandCohortRealismMode.LIGHT, second, 10_000L);

        service.recordMapChanged(1, 1_000_000, 3_000L);
        service.recordMapChanged(2, 1_000_000, 13_000L);
        service.recordMapChanged(1, 2_000_000, 7_000L);
        service.recordMapChanged(2, 2_000_000, 18_000L);

        var snapshot = service.snapshot("session", 20_000L);
        assertEquals(2_500L, snapshot.amherst().averageMs());
        assertEquals(2_000L, snapshot.amherst().medianMs());
        assertEquals(3_000L, snapshot.amherst().p95Ms());
        assertEquals("First", snapshot.amherst().fastestAgent());
        assertEquals("Second", snapshot.amherst().slowestAgent());
        assertEquals(7_000L, snapshot.southperry().averageMs());
    }

    @Test
    void aggregatesObjectiveDurationRecoverySignalsAndCurrentState() {
        MapleIslandCohortTelemetryService service = new MapleIslandCohortTelemetryService();
        Character agent = agent(3, "Quester");
        service.beginSession("session", MapleIslandCohortRealismMode.FULL);
        service.register("session", MapleIslandCohortRealismMode.FULL, agent, 1_000L);

        service.observe(3, observation(AmherstPlanObservation.Type.OBJECTIVE_STARTED,
                2_000L, "hunt-snails", AgentCapabilityStatus.RUNNING, null, ""));
        var retry = new AgentCapabilityJournalEvent(2_500L, AgentCapabilityJournalEventType.RETRY,
                "hunt", "attack", AgentCapabilityStatus.RETRY,
                AgentCapabilityReasonCode.RETRY_REQUESTED, "try again");
        service.observe(3, observation(AmherstPlanObservation.Type.CAPABILITY_EVENT,
                2_500L, "hunt-snails", AgentCapabilityStatus.RETRY, retry, "try again"));

        var active = service.snapshot("session", 5_000L).longestActiveObjective();
        assertNotNull(active);
        assertEquals("hunt-snails", active.objectiveId());
        assertEquals(3_000L, active.elapsedMs());

        service.observe(3, observation(AmherstPlanObservation.Type.OBJECTIVE_FINISHED,
                6_000L, "hunt-snails", AgentCapabilityStatus.SUCCESS, null,
                "authoritative live state satisfied objective"));
        service.observe(3, observation(AmherstPlanObservation.Type.PLAN_COMPLETED,
                9_000L, "", AgentCapabilityStatus.SUCCESS, null, "done"));

        var snapshot = service.snapshot("session", 9_000L);
        assertEquals(1, snapshot.retries());
        assertEquals(1, snapshot.liveStateRecoveries());
        assertEquals(4_000L, snapshot.slowestObjectives().getFirst().averageMs());
        assertEquals(8_000L, snapshot.completion().averageMs());
    }

    @Test
    void completedSessionsAreCompactedAndRetentionIsBounded() {
        MapleIslandCohortTelemetryService service = new MapleIslandCohortTelemetryService(1);
        service.beginSession("first", MapleIslandCohortRealismMode.LIGHT);
        service.register("first", MapleIslandCohortRealismMode.LIGHT, agent(4, "Reusable"), 1_000L);
        var first = service.completeSession("first", 2_000L);
        assertEquals(1, first.trackedAgents());

        service.beginSession("second", MapleIslandCohortRealismMode.FULL);
        service.register("second", MapleIslandCohortRealismMode.FULL, agent(4, "Reusable"), 3_000L);
        var second = service.completeSession("second", 4_000L);

        assertNull(service.snapshot("first", 5_000L));
        assertEquals(1, second.trackedAgents());
        assertNotNull(service.snapshot("second", 5_000L));
    }

    private static Character agent(int id, String name) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getName()).thenReturn(name);
        when(agent.getMapId()).thenReturn(10_000);
        return agent;
    }

    private static AmherstPlanObservation observation(AmherstPlanObservation.Type type,
                                                       long timestampMs,
                                                       String objectiveId,
                                                       AgentCapabilityStatus status,
                                                       AgentCapabilityJournalEvent event,
                                                       String message) {
        return new AmherstPlanObservation(type, timestampMs, objectiveId, null, status, event, message);
    }
}
