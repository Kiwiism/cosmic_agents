package server.agents.capabilities.townlife;

/**
 * Bounded local-mechanic seam. Extensions cannot own the session lifecycle or cross-map travel;
 * implementations receive immutable context and return typed progress only.
 */
public interface AgentTownLifeActivityExtension {
    String id();

    Result start(Context context);

    Result tick(Context context);

    default void cancel(Context context) {
    }

    record Context(int agentId,
                   int world,
                   int channel,
                   int townMapId,
                   String venueId,
                   long nowMs,
                   long deadlineMs) {
        public Context {
            venueId = venueId == null ? "" : venueId;
            if (agentId <= 0 || townMapId <= 0 || nowMs < 0L || deadlineMs < nowMs) {
                throw new IllegalArgumentException("valid bounded TownLife extension context is required");
            }
        }
    }

    enum Result {
        ACTIVE,
        SUCCEEDED,
        BLOCKED,
        FAILED,
        CANCELLED
    }
}
