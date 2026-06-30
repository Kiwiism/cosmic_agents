package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

public final class AgentTradeRecipientService {
    private AgentTradeRecipientService() {
    }

    public static Character resolveTradeRecipient(BotEntry entry, Character agent) {
        int recipientId = AgentBotPendingTradeStateRuntime.recipientId(entry);
        if (recipientId <= 0) {
            return AgentBotRuntimeIdentityRuntime.owner(entry);
        }

        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
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
