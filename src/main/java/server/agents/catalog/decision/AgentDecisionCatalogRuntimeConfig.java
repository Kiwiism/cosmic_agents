package server.agents.catalog.decision;

import java.nio.file.Path;

/** Startup and rollout gates for read-only decision-catalog shadow evaluation. */
public record AgentDecisionCatalogRuntimeConfig(
        boolean enabled,
        boolean navigationShadowEnabled,
        boolean combatShadowEnabled,
        Path catalogDirectory,
        long sampleIntervalMs,
        long logIntervalMs) {

    public AgentDecisionCatalogRuntimeConfig {
        if (catalogDirectory == null || sampleIntervalMs < 100L || logIntervalMs < 1_000L) {
            throw new IllegalArgumentException("Decision catalog path and bounded telemetry intervals are required");
        }
        catalogDirectory = catalogDirectory.toAbsolutePath().normalize();
    }

    public static AgentDecisionCatalogRuntimeConfig fromSystemProperties() {
        return new AgentDecisionCatalogRuntimeConfig(
                booleanProperty("agents.catalog.decision.enabled", false),
                booleanProperty("agents.catalog.decision.navigationShadow.enabled", true),
                booleanProperty("agents.catalog.decision.combatShadow.enabled", true),
                Path.of(System.getProperty("agents.catalog.decision.dir", "tmp/agent-llm-catalog")),
                longProperty("agents.catalog.decision.sampleIntervalMs", 2_000L, 100L),
                longProperty("agents.catalog.decision.logIntervalMs", 60_000L, 1_000L));
    }

    private static boolean booleanProperty(String name, boolean fallback) {
        return Boolean.parseBoolean(System.getProperty(name, Boolean.toString(fallback)));
    }

    private static long longProperty(String name, long fallback, long minimum) {
        String configured = System.getProperty(name);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(minimum, Long.parseLong(configured));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
