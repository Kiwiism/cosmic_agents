package server.agents.integration;

import client.Character;

public interface PartyGateway {
    void leaveCurrentParty(Character agent);

    boolean createAgentParty(Character leader);

    boolean joinAgentParty(Character agent, int partyId);

    void publishAgentOnline(Character agent, int partyId);

    boolean sendPartyChat(Character speaker, String message);

    AgentPartySnapshot snapshot(Character character);
}

