package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAsyncQueueBoundaryTest {
    @Test
    void agentBackgroundWorkersUseBoundedExecutorFactory() throws Exception {
        Path registry = Path.of(
                "src/main/java/server/agents/runtime/async/AgentAsyncExecutorRegistry.java");
        String registrySource = Files.readString(registry);
        assertTrue(registrySource.contains("AgentBoundedExecutorFactory.fixed"), registry.toString());
        assertFalse(registrySource.contains("Executors.new"), registry.toString());

        List<Path> workers = List.of(
                Path.of("src/main/java/server/agents/capabilities/dialogue/llm/AgentLlmReplyService.java"),
                Path.of("src/main/java/server/agents/capabilities/navigation/AgentNavigationGraphService.java"),
                Path.of("src/main/java/server/agents/capabilities/trade/AgentTransferRuntime.java"),
                Path.of("src/main/java/server/agents/plans/amherst/AmherstPlanRuntimeRunner.java"));

        for (Path worker : workers) {
            String source = Files.readString(worker);
            assertTrue(source.contains("AgentAsyncTaskGateway")
                    || source.contains("AgentAsyncExecutorRegistry"), worker.toString());
            assertFalse(source.contains("Executors.new"), worker.toString());
            assertFalse(source.contains("CompletableFuture.supplyAsync"), worker.toString());
        }
    }
}
