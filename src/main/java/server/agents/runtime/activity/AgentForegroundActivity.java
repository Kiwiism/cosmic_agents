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
}
