package server.agents.economy.scenario;

import server.agents.economy.session.CommerceParticipant;

import org.junit.jupiter.api.Test;
import server.agents.economy.activity.*;
import server.agents.economy.catalog.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import server.agents.economy.persistence.RunCheckpointCodec;
import server.agents.economy.persistence.EconomyLifecycleJournal;

import static org.junit.jupiter.api.Assertions.*;

class EconomyRunCoordinatorTest {
    @Test
    void commerceOnlyModeReentersWithoutPlanningOrSettlingSyntheticActivity() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load(
                java.nio.file.Path.of("config/economy/economy-engine-basic.yaml"));
        loaded.config().population.initialAgents = 1;
        loaded.config().population.maximumAgents = 1;
        CatalogBundleDescriptor bundle = new CatalogBundleLoader().load(loaded.config().catalog);
        TestWorld world = new TestWorld();
        AtomicReference<EconomyRunCoordinator> coordinator = new AtomicReference<>();
        SimulationRunEngine engine = new SimulationRunEngine(UUID.randomUUID(), loaded, bundle,
                event -> coordinator.get().handle(event));
        coordinator.set(new EconomyRunCoordinator(engine, world,
                new RuleExactFarmResolver(new StubCatalog()), journal()));

        engine.advanceTo(engine.now().plus(Duration.ofMinutes(2)));

