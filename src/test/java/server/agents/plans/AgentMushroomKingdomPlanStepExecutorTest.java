package server.agents.plans;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMushroomKingdomPlanStepExecutorTest {
    private final AgentMushroomKingdomPlanStepExecutor executor =
            new AgentMushroomKingdomPlanStepExecutor();

    @Test
    void admitsOnlyLevel30Through38ExplorerSecondJobs() {
        assertEquals(AgentPlanExecutionStatus.ACTIVE, start(Job.FIGHTER, 30).status());
        assertEquals(AgentPlanExecutionStatus.ACTIVE, start(Job.GUNSLINGER, 38).status());
        assertEquals(AgentPlanExecutionStatus.BLOCKED, start(Job.FIGHTER, 39).status());
        assertEquals(AgentPlanExecutionStatus.BLOCKED, start(Job.WARRIOR, 30).status());
        assertEquals(AgentPlanExecutionStatus.BLOCKED, start(Job.CRUSADER, 38).status());
    }

    @Test
    void rejectedJobReportsTheSupportedScope() {
        AgentPlanStepExecution result = start(Job.DAWNWARRIOR2, 30);

        assertEquals(AgentPlanExecutionStatus.BLOCKED, result.status());
        assertTrue(result.reason().contains("Explorer second jobs"));
    }

    private AgentPlanStepExecution start(Job job, int level) {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(job);
        when(agent.getLevel()).thenReturn(level);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        return executor.start(new AgentPlanExecutionContext(
                entry, agent, null, null, AgentPlanStartRequest.EMPTY, 1_000L));
    }
}
