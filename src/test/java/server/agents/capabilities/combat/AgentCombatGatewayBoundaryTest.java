package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatGatewayBoundaryTest {
    @Test
    void attackExecutionProviderDoesNotImportCosmicDamageHandlers() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/combat/AgentAttackExecutionProvider.java"));
        String gateway = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicCombatGateway.java"));

        assertFalse(source.contains("CloseRangeDamageHandler"));
        assertFalse(source.contains("MagicDamageHandler"));
        assertFalse(source.contains("RangedAttackHandler"));
        assertTrue(source.contains("AgentCombatGatewayRuntime.combat().applyAttackEffects"));

        assertTrue(gateway.contains("CloseRangeDamageHandler"));
        assertTrue(gateway.contains("MagicDamageHandler"));
        assertTrue(gateway.contains("RangedAttackHandler"));
    }
}
