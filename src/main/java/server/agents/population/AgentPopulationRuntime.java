package server.agents.population;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.cosmic.CosmicAgentPopulationBackend;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

/** Process lifecycle and composition root for the external population module. */
public final class AgentPopulationRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentPopulationRuntime.class);
    private static final String STORE_ENV = "COSMIC_AGENT_POPULATION_FILE";
    private static AgentPopulationScheduler scheduler;
    private static AgentPopulationAdminService admin;

    private AgentPopulationRuntime() {
    }

    public static synchronized void start() {
        if (scheduler != null) return;
        try {
            AgentPopulationRegistry registry = new AgentPopulationRegistry(
                    new FileAgentPopulationStore(storePath()));
            AgentPopulationMetrics metrics = new AgentPopulationMetrics();
            AgentPopulationSessionService sessions = new AgentPopulationSessionService(
                    new CosmicAgentPopulationBackend());
            AgentPopulationReconciler reconciler = new AgentPopulationReconciler(
                    registry, new AgentPopulationCurve(), new AgentPopulationPolicy(), sessions, metrics);
            scheduler = new AgentPopulationScheduler(reconciler, metrics,
                    AgentPopulationScheduler.DEFAULT_SWEEP_MS);
            admin = new AgentPopulationAdminService(
                    registry,
                    sessions,
                    scheduler,
                    metrics,
                    name -> {
                        try {
                            return AgentPersistenceGatewayRuntime.persistence().findCharacterByName(name);
                        } catch (SQLException failure) {
                            return null;
                        }
                    });
            scheduler.start();
            if (registry.snapshot().enabled()) scheduler.scheduleFastStart();
            log.info("Agent population runtime started: enabled={} managed={} store={}",
                    registry.snapshot().enabled(), registry.snapshot().agents().size(), storePath());
        } catch (IOException | RuntimeException failure) {
            scheduler = null;
            admin = null;
            log.error("Agent population runtime failed closed; scheduling is unavailable", failure);
        }
    }

    public static synchronized AgentPopulationAdminService admin() {
        if (admin == null) throw new IllegalStateException("Agent population runtime is unavailable");
        return admin;
    }

    public static synchronized void stop() {
        if (scheduler != null) scheduler.close();
        scheduler = null;
        admin = null;
    }

    static Path storePath() {
        String configured = System.getenv(STORE_ENV);
        return configured == null || configured.isBlank()
                ? Path.of(".runtime", "agents", "population.json")
                : Path.of(configured);
    }
}
