package server.agents.runtime;

import server.agents.plans.AgentScriptTaskExecutionService;
import server.agents.plans.AgentScriptTaskTickService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.plans.AgentTask;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentScriptTaskRuntimeTest {
    @Test
    void defaultTickUsesMovementStopDistance() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        AgentTask task = AgentTask.stop();
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentScriptTaskTickService> tickService = mockStatic(AgentScriptTaskTickService.class);
             MockedStatic<AgentScriptTaskExecutionService> executionService = mockStatic(AgentScriptTaskExecutionService.class)) {
            tickService.when(() -> AgentScriptTaskTickService.tick(
                            eq(entry),
                            any(),
                            any()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        java.util.function.BiPredicate<AgentRuntimeEntry, AgentTask> isComplete = invocation.getArgument(2);
                        isComplete.test(entry, task);
                        calls.add("tick");
                        return null;
                    });
            executionService.when(() -> AgentScriptTaskExecutionService.isComplete(
                            eq(entry),
                            eq(task),
                            eq(AgentMovementPhysicsConfig.configuredStopDist())))
                    .thenAnswer(invocation -> {
                        calls.add("complete");
                        return true;
                    });

            AgentScriptTaskRuntime.tick(entry);

            org.junit.jupiter.api.Assertions.assertEquals(List.of("complete", "tick"), calls);
        }
    }

    @Test
    void delegatesTickThroughAgentScriptTaskServices() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        AgentTask task = AgentTask.stop();
        List<String> calls = new ArrayList<>();

        try (MockedStatic<AgentScriptTaskTickService> tickService = mockStatic(AgentScriptTaskTickService.class);
             MockedStatic<AgentScriptTaskExecutionService> executionService = mockStatic(AgentScriptTaskExecutionService.class)) {
            tickService.when(() -> AgentScriptTaskTickService.tick(
                            eq(entry),
                            any(),
                            any()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        java.util.function.BiConsumer<AgentRuntimeEntry, AgentTask> startTask = invocation.getArgument(1);
                        @SuppressWarnings("unchecked")
                        java.util.function.BiPredicate<AgentRuntimeEntry, AgentTask> isComplete = invocation.getArgument(2);
                        startTask.accept(entry, task);
                        isComplete.test(entry, task);
                        calls.add("tick");
                        return null;
                    });
            executionService.when(() -> AgentScriptTaskExecutionService.start(entry, task))
                    .thenAnswer(invocation -> {
                        calls.add("start");
                        return null;
                    });
            executionService.when(() -> AgentScriptTaskExecutionService.isComplete(entry, task, 88))
                    .thenAnswer(invocation -> {
                        calls.add("complete");
                        return true;
                    });

            AgentScriptTaskRuntime.tick(entry, 88);

            org.junit.jupiter.api.Assertions.assertEquals(List.of("start", "complete", "tick"), calls);
        }
    }
}
