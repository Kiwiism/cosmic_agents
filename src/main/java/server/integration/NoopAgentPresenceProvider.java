package server.integration;

import client.Character;

public final class NoopAgentPresenceProvider implements AgentPresenceProvider {
    @Override
    public boolean isAgent(Character chr) {
        return false;
    }
}
