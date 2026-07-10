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
        List<Path> workers = List.of(
                Path.of("src/main/java/server/agents/capabilities/dialogue/llm/AgentLlmReplyService.java"),
                Path.of("src/main/java/server/agents/capabilities/navigation/AgentNavigationGraphService.java"),
                Path.of("src/main/java/server/agents/capabilities/trade/AgentTransferRuntime.java"));

        for (Path worker : workers) {
            String source = Files.readString(worker);
            assertTrue(source.contains("AgentBoundedExecutorFactory.fixed"), worker.toString());
            assertFalse(source.contains("Executors.new"), worker.toString());
        }
    }
}
