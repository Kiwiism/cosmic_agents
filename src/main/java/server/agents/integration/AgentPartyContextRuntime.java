package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/** Party identity is canonical game state and independent of cohort and formation identity. */
public final class AgentPartyContextRuntime {
    private AgentPartyContextRuntime() {
    }

    public static int partyId(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        return agent == null ? -1 : agent.getPartyId();
    }
}
