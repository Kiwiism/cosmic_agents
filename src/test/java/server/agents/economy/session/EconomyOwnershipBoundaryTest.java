package server.agents.economy.session;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyOwnershipBoundaryTest {
    @Test
    void standaloneSessionContractHasNoFarmingDependency() throws Exception {
        String source = Files.readString(Path.of("src/main/java/server/agents/economy/session/EconomySessionPort.java"));
        assertFalse(source.contains("economy.activity"));
        assertFalse(source.contains("FarmSession"));
        assertFalse(source.contains("economy.scenario"));
        assertTrue(source.contains("requestEntry"));
        assertTrue(source.contains("release("));
    }

    @Test
    void externalActivityContractLivesOutsideEconomySessionPackage() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/server/agents/simulation/activity/ExternalAgentActivityPort.java"));
        assertTrue(source.contains("plan("));
        assertTrue(source.contains("settle("));
        assertFalse(source.contains("package server.agents.economy.session"));
    }
}
