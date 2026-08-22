package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCleanSlateResetBoundaryTest {
    @Test
    void destructiveSqlStaysInsideCosmicAndBridgeUsesGuardedService() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/server/agents/administration/AgentCleanSlateResetService.java"));
        String cosmic = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentCleanSlateResetPort.java"));
        String bridge = Files.readString(Path.of(
                "src/main/java/net/server/admin/AgentDirectorBridgeServer.java"));

        assertFalse(service.contains("tools.DatabaseConnection"));
        assertFalse(bridge.contains("tools.DatabaseConnection"));
        assertTrue(cosmic.contains("tools.DatabaseConnection"));
        assertTrue(cosmic.contains("UPDATE characters SET level = 1"));
        assertTrue(cosmic.contains("ItemFactory.INVENTORY.saveItems"));
        assertTrue(bridge.contains("/reset/preview"));
        assertTrue(bridge.contains("/reset/execute"));
    }
}
