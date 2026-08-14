package server.agents.economy.scenario;

import server.agents.economy.activity.RuleExactFarmResolver;
import server.agents.economy.catalog.CatalogBundleDescriptor;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.persistence.EconomyLifecycleJournal;

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
        return new EconomyRunApplication(runId, null, config, bundle, catalog, world, journal);
    }

    public static EconomyRunApplication restore(SimulationRunEngine.RunCheckpoint checkpoint,
                                                LoadedEconomyConfig config,
                                                CatalogBundleDescriptor bundle, EconomyCatalog catalog,
                                                EconomyWorldPort world, EconomyLifecycleJournal journal) {
        return new EconomyRunApplication(checkpoint.runId(), checkpoint, config, bundle, catalog, world, journal);
    }

    @SuppressWarnings("unchecked")
    private EconomyRunApplication(UUID runId, SimulationRunEngine.RunCheckpoint checkpoint,
                                  LoadedEconomyConfig config, CatalogBundleDescriptor bundle,
                                  EconomyCatalog catalog, EconomyWorldPort world,
                                  EconomyLifecycleJournal journal) {
        AtomicReference<EconomyRunCoordinator> handler = new AtomicReference<>();
        this.engine = checkpoint == null
                ? new SimulationRunEngine(runId, config, bundle, event -> handler.get().handle(event))
                : SimulationRunEngine.restore(checkpoint, config, bundle, event -> handler.get().handle(event));
        this.coordinator = new EconomyRunCoordinator(engine, world, new RuleExactFarmResolver(catalog), journal);
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
    public UUID runId() { return engine.runId(); }
}
