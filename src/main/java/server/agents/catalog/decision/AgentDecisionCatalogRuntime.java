package server.agents.catalog.decision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Optional;

/** Process lifecycle for the optional read-only decision-catalog shadow runtime. */
public final class AgentDecisionCatalogRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentDecisionCatalogRuntime.class);
    private static volatile State state = State.disabled();
    private static boolean started;

    private AgentDecisionCatalogRuntime() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        AgentDecisionCatalogRuntimeConfig config = AgentDecisionCatalogRuntimeConfig.fromSystemProperties();
        if (!config.enabled()) {
            state = new State(config, null, null, null, "disabled");
            log.info("Agent decision catalog runtime disabled");
            return;
        }
        try {
            AgentDecisionCatalogRepository repository =
                    FileAgentDecisionCatalogRepository.load(config.catalogDirectory());
            AgentTopologyQueryService topology = new AgentTopologyQueryService(repository);
            AgentCombatPolicyQueryService combat = new AgentCombatPolicyQueryService(repository, topology);
            AgentDecisionShadowService shadow = new AgentDecisionShadowService(config, topology, combat);
            state = new State(config, repository, new Queries(topology, combat), shadow, "");
            AgentDecisionCatalogSnapshot snapshot = repository.snapshot();
            log.info("Agent decision catalog runtime ready: schema={} generatedAt={} navigationMaps={} "
                            + "combatMaps={} navigationShadow={} combatShadow={}",
                    snapshot.version().schemaVersion(),
                    snapshot.version().generatedAt(),
                    snapshot.navigationByMapId().size(),
                    snapshot.combatByMapId().size(),
                    config.navigationShadowEnabled(),
                    config.combatShadowEnabled());
        } catch (RuntimeException failure) {
            state = new State(config, null, null, null, failure.getMessage());
            log.error("Agent decision catalog runtime failed closed; live behavior remains unchanged", failure);
        }
    }

    public static synchronized void stop() {
        state = State.disabled();
        started = false;
    }

    public static void observeNavigation(AgentRuntimeEntry entry,
                                         int mapId,
                                         int sourceX,
                                         int sourceY,
                                         int targetX,
                                         int targetY,
                                         int liveSourceRegionId,
                                         int liveTargetRegionId,
                                         long nowMs) {
        AgentDecisionShadowService shadow = state.shadow();
        if (shadow != null) {
            shadow.observeNavigation(entry, mapId, sourceX, sourceY, targetX, targetY,
                    liveSourceRegionId, liveTargetRegionId, nowMs);
        }
    }

    public static void observeCombatTarget(AgentRuntimeEntry entry,
                                           int mapId,
                                           int agentX,
                                           int agentY,
                                           int mobId,
                                           int targetX,
                                           int targetY,
                                           long nowMs) {
        AgentDecisionShadowService shadow = state.shadow();
        if (shadow != null) {
            shadow.observeCombatTarget(entry, mapId, agentX, agentY, mobId, targetX, targetY, nowMs);
        }
    }

    public static Status status() {
        State current = state;
        AgentDecisionCatalogSnapshot snapshot = current.repository() == null
                ? null : current.repository().snapshot();
        return new Status(
                current.config().enabled(),
                snapshot != null,
                snapshot == null ? 0 : snapshot.version().schemaVersion(),
                snapshot == null ? "" : snapshot.version().generatedAt(),
                snapshot == null ? 0 : snapshot.navigationByMapId().size(),
                snapshot == null ? 0 : snapshot.combatByMapId().size(),
                current.failureReason(),
                current.shadow() == null
                        ? AgentDecisionShadowService.Snapshot.empty()
                        : current.shadow().snapshot());
    }

    /** Shared advisory query bundle; empty when the optional runtime is disabled or failed to load. */
    public static Optional<Queries> queries() {
        return Optional.ofNullable(state.queries());
    }

    private record State(AgentDecisionCatalogRuntimeConfig config,
                         AgentDecisionCatalogRepository repository,
                         Queries queries,
                         AgentDecisionShadowService shadow,
                         String failureReason) {
        private static State disabled() {
            return new State(new AgentDecisionCatalogRuntimeConfig(
                    false, false, false, java.nio.file.Path.of("tmp/agent-llm-catalog"),
                    2_000L, 60_000L), null, null, null, "disabled");
        }
    }

    public record Queries(AgentTopologyQueryService topology,
                          AgentCombatPolicyQueryService combat) {
        public Queries {
            if (topology == null || combat == null) {
                throw new IllegalArgumentException("Decision catalog query services are required");
            }
        }
    }

    public record Status(boolean enabled,
                         boolean available,
                         int schemaVersion,
                         String generatedAt,
                         int navigationMaps,
                         int combatMaps,
                         String failureReason,
                         AgentDecisionShadowService.Snapshot shadow) {
    }
}
