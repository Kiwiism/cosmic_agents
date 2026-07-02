package server.agents.runtime;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.plans.AgentTask;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentScriptTaskRuntimeTest {
    @Test
    void defaultTickUsesMovementStopDistance() {
        BotEntry entry = mock(BotEntry.class);
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
                        java.util.function.BiPredicate<BotEntry, AgentTask> isComplete = invocation.getArgument(2);
                        isComplete.test(entry, task);
                        calls.add("tick");
                        return null;
                    });
            executionService.when(() -> AgentScriptTaskExecutionService.isComplete(
                            eq(entry),
                            eq(task),
                            eq(BotMovementManager.configuredStopDist())))
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
        BotEntry entry = mock(BotEntry.class);
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
                        java.util.function.BiConsumer<BotEntry, AgentTask> startTask = invocation.getArgument(1);
                        @SuppressWarnings("unchecked")
                        java.util.function.BiPredicate<BotEntry, AgentTask> isComplete = invocation.getArgument(2);
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
