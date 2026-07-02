package server.agents.capabilities.supplies;

import client.Character;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.util.List;

public final class AgentGroupSupplyResponderSelector {
    private AgentGroupSupplyResponderSelector() {
    }

    public static BotEntry select(Character leader, List<BotEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        int leaderMapId = leader != null ? leader.getMapId() : -1;
        for (BotEntry entry : entries) {
            if (AgentBotRuntimeIdentityRuntime.botMapId(entry) == leaderMapId) {
                return entry;
            }
        }
        return entries.get(0);
    }
}
