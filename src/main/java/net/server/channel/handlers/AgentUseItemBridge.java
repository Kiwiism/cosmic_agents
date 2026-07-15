package net.server.channel.handlers;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;

public final class AgentUseItemBridge {
    private AgentUseItemBridge() {
    }

    public static boolean consumeUseItem(Character agent, short slot, int itemId) {
        if (agent == null || !AgentCharacterGatewayRuntime.characters().isAgentCharacter(agent)) {
            return false;
        }
        return UseItemHandler.consumeServerSideUseItem(agent, slot, itemId);
    }
}
