package server.agents.integration;

import client.Character;

public interface PartyGateway {
    void leaveCurrentParty(Character agent);

    boolean createAgentParty(Character leader);

    boolean joinAgentParty(Character agent, int partyId);
}

