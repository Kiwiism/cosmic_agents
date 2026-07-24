package server.agents.runtime.activity;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Explicit foreground replacement boundary used by commands and plan starts.
 * It keeps cleanup out of the scheduler and out of the new activity owner.
 */
public final class AgentForegroundActivityCoordinator {
    private final AgentForegroundActivityRegistry registry;

    public AgentForegroundActivityCoordinator(AgentForegroundActivityRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Foreground activity registry is required");
        }
        this.registry = registry;
    }

    public void prepareExclusive(
            String targetActivityId,
            AgentRuntimeEntry entry,
            Character agent,
            String reason,
            long nowMs) {
        if (entry == null || agent == null || targetActivityId == null
                || targetActivityId.isBlank()) {
            return;
        }
        registry.find(targetActivityId).orElseThrow(() ->
                new IllegalArgumentException(
                        "Unknown foreground activity: " + targetActivityId));
        for (AgentForegroundActivity activity : registry.activities()) {
            if (!activity.id().equals(targetActivityId)
                    && activity.exclusive()
                    && activity.active(entry, agent)) {
                activity.deactivate(entry, agent, reason, nowMs);
            }
        }
    }
}
