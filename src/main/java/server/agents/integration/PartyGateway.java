package server.agents.integration;

import client.Character;

import java.util.List;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Party operations use the existing synchronized world/party services.")
public interface PartyGateway {
    void leaveCurrentParty(Character agent);

    boolean createAgentParty(Character leader);

    boolean joinAgentParty(Character agent, int partyId);

    boolean invitePartyMember(Character leader, Character invitee);

    boolean hasPendingPartyInvite(int inviteeId);

    void cancelPartyInvite(int inviteeId);

    void publishAgentOnline(Character agent, int partyId);

    boolean sendPartyChat(Character speaker, String message);

    AgentPartySnapshot snapshot(Character character);

    boolean hasParty(Character character);

    List<Character> onlineMembers(Character character);
}

