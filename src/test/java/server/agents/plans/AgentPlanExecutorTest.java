package server.agents.plans;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.objectives.AgentObjectiveSource;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentForegroundPauseRuntime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPlanExecutorTest {
    @Test
    void executesSequentialStepsAndPreservesChainAcrossAvailableSuccessor() {
        CompletingStepExecutor steps = new CompletingStepExecutor();
        AgentPlanDefinition first = plan("first", List.of(step("one", 0), step("two", 0)),
                List.of(new AgentPlanDefinition.Successor(
                        "second", AgentPlanDefinition.Outcome.SUCCEEDED,
                        AgentPlanDefinition.Activation.AVAILABLE, 0L)));
        AgentPlanDefinition second = plan("second", List.of(step("three", 0)), List.of());
        AgentPlanExecutor executor = new AgentPlanExecutor(
                new AgentPlanRepository(List.of(first, second)),
                new AgentPlanStepExecutorRegistry(List.of(steps)));
        Character agent = agent(71);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        assertTrue(executor.start(entry, agent, "first", AgentPlanStartRequest.EMPTY, 1_000L));
        String chainId = entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY).chainId();
        assertTrue(executor.tick(entry, agent, 1_001L));
        assertTrue(executor.tick(entry, agent, 1_002L));

        AgentPlanSessionState state =
                entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        assertEquals(AgentPlanExecutionStatus.SUCCEEDED, state.status());
        assertEquals(List.of("second"), state.availableSuccessorPlanIds());
        assertFalse(state.active());

        assertTrue(executor.startAvailableSuccessor(
                entry, agent, "second", AgentPlanStartRequest.EMPTY, 1_003L));
        assertEquals(chainId, state.chainId());
        assertTrue(executor.tick(entry, agent, 1_004L));
        assertEquals(AgentPlanExecutionStatus.SUCCEEDED, state.status());
        assertEquals(3, steps.starts.get());
    }

    @Test
    void appliesSharedRetryBudgetBeforeSucceeding() {
        RetryStepExecutor steps = new RetryStepExecutor(2);
        AgentPlanDefinition retrying = plan("retrying", List.of(step("retry", 2)), List.of());
        AgentPlanExecutor executor = new AgentPlanExecutor(
                new AgentPlanRepository(List.of(retrying)),
                new AgentPlanStepExecutorRegistry(List.of(steps)));
        Character agent = agent(72);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        assertTrue(executor.start(entry, agent, "retrying", AgentPlanStartRequest.EMPTY, 1_000L));
        assertTrue(executor.tick(entry, agent, 1_000L));
        assertTrue(executor.tick(entry, agent, 1_999L));
        assertTrue(executor.tick(entry, agent, 2_000L));
        assertTrue(executor.tick(entry, agent, 4_000L));

        AgentPlanSessionState state =
                entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        assertEquals(AgentPlanExecutionStatus.SUCCEEDED, state.status());
        assertEquals(3, steps.starts.get());
    }

    @Test
    void reattachesTheCurrentRegisteredStepFromAUniversalCheckpoint() {
        ActiveStepExecutor steps = new ActiveStepExecutor();
        AgentPlanDefinition durable = plan("durable", List.of(step("active", 0)), List.of());
        AgentPlanExecutor executor = new AgentPlanExecutor(
                new AgentPlanRepository(List.of(durable)),
                new AgentPlanStepExecutorRegistry(List.of(steps)));
        Character agent = agent(73);
        AgentRuntimeEntry original = new AgentRuntimeEntry(agent, null, null);

        assertTrue(executor.start(original, agent, "durable", AgentPlanStartRequest.EMPTY, 1_000L));
        assertTrue(executor.tick(original, agent, 1_001L));
        AgentPlanCheckpoint checkpoint = original.capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY)
                .pendingCheckpoint(73, 1_002L);

        AgentRuntimeEntry restored = new AgentRuntimeEntry(agent, null, null);
        restored.capabilityStates().require(AgentPlanSessionState.STATE_KEY).restore(checkpoint);

        assertTrue(executor.reattach(restored, agent, 1_003L));
        assertEquals(1, steps.reattachments.get());
        assertTrue(restored.capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY).active());
        assertTrue(executor.tick(restored, agent, 1_004L));
        assertEquals(1, steps.ticks.get());
        assertEquals(1, steps.reattachments.get());
    }

    @Test
    void normalUniversalStepStartIsMarkedAttachedAndContinuesTicking() {
        ActiveStepExecutor steps = new ActiveStepExecutor();
        AgentPlanDefinition durable = plan("live", List.of(step("active", 0)), List.of());
        AgentPlanExecutor executor = new AgentPlanExecutor(
                new AgentPlanRepository(List.of(durable)),
                new AgentPlanStepExecutorRegistry(List.of(steps)));
        Character agent = agent(75);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        assertTrue(executor.start(entry, agent, "live", AgentPlanStartRequest.EMPTY, 1_000L));
        assertTrue(executor.tick(entry, agent, 1_001L));
        AgentPlanSessionState session =
                entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        String attachmentKey = AgentPlanAttachmentKey.current(session);

        assertFalse(entry.capabilityStates().require(AgentPlanAttachmentState.STATE_KEY)
                .ready(attachmentKey, Long.MAX_VALUE));
        assertTrue(executor.tick(entry, agent, 1_002L));
        assertTrue(executor.tick(entry, agent, 1_003L));
        assertEquals(1, steps.starts.get());
        assertEquals(2, steps.ticks.get());
        assertEquals(0, steps.reattachments.get());
    }

    @Test
    void foregroundPauseStopsStepTicksAndPreservesTheEffectiveClock() {
        ActiveStepExecutor steps = new ActiveStepExecutor();
        AgentPlanDefinition durable = plan("pauseable", List.of(step("active", 0)), List.of());
        AgentPlanExecutor executor = new AgentPlanExecutor(
                new AgentPlanRepository(List.of(durable)),
                new AgentPlanStepExecutorRegistry(List.of(steps)));
        Character agent = agent(74);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        assertTrue(executor.start(entry, agent, "pauseable", AgentPlanStartRequest.EMPTY, 100L));
        assertTrue(executor.tick(entry, agent, 100L));
        AgentForegroundPauseRuntime.pause(entry, "town-life", 110L);
        assertTrue(executor.tick(entry, agent, 500L));
        assertEquals(0, steps.ticks.get());

        AgentForegroundPauseRuntime.resume(entry, "town-life", 500L);
        assertTrue(executor.tick(entry, agent, 501L));
        assertEquals(1, steps.ticks.get());
        assertEquals(111L, steps.lastTickNowMs);
    }

    @Test
    void activeUniversalStepYieldsToItsChildCapability() {
        YieldingStepExecutor steps = new YieldingStepExecutor();
        AgentPlanDefinition durable = plan("yielding", List.of(step("active", 0)), List.of());
        AgentPlanExecutor executor = new AgentPlanExecutor(
                new AgentPlanRepository(List.of(durable)),
                new AgentPlanStepExecutorRegistry(List.of(steps)));
        Character agent = agent(76);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        assertTrue(executor.start(entry, agent, "yielding", AgentPlanStartRequest.EMPTY, 100L));
        assertTrue(executor.tick(entry, agent, 101L));
        assertFalse(executor.tick(entry, agent, 102L));
        assertTrue(entry.capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY).active());
    }

    private static AgentPlanDefinition plan(
            String id,
            List<AgentPlanDefinition.Step> steps,
            List<AgentPlanDefinition.Successor> successors) {
        return new AgentPlanDefinition(
                1, id, "1", id, "executable",
                new AgentPlanDefinition.ObjectivePolicy(
                        "test", 1, Long.MAX_VALUE, 0,
                        AgentObjectiveSource.PROGRESSION_POLICY, "test-v1",
                        AgentPlanDefinition.Registration.STEP),
                List.of(), steps, List.of(), successors);
    }

    private static AgentPlanDefinition.Step step(String id, int retries) {
        return new AgentPlanDefinition.Step(
                id, "test-operation", List.of("test"), Map.of(), 0L, retries);
    }

    private static Character agent(int id) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getName()).thenReturn("PlanAgent" + id);
        return agent;
    }

    private static class CompletingStepExecutor implements AgentPlanStepExecutor {
        protected final AtomicInteger starts = new AtomicInteger();

        @Override
        public String operation() {
            return "test-operation";
        }

        @Override
        public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
            starts.incrementAndGet();
            return AgentPlanStepExecution.terminal(
                    AgentPlanExecutionStatus.SUCCEEDED, "done");
        }

        @Override
        public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
            return AgentPlanStepExecution.active(true);
        }

        @Override
        public void cancel(AgentPlanExecutionContext context) {
        }
    }

    private static final class RetryStepExecutor extends CompletingStepExecutor {
        private final int failures;

        private RetryStepExecutor(int failures) {
            this.failures = failures;
        }

        @Override
        public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
            int attempt = super.starts.incrementAndGet();
            return attempt <= failures
                    ? AgentPlanStepExecution.terminal(
                    AgentPlanExecutionStatus.FAILED, "retry " + attempt)
                    : AgentPlanStepExecution.terminal(
                    AgentPlanExecutionStatus.SUCCEEDED, "done");
        }
    }

    private static final class ActiveStepExecutor extends CompletingStepExecutor {
        private final AtomicInteger ticks = new AtomicInteger();
        private final AtomicInteger reattachments = new AtomicInteger();
        private long lastTickNowMs;

        @Override
        public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
            super.starts.incrementAndGet();
            return AgentPlanStepExecution.active(true);
        }

        @Override
        public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
            ticks.incrementAndGet();
            lastTickNowMs = context.nowMs();
            return AgentPlanStepExecution.active(true);
        }

        @Override
        public AgentPlanStepExecution reattach(AgentPlanExecutionContext context) {
            reattachments.incrementAndGet();
            return AgentPlanStepExecution.active(true);
        }
    }

    private static final class YieldingStepExecutor extends CompletingStepExecutor {
        @Override
        public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
            super.starts.incrementAndGet();
            return AgentPlanStepExecution.active(true);
        }

        @Override
        public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
            return AgentPlanStepExecution.active(false);
        }
    }
}
