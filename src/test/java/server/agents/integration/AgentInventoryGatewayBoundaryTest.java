package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentInventoryGatewayBoundaryTest {
    @Test
    void useItemHandlerRemainsInsideCosmicInventoryAdapter() throws Exception {
        String capability = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/combat/AgentBuffService.java"));
        String cosmic = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicInventoryGateway.java"));

        assertFalse(capability.contains("UseItemHandler"));
        assertTrue(capability.contains("inventory.consumeUseItem"));
        assertTrue(cosmic.contains("UseItemHandler.consumeUseItem"));
    }
}
