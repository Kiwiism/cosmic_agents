package server.integration;

import client.Character;

public interface AgentPresenceProvider {
    boolean isAgent(Character chr);
}
