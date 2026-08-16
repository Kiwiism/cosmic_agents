package server.agents.economy.scenario;

import server.agents.economy.session.CommerceParticipant;

import server.agents.economy.activity.RuleExactFarmResolver;
import server.agents.economy.catalog.CatalogBundleDescriptor;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.persistence.EconomyLifecycleJournal;
import server.agents.economy.session.EconomySessionPort;
import server.agents.simulation.activity.ExternalAgentActivityPort;
import server.agents.simulation.activity.RuleExactExternalActivityAdapter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Public run facade: same coordinator supports fresh runs, fast-forward, and checkpoint resume. */
public final class EconomyRunApplication {
    private final SimulationRunEngine engine;
    private final EconomyRunCoordinator coordinator;

    public static EconomyRunApplication start(UUID runId, LoadedEconomyConfig config,
                                              CatalogBundleDescriptor bundle, EconomyCatalog catalog,
                                              EconomyWorldPort world, EconomyLifecycleJournal journal) {
        return start(runId, config, bundle, catalog, world, legacyActivity(world, catalog), journal);
    }

    public static EconomyRunApplication start(UUID runId, LoadedEconomyConfig config,
                                              CatalogBundleDescriptor bundle, EconomyCatalog catalog,
                                              EconomySessionPort sessions,
                                              ExternalAgentActivityPort activities,
                                              EconomyLifecycleJournal journal) {
        return new EconomyRunApplication(runId, null, config, bundle, sessions, activities, journal);
    }

    public static EconomyRunApplication restore(SimulationRunEngine.RunCheckpoint checkpoint,
                                                LoadedEconomyConfig config,
                                                CatalogBundleDescriptor bundle, EconomyCatalog catalog,
                                                EconomyWorldPort world, EconomyLifecycleJournal journal) {
        return restore(checkpoint, config, bundle, catalog, world, legacyActivity(world, catalog), journal);
    }

    public static EconomyRunApplication restore(SimulationRunEngine.RunCheckpoint checkpoint,
                                                LoadedEconomyConfig config,
                                                CatalogBundleDescriptor bundle, EconomyCatalog catalog,
                                                EconomySessionPort sessions,
                                                ExternalAgentActivityPort activities,
                                                EconomyLifecycleJournal journal) {
        return new EconomyRunApplication(checkpoint.runId(), checkpoint, config, bundle,
                sessions, activities, journal);
    }

    @SuppressWarnings("unchecked")
    private EconomyRunApplication(UUID runId, SimulationRunEngine.RunCheckpoint checkpoint,
                                  LoadedEconomyConfig config, CatalogBundleDescriptor bundle,
                                  EconomySessionPort sessions, ExternalAgentActivityPort activities,
                                  EconomyLifecycleJournal journal) {
        AtomicReference<EconomyRunCoordinator> handler = new AtomicReference<>();
        this.engine = checkpoint == null
                ? new SimulationRunEngine(runId, config, bundle, event -> handler.get().handle(event))
                : SimulationRunEngine.restore(checkpoint, config, bundle, event -> handler.get().handle(event));
        this.coordinator = new EconomyRunCoordinator(engine, sessions, activities, journal);
        handler.set(coordinator);
        if (checkpoint != null) {
            Object state = checkpoint.domainState().get("coordinator");
            if (!(state instanceof Map<?, ?>)) throw new IllegalStateException("checkpoint lacks coordinator state");
            coordinator.restore((Map<String, Object>) state);
        }
    }

    public SimulationRunEngine.AdvanceSummary advanceDays(long days) { return engine.advanceDays(days); }
    public SimulationRunEngine.AdvanceSummary advanceTo(Instant target) { return engine.advanceTo(target); }
    public SimulationRunEngine.RunCheckpoint checkpoint() {
        return engine.checkpoint(Map.of("coordinator", coordinator.snapshot()));
    }
    public Map<String, EconomyRunCoordinator.AgentView> agents() { return coordinator.agentViews(); }
    public Instant now() { return engine.now(); }
    public Instant targetAt() { return engine.targetAt(); }
    public UUID runId() { return engine.runId(); }
    public void onCheckpoint(Runnable hook) { engine.onCheckpoint(hook); }

    private static ExternalAgentActivityPort legacyActivity(EconomyWorldPort world,
                                                             EconomyCatalog catalog) {
        return new RuleExactExternalActivityAdapter(world::planOffscreenActivity,
                new RuleExactFarmResolver(catalog), new RuleExactExternalActivityAdapter.Lifecycle() {
            @Override public void begin(CommerceParticipant profile,
                                        server.agents.economy.activity.FarmSessionPlan plan, Instant at) {
                world.leaveFreeMarket(profile, plan, at);
            }

            @Override public server.agents.economy.activity.FarmSessionOutcome settle(
                    CommerceParticipant profile,
                    server.agents.economy.activity.FarmSessionOutcome outcome, Instant at,
                    java.util.function.LongSupplier random) {
                return world.settleOffscreenActivity(profile, outcome, at, random);
            }

            @Override public void returnToEconomyEntrance(CommerceParticipant profile, Instant at) {
                world.returnThroughFreeMarketEntrance(profile, at);
            }
        });
    }
}