        assertEquals(List.of("admit", "market", "admit", "market"), world.actions);
        assertNull(world.outcome);
        assertEquals(EconomyRunCoordinator.Status.IN_FREE_MARKET,
                coordinator.get().agentViews().get("agent-1").status());
    }

    @Test
    void runsAdmissionCalibratedActivitySettlementAndFmReturnInLogicalTime() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        loaded.config().population.initialAgents = 1;
        loaded.config().population.maximumAgents = 1;
        CatalogBundleDescriptor bundle = new CatalogBundleLoader().load(loaded.config().catalog);
        EconomyCatalog catalog = new StubCatalog();
        TestWorld world = new TestWorld();
        AtomicReference<EconomyRunCoordinator> coordinator = new AtomicReference<>();
        SimulationRunEngine engine = new SimulationRunEngine(UUID.randomUUID(), loaded, bundle,
                event -> coordinator.get().handle(event));
        coordinator.set(new EconomyRunCoordinator(engine, world, new RuleExactFarmResolver(catalog), journal()));

        engine.advanceTo(engine.now().plus(Duration.ofHours(1)));

        assertEquals(List.of("admit", "market", "leave", "settle", "return", "admit", "market"),
                world.actions);
        assertEquals(1, world.outcome.itemDrops().size());
        assertEquals("observed:test-session", world.outcome.calibrationId());
        assertEquals(EconomyRunCoordinator.Status.IN_FREE_MARKET,
                coordinator.get().agentViews().get("agent-1").status());
    }

    @Test
    void resumesAnInFlightActivityWithoutRepeatingProduction() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        loaded.config().population.initialAgents = 1;
        loaded.config().population.maximumAgents = 1;
        CatalogBundleDescriptor bundle = new CatalogBundleLoader().load(loaded.config().catalog);
        EconomyCatalog catalog = new StubCatalog();
        TestWorld beforeRestart = new TestWorld();
        EconomyRunApplication first = EconomyRunApplication.start(UUID.randomUUID(), loaded, bundle,
                catalog, beforeRestart, journal());
        first.advanceTo(first.now().plus(Duration.ofMinutes(5)));
        assertEquals(EconomyRunCoordinator.Status.OFFSCREEN_ACTIVITY,
                first.agents().get("agent-1").status());
        RunCheckpointCodec codec = new RunCheckpointCodec();
        RunCheckpointCodec.Encoded encoded = codec.encode(first.checkpoint());

        TestWorld afterRestart = new TestWorld();
        EconomyRunApplication restored = EconomyRunApplication.restore(
                codec.decode(encoded.json(), encoded.sha256()), loaded, bundle, catalog, afterRestart, journal());
        restored.advanceTo(restored.now().plus(Duration.ofHours(1)));

        assertEquals(1, afterRestart.actions.stream().filter("settle"::equals).count());
        assertEquals(EconomyRunCoordinator.Status.IN_FREE_MARKET,
                restored.agents().get("agent-1").status());
    }

    @Test
    void cohortCompletesConfiguredExternalOnboardingBeforeFirstMarketCycle() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        loaded.config().population.initialAgents = 1;
        loaded.config().population.maximumAgents = 1;
        loaded.config().population.onboardingDuration = "PT20M";
        CatalogBundleDescriptor bundle = new CatalogBundleLoader().load(loaded.config().catalog);
        TestWorld world = new TestWorld();
        AtomicReference<EconomyRunCoordinator> coordinator = new AtomicReference<>();
        SimulationRunEngine engine = new SimulationRunEngine(UUID.randomUUID(), loaded, bundle,
                event -> coordinator.get().handle(event));
        coordinator.set(new EconomyRunCoordinator(engine, world,
                new RuleExactFarmResolver(new StubCatalog()), journal()));

        engine.advanceTo(engine.now().plus(Duration.ofMinutes(19)));
        assertFalse(world.actions.contains("market"));
        assertEquals(EconomyRunCoordinator.Status.ONBOARDING_ACTIVITY,
                coordinator.get().agentViews().get("agent-1").status());

        engine.advanceTo(engine.now().plus(Duration.ofMinutes(1)));
        assertTrue(world.actions.contains("market"));
        assertEquals(2, world.actions.stream().filter("settle"::equals).count());
        assertEquals(EconomyRunCoordinator.Status.IN_FREE_MARKET,
                coordinator.get().agentViews().get("agent-1").status());
    }

    private static final class TestWorld implements EconomyWorldPort {
        private final List<String> actions = new ArrayList<>();
        private boolean activityScheduled;
        private FarmSessionOutcome outcome;

        public void admit(CommerceParticipant profile, Instant at) { actions.add("admit"); }
        public MarketDirective performMarketCycle(CommerceParticipant profile, Instant at) {
            actions.add("market");
            if (activityScheduled) return MarketDirective.idle();
            activityScheduled = true;
            return new MarketDirective(Optional.of(at.plusSeconds(1)), Optional.empty());
        }
        public FarmSessionPlan planOffscreenActivity(CommerceParticipant profile, Instant at) {
            return new FarmSessionPlan("session-1", "observed:test-session", profile.agentId(),
                    100000001, at, Duration.ofMinutes(10), 1,
                    List.of(new FarmSessionPlan.MonsterWork(100100, 1, 3)), Set.of(), List.of());
        }
        public void leaveFreeMarket(CommerceParticipant profile, FarmSessionPlan plan, Instant at) {
            actions.add("leave");
        }
        public FarmSessionOutcome settleOffscreenActivity(CommerceParticipant profile,
                                                           FarmSessionOutcome value, Instant at,
                                                           java.util.function.LongSupplier random) {
            actions.add("settle");
            outcome = value;
            return value;
        }
        public void returnThroughFreeMarketEntrance(CommerceParticipant profile, Instant at) {
            actions.add("return");
        }
        public Map<String, Object> snapshotState() {
            return Map.of("activityScheduled", activityScheduled);
        }
        public void restoreState(Map<String, Object> state) {
            activityScheduled = Boolean.TRUE.equals(state.get("activityScheduled"));
        }
    }

    private static EconomyLifecycleJournal journal() {
        return new EconomyLifecycleJournal() {
            public void admitted(UUID runId, CommerceParticipant profile, Instant at) { }
            public void activityStarted(UUID runId, FarmSessionPlan plan) { }
            public void activityCompleted(UUID runId, FarmSessionOutcome outcome) { }
            public void stateChanged(UUID runId, String agentId, EconomyRunCoordinator.Status state,
                                     String activityId, Instant at) { }
        };
    }

    private static final class StubCatalog implements EconomyCatalog {
        public String version() { return "test"; }
        public Optional<ItemFact> item(int itemId) { return Optional.of(new ItemFact(itemId, "drop", 1,
                null, 100, Set.of(ItemCategory.OTHER), Map.of())); }
        public List<MonsterDropFact> monsterDrops(int monsterId) {
            return List.of(new MonsterDropFact(monsterId, 4000000, 1_000_000, 1, 1, 0));
        }
        public Optional<NpcShopFact> npcShop(int npcId) { return Optional.empty(); }
    }
}
