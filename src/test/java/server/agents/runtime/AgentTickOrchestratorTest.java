package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentTickCadenceStateRuntime;
import server.agents.integration.AgentTickFailureStateRuntime;
import server.agents.integration.AgentTickStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTickOrchestratorTest {
    @Test
    void guardedTickRunsCoreAndClearsPreviousFailures() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentTickFailureStateRuntime.recordFailure(entry, 1_000L, 10_000L);
        int[] coreRuns = {0};
        int[] failures = {0};

        AgentTickOrchestrator.runGuardedTick(
                entry,
                100,
                200,
                (tickEntry, leaderId, agentId) -> {
                    assertSame(entry, tickEntry);
                    assertEquals(100, leaderId);
                    assertEquals(200, agentId);
                    coreRuns[0]++;
                },
                (tickEntry, leaderId, agentId, failure) -> failures[0]++);

        assertEquals(1, coreRuns[0]);
        assertEquals(0, failures[0]);
        assertFalse(AgentTickFailureStateRuntime.hasFailures(entry));
    }

    @Test
    void guardedTickRoutesFailureToHandler() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        RuntimeException failure = new RuntimeException("boom");
        int[] failures = {0};

        AgentTickOrchestrator.runGuardedTick(
                entry,
                100,
                200,
                (tickEntry, leaderId, agentId) -> {
                    throw failure;
                },
                (tickEntry, leaderId, agentId, handledFailure) -> {
                    assertSame(entry, tickEntry);
                    assertEquals(100, leaderId);
                    assertEquals(200, agentId);
                    assertSame(failure, handledFailure);
                    failures[0]++;
                });

        assertEquals(1, failures[0]);
    }

    @Test
    void prepareTickRecordsNonAiTickUntilCadenceIsDue() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);

        boolean runAiTick = AgentTickOrchestrator.prepareTick(entry, 100, 250, 1_000L);

        assertFalse(runAiTick);
        assertFalse(AgentTickStateRuntime.lastTickWasAi(entry));
        assertEquals(1_000L, AgentTickStateRuntime.lastTickAtMs(entry));
        assertEquals(100, AgentTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
    }

    @Test
    void prepareTickRecordsAiTickAndCarriesCadenceRemainder() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentTickCadenceStateRuntime.setAiTickAccumulatorMs(entry, 200);

        boolean runAiTick = AgentTickOrchestrator.prepareTick(entry, 100, 250, 2_000L);

        assertTrue(runAiTick);
        assertTrue(AgentTickStateRuntime.lastTickWasAi(entry));
        assertEquals(2_000L, AgentTickStateRuntime.lastTickAtMs(entry));
        assertEquals(50, AgentTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
    }
}
