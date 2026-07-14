package server.agents.runtime.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.async.AgentAsyncWorkKind;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoadSheddingRuntimeTest {
    @AfterEach
    void tearDown() {
        AgentLoadSheddingRuntime.resetForTests();
        System.clearProperty("agents.scheduler.loadShedding.enabled");
        System.clearProperty("agents.scheduler.loadShedding.maxActiveAgents");
    }

    @Test
    void strongestShardStateControlsCosmeticAsyncAndAdmissionDecisions() {
        AgentLoadSheddingRuntime.publish(0, state(AgentLoadSheddingLevel.SUPPRESS_COSMETIC));
        assertFalse(AgentLoadSheddingRuntime.permitsDialogue(false));
        assertTrue(AgentLoadSheddingRuntime.permitsDialogue(true));

        AgentLoadSheddingRuntime.publish(1, state(AgentLoadSheddingLevel.PAUSE_DEFERRED_AND_LLM));
        assertFalse(AgentLoadSheddingRuntime.permitsAsync(AgentAsyncWorkKind.LLM_NETWORK));
        assertFalse(AgentLoadSheddingRuntime.permitsAsync(AgentAsyncWorkKind.ECONOMY_ANALYSIS));
        assertTrue(AgentLoadSheddingRuntime.permitsAsync(AgentAsyncWorkKind.NAVIGATION_GRAPH));

        AgentLoadSheddingRuntime.publish(2, state(AgentLoadSheddingLevel.ADMISSION_CONTROL));
        System.setProperty("agents.scheduler.loadShedding.enabled", "true");
        AgentAdmissionDecision rejected = AgentLoadSheddingRuntime.admissionDecision(false, 1);
        assertFalse(rejected.allowed());
        assertEquals(AgentLoadSheddingReason.READY_BACKLOG, rejected.reason());
        assertTrue(AgentLoadSheddingRuntime.admissionDecision(true, 2_000).allowed());
    }

    @Test
    void configuredPopulationLimitRejectsOnlyNewSessions() {
        System.setProperty("agents.scheduler.loadShedding.enabled", "true");
        System.setProperty("agents.scheduler.loadShedding.maxActiveAgents", "2");

        assertTrue(AgentLoadSheddingRuntime.admissionDecision(false, 1).allowed());
        assertEquals(
                AgentLoadSheddingReason.POPULATION_LIMIT,
                AgentLoadSheddingRuntime.admissionDecision(false, 2).reason());
        assertTrue(AgentLoadSheddingRuntime.admissionDecision(true, 2).allowed());
    }

    private static AgentLoadSheddingState state(AgentLoadSheddingLevel level) {
        return new AgentLoadSheddingState(level, Set.of(AgentLoadSheddingReason.READY_BACKLOG), 1L, 1L);
    }
}
