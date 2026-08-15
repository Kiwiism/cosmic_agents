package server.agents.plans;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.townlife.AgentTownLifeExitRequest;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.objectives.AgentObjectiveSource;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.townlife.AgentTownLifeVisitLeaseRuntime;

import java.awt.Point;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTownLifeVisitPlanStepExecutorTest {
    @Test
    void boundedVisitPausesThenCompletesTheOwningPlanStepExactlyOnce() {
        Character agent = localAgent();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentPlanDefinition plan = plan();
        AgentPlanSessionState session = entry.capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY);
        session.start(plan, "chain-1", AgentPlanStartRequest.EMPTY, 1_000L);
        session.stepStarted(1_000L);
        AgentPlanExecutionContext context = new AgentPlanExecutionContext(
                entry, agent, plan, plan.steps().getFirst(), AgentPlanStartRequest.EMPTY, 1_000L);
        AgentTownLifeVisitPlanStepExecutor executor = new AgentTownLifeVisitPlanStepExecutor();

        try {
            AgentPlanStepExecution started = executor.start(context);
            assertEquals(AgentPlanExecutionStatus.ACTIVE, started.status());
            assertFalse(started.consumed());
            assertTrue(AgentTownLifeRuntime.active(entry));
            assertTrue(AgentTownLifeVisitLeaseRuntime.active(entry));

            AgentTownLifeState townState = entry.capabilityStates()
                    .require(AgentTownLifeState.STATE_KEY);
            AgentTownLifeRuntime.requestExit(entry, agent,
                    AgentTownLifeExitRequest.graceful(
                            townState.sessionHandle(agent.getId()), "visit complete", 2_000L, 5_000L));
            AgentPlanExecutionContext resumed = new AgentPlanExecutionContext(
                    entry, agent, plan, plan.steps().getFirst(), AgentPlanStartRequest.EMPTY, 2_001L);

            AgentPlanStepExecution completed = executor.tick(resumed);
            assertEquals(AgentPlanExecutionStatus.SUCCEEDED, completed.status());
            assertFalse(AgentTownLifeVisitLeaseRuntime.active(entry));
        } finally {
            executor.cancel(context);
            AgentTownLifeRuntime.forceStop(entry, agent, "test cleanup");
            AgentTownLifeVisitLeaseRuntime.clear(entry, agent);
        }
    }

    @Test
    void reattachmentDoesNotDuplicateAVisitThatAlreadyReachedItsTerminalBoundary() {
        Character agent = localAgent();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentPlanDefinition plan = plan();
        AgentPlanSessionState session = entry.capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY);
        session.start(plan, "chain-2", AgentPlanStartRequest.EMPTY, 1_000L);
        session.stepStarted(1_000L);
        AgentPlanExecutionContext context = new AgentPlanExecutionContext(
                entry, agent, plan, plan.steps().getFirst(), AgentPlanStartRequest.EMPTY, 3_000L);

        AgentPlanStepExecution result = new AgentTownLifeVisitPlanStepExecutor().reattach(context);

        assertEquals(AgentPlanExecutionStatus.SUCCEEDED, result.status());
        assertFalse(AgentTownLifeRuntime.active(entry));
    }

    private static Character localAgent() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(711);
        when(agent.getName()).thenReturn("PlanTownLifeTest");
        when(agent.getMapId()).thenReturn(104000000);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        when(agent.getChair()).thenReturn(-1);
        return agent;
    }

    private static AgentPlanDefinition plan() {
        AgentPlanDefinition.Step step = new AgentPlanDefinition.Step(
                "town-break", AgentTownLifeVisitPlanStepExecutor.OPERATION,
                List.of("townlife.local"),
                Map.of("durationMs", 10_000L, "gracefulTimeoutMs", 5_000L),
                30_000L, 0);
        return new AgentPlanDefinition(
                1, "townlife-test-plan", "1", "TownLife test plan", "executable",
                new AgentPlanDefinition.ObjectivePolicy(
                        "test", 1, 0L, 0, AgentObjectiveSource.QUEST_PLAN,
                        "townlife-test-v1", AgentPlanDefinition.Registration.STEP),
                List.of(), List.of(step), List.of(), List.of());
    }
}
