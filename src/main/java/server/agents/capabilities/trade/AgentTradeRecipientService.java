package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentTradeRecipientService {
    private AgentTradeRecipientService() {
    }

    public static Character resolveTradeRecipient(AgentRuntimeEntry entry, Character agent) {
        int recipientId = AgentPendingTradeStateRuntime.recipientId(entry);
        if (recipientId <= 0) {
            return AgentRelationshipRuntime.interactionTarget(entry);
        }

        Character owner = AgentRelationshipRuntime.interactionTarget(entry);
        if (owner != null && owner.getId() == recipientId) {
            return owner;
        }

        if (agent.getMap() != null) {
            Character mapRecipient = agent.getMap().getCharacterById(recipientId);
            if (mapRecipient != null) {
                return mapRecipient;
            }
        }

        if (owner == null || !AgentPartyGatewayRuntime.party().hasParty(owner)) {
            return null;
        }

        for (Character member : AgentPartyGatewayRuntime.party().onlineMembers(owner)) {
            if (member != null && member.getId() == recipientId) {
                return member;
            }
        }

        return null;
    }
}
