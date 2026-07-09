package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTradeWindowBoundaryTest {
    @Test
    void concreteTradeWindowAdapterLivesOnlyAtCosmicBoundary() throws IOException {
        String manualRuntime = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/trade/AgentManualTradeRuntimeService.java"));
        String inventoryRuntime = Files.readString(Path.of(
                "src/main/java/server/agents/integration/AgentInventoryRuntimeAdapters.java"));
        String cosmicGateway = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicTradeGateway.java"));

        assertFalse(manualRuntime.contains("CosmicAgentTradeWindow"));
        assertFalse(inventoryRuntime.contains("CosmicAgentTradeWindow"));
        assertTrue(manualRuntime.contains("AgentTradeGatewayRuntime.trade().currentWindow("));
        assertTrue(inventoryRuntime.contains("AgentTradeGatewayRuntime.trade().currentWindow(agent)"));
        assertTrue(cosmicGateway.contains("CosmicAgentTradeWindow.wrap(agent.getTrade())"));
    }
}
