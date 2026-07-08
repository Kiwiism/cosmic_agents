package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentPendingTradeStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentTradeRecipientService {
    private AgentTradeRecipientService() {
    }

    public static Character resolveTradeRecipient(AgentRuntimeEntry entry, Character agent) {
        int recipientId = AgentPendingTradeStateRuntime.recipientId(entry);
        if (recipientId <= 0) {
            return AgentRuntimeIdentityRuntime.owner(entry);
        }

        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        if (owner != null && owner.getId() == recipientId) {
            return owner;
        }

        if (agent.getMap() != null) {
            Character mapRecipient = agent.getMap().getCharacterById(recipientId);
            if (mapRecipient != null) {
                return mapRecipient;
            }
        }

        if (owner == null || owner.getParty() == null) {
            return null;
        }

        for (Character member : owner.getPartyMembersOnline()) {
            if (member != null && member.getId() == recipientId) {
                return member;
            }
        }

        return null;
    }
}
