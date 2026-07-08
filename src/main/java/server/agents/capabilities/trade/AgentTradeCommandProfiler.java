package server.agents.capabilities.trade;

import client.Character;
import org.slf4j.Logger;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentTradeCommandProfiler {
    private AgentTradeCommandProfiler() {
    }

    public static boolean profileCategory(String category) {
        return "trash".equals(category)
                || "equips".equals(category)
                || AgentInventoryTradePolicy.isReservedEquipsCategory(category);
    }

    public static long startIfProfiled(String category) {
        return profileCategory(category) ? System.nanoTime() : 0L;
    }

    public static void logSlowCommand(String category,
                                      String phase,
                                      AgentRuntimeEntry entry,
                                      Character agent,
                                      long startedAt,
                                      long warnThresholdNs,
                                      Logger logger) {
        if (startedAt == 0L || !profileCategory(category)) {
            return;
        }
        long elapsedNs = System.nanoTime() - startedAt;
        if (elapsedNs < warnThresholdNs) {
            return;
        }
        String agentName = agent != null ? agent.getName() : "?";
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        String ownerName = owner != null ? owner.getName() : "?";
        logger.warn("Slow bot trade command phase: category={} phase={} took {} ms bot={} owner={}",
                category,
                phase,
                String.format("%.1f", elapsedNs / 1_000_000.0),
                agentName,
                ownerName);
    }
}
