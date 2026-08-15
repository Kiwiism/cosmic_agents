package server.agents.runtime.activity;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * One independently replaceable foreground mode considered by the activity
 * arbiter. Higher priorities are evaluated first.
 */
public interface AgentForegroundActivity {
    String id();

    int priority();

    boolean active(AgentRuntimeEntry entry, Character agent);

    AgentForegroundActivityTick tick(AgentRuntimeEntry entry, Character agent, long nowMs);

    /** Whether this activity must be deactivated before another exclusive owner starts. */
    default boolean exclusive() {
        return true;
    }

    /** Immediate cleanup reserved for forced replacement or shutdown. */
    default void deactivate(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
    }

    /**
     * Requests ordinary deactivation and reports whether the activity is already terminal.
     * Activities with a closing sequence override this without weakening the force-cleanup seam.
     */
    default boolean requestDeactivate(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        deactivate(entry, agent, reason, nowMs);
        return true;
    }
}
