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
        assertEquals("REMOTE_FROM_FREE_MARKET_ENTRANCE", loaded.config().npcCommerce.accessMode);
        assertEquals("cosmic_economy", loaded.config().persistence.database);
        assertEquals(64, loaded.sha256().length());
        assertTrue(loaded.normalizedJson().contains("\"schemaVersion\":1"));
        assertTrue(loaded.normalizedJson().contains("\"shopPermitItemId\":5140000"));
        assertEquals(6, loaded.config().bootstrap.shopPermitItemIds.size());
        assertEquals("PT0.5S", loaded.config().market.stallInspectionDurationPerListing);
        assertTrue(!loaded.config().market.openChatSelling.enabled);
    }

    @Test
    void loadsTenAgentOneRoomOwnershipProfile() {
        LoadedEconomyConfig loaded = loader.load(
                Path.of("config/economy/economy-engine-basic.yaml"));

        assertEquals("victoria-fm-ownership-basic", loaded.config().scenario.id);
        assertEquals(10, loaded.config().population.initialAgents);
        assertEquals(10, loaded.config().population.maximumAgents);
        assertEquals(910000001, loaded.config().world.firstFreeMarketRoomMapId);
        assertEquals(910000001, loaded.config().world.lastFreeMarketRoomMapId);
        assertEquals("REALTIME", loaded.config().clock.mode);
        assertEquals("DISABLED", loaded.config().activity.executionMode);
        assertTrue(loaded.config().market.openChatSelling.enabled);
    }

    @Test
    void loadsDetachedThirtyDayObservationProfile() {
        LoadedEconomyConfig loaded = loader.load(
                Path.of("config/economy/economy-commerce-observe-30day.yaml"));

        assertEquals("victoria-commerce-observe-30day-v1", loaded.config().scenario.id);
        assertEquals(30, loaded.config().scenario.targetLogicalDays);
        assertEquals("MAX_THROUGHPUT", loaded.config().clock.mode);
        assertEquals(10, loaded.config().population.initialAgents);
        assertEquals(100, loaded.config().population.maximumAgents);
        assertEquals(10, loaded.config().population.growth.amount);
        assertEquals(910000022, loaded.config().world.lastFreeMarketRoomMapId);
        assertTrue(!loaded.config().ambient.enabled);
        assertTrue(loaded.config().quests.enabled);
        assertTrue(loaded.config().market.openChatSelling.enabled);
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
                .replace("shopPermitPolicy: GRANT_RANDOM_REAL_PERMIT_ON_ENTRY",
                        "shopPermitPolicy: EXPLICIT_BOOTSTRAP_ENDOWMENT")
                .replace("allowAdministratorEndowment: false", "allowAdministratorEndowment: true");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("bootstrap.holdingsMode"));
    }

    @Test
    void rejectsHiredMerchantItemAsPlayerShopPermit() {
        String source = javaResource("economy-engine.yaml")
                .replace("shopPermitItemId: 5140000", "shopPermitItemId: 5030000");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("PlayerShop permit"));
    }

    @Test
    void rejectsUnverifiedPermitPoolAndUnknownInteractionProvider() {
        String invalidPermit = javaResource("economy-engine.yaml")
                .replace("5140006]", "5030000]");
        String invalidProvider = javaResource("economy-engine.yaml")
                .replace("interactionBehaviorProvider: SOLOMAPLING_INSPIRED",
                        "interactionBehaviorProvider: PROCEDURAL_ORACLE");

        assertTrue(assertThrows(EconomyConfigException.class,
                () -> loader.load(invalidPermit)).getMessage().contains("PlayerShop permits"));
        assertTrue(assertThrows(EconomyConfigException.class,
                () -> loader.load(invalidProvider)).getMessage().contains("interactionBehaviorProvider"));
    }

    @Test
    void rejectsRepricingWithoutARealObservationWindow() {
        String source = javaResource("economy-engine.yaml")
                .replace("maximumReprices: 0", "maximumReprices: 1")
                .replace("minimumRepriceInterval: PT30M", "minimumRepriceInterval: PT0S");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("minimumRepriceInterval"));
    }

    @Test
    void rejectsAdvertisedButUnimplementedNpcTravelMode() {
        String source = javaResource("economy-engine.yaml")
                .replace("accessMode: REMOTE_FROM_FREE_MARKET_ENTRANCE", "accessMode: PHYSICAL_TRAVEL");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("npcCommerce.accessMode"));
    }

    @Test
    void acceptsRealtimeClockAndRejectsUnimplementedAcceleratedClock() {
        String realtime = javaResource("economy-engine.yaml");
        String maximumThroughput = realtime.replace("mode: REALTIME", "mode: MAX_THROUGHPUT");
        String unsupported = realtime.replace("mode: REALTIME", "mode: ACCELERATED");

        assertEquals("REALTIME", loader.load(realtime).config().clock.mode);
        assertEquals("MAX_THROUGHPUT", loader.load(maximumThroughput).config().clock.mode);

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(unsupported));

        assertTrue(failure.getMessage().contains("clock.mode"));
    }

    @Test
    void rejectsAdvertisedCircularDetectionUntilItHasDurableSemantics() {
        String source = javaResource("economy-engine.yaml")
                .replace("detectCircularTrade: false", "detectCircularTrade: true");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("detectCircularTrade"));
    }

    @Test
    void rejectsAdvertisedCheckpointCompressionWithoutACompressedCodec() {
        String source = javaResource("economy-engine.yaml")
                .replace("checkpointCompression: false", "checkpointCompression: true");

        EconomyConfigException failure = assertThrows(
                EconomyConfigException.class, () -> loader.load(source));

        assertTrue(failure.getMessage().contains("checkpointCompression"));
    }

    @Test
    void rejectsUnknownConfigurationFields() {
        String source = javaResource("economy-engine.yaml")
                .replace("schemaVersion: 1", "schemaVersion: 1\nunknownMarketOracle: true");

        assertThrows(EconomyConfigException.class, () -> loader.load(source));
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

    @Test
    void enabledOpenChatRequiresStructuredIntentsAndConfiguredFmMaps() {
        String withoutIntents = javaResource("economy-engine.yaml")
                .replaceFirst("(?s)(openChatSelling:.*?enabled:) false", "$1 true")
                .replace("implicitEconomicIntentsEnabled: true", "implicitEconomicIntentsEnabled: false");
        String outsideConfiguredRooms = javaResource("config/economy/economy-engine-basic.yaml")
                .replace("allowedMaps: [910000000, 910000001]",
                        "allowedMaps: [910000000, 910000002]");

        EconomyConfigException withoutIntentFailure = assertThrows(EconomyConfigException.class,
                () -> loader.load(withoutIntents));
        assertTrue(withoutIntentFailure.getMessage().contains("structured implicit economic intents"),
                withoutIntentFailure.getMessage());
        EconomyConfigException roomFailure = assertThrows(EconomyConfigException.class,
                () -> loader.load(outsideConfiguredRooms));
        assertTrue(roomFailure.getMessage().contains("configured Free Market"), roomFailure.getMessage());
    }

    private static String javaResource(String path) {
        try {
            return java.nio.file.Files.readString(Path.of(path));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
