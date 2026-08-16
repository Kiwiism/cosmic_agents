package server.agents.runtime.activity;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/** Stable scheduler entry point for advancing the current Activity Host owner. */
public final class AgentActivityRuntime {
    private AgentActivityRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return AgentActivityBootstrap.host().tick(entry, agent, nowMs);
    }
}
