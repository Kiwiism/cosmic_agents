package server.agents.runtime.activity;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

/** One controller considered by the Activity Host. */
public interface AgentActivityController {
    String id();

    int precedence();

    AgentActivityRole role();

    default AgentActivityKind activityKind() {
        return null;
    }

    boolean active(AgentRuntimeEntry entry, Character agent);

    AgentActivityTick tick(AgentRuntimeEntry entry, Character agent, long nowMs);

    default boolean exclusive() {
        return role() == AgentActivityRole.PRIMARY;
    }

    default void forceStop(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
    }

    default boolean requestStop(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        forceStop(entry, agent, reason, nowMs);
        return true;
    }
}
