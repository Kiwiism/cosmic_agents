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
        assertEquals("cosmic_economy", loaded.config().persistence.database);
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

    @Test
    void rejectsAdministrativeBootstrapEndowments() {
        String source = javaResource("economy-engine.yaml")
                .replace("holdingsMode: IMPORT_EXISTING_COSMIC_CHARACTERS",
                        "holdingsMode: EXPLICIT_BOOTSTRAP_ENDOWMENT")
                .replace("shopPermitPolicy: REQUIRE_OWNED_REAL_ITEM",
                        "shopPermitPolicy: EXPLICIT_BOOTSTRAP_ENDOWMENT")
                .replace("allowAdministratorEndowment: false", "allowAdministratorEndowment: true");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("bootstrap.holdingsMode"));
    }

    @Test
    void rejectsRepricingWithoutARealObservationWindow() {
        String source = javaResource("economy-engine.yaml")
                .replace("minimumRepriceInterval: PT30M", "minimumRepriceInterval: PT0S");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("minimumRepriceInterval"));
    }

    @Test
    void rejectsAdvertisedButUnimplementedNpcTravelMode() {
        String source = javaResource("economy-engine.yaml")
                .replace("accessMode: REMOTE_FROM_FREE_MARKET", "accessMode: PHYSICAL_TRAVEL");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("npcCommerce.accessMode"));
    }

    @Test
    void rejectsSyntheticScrollOutcomeRates() {
        String source = javaResource("economy-engine.yaml")
                .replace("preserveRealSuccessAndDestructionRates: true",
                        "preserveRealSuccessAndDestructionRates: false");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("Cosmic outcome rates"));
    }

    private static String javaResource(String path) {
        try {
            return java.nio.file.Files.readString(Path.of(path));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
