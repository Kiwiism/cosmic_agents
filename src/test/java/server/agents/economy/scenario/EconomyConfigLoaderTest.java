package server.agents.economy.scenario;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyConfigLoaderTest {
    private final EconomyConfigLoader loader = new EconomyConfigLoader();

    @Test
    void loadsAndPinsDefaultScenario() {
        LoadedEconomyConfig loaded = loader.load(Path.of("economy-engine.yaml"));

        assertEquals("victoria-fm-baseline", loaded.config().scenario.id);
        assertEquals(50, loaded.config().population.initialAgents);
        assertEquals(200, loaded.config().population.maximumAgents);
        assertEquals("REMOTE_FROM_FREE_MARKET", loaded.config().npcCommerce.accessMode);
        assertEquals(64, loaded.sha256().length());
    }

    @Test
    void sourceChangesProduceDifferentRunHash() {
        String source = javaResource("economy-engine.yaml");
        String changed = source.replace("targetLogicalDays: 30", "targetLogicalDays: 31");

        assertNotEquals(loader.load(source).sha256(), loader.load(changed).sha256());
    }

    @Test
    void rejectsClassDistributionThatDoesNotBalance() {
        String source = javaResource("economy-engine.yaml")
                .replace("warrior: 0.20", "warrior: 0.30");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("classDistribution"));
    }

    @Test
    void rejectsMoreThanOneStallPerAgent() {
        String source = javaResource("economy-engine.yaml")
                .replace("maximumStallsPerAgent: 1", "maximumStallsPerAgent: 2");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("one stall"));
    }

    @Test
    void rejectsRemoteNpcAccessThatDropsSourceEvidence() {
        String source = javaResource("economy-engine.yaml")
                .replace("recordOriginalNpcAndMap: true", "recordOriginalNpcAndMap: false");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("original NPC and map"));
    }

    private static String javaResource(String path) {
        try {
            return java.nio.file.Files.readString(Path.of(path));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}

