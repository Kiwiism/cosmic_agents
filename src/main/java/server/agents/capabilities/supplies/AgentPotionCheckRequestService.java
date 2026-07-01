package server.agents.capabilities.supplies;

import client.BotClient;
import client.Character;
import server.agents.integration.AgentBotPotionStateRuntime;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeRegistry;
import server.bots.BotEntry;

public final class AgentPotionCheckRequestService {
    private AgentPotionCheckRequestService() {
    }

    public static void requestPotionCheckSoon(Character agent) {
        if (agent == null || !(agent.getClient() instanceof BotClient)) {
            return;
        }
        Character leader = AgentRuntimeRegistry.activeLeaderByAgentCharacterId(
                AgentRuntimeRegistry.entriesByLeaderId(), agent.getId());
        if (leader == null) {
            return;
        }
        BotEntry entry = AgentRuntimeRegistry.findByCharacterId(
                AgentRuntimeRegistry.entriesByLeaderId(), leader.getId(), agent.getId());
        if (entry == null) {
            return;
        }
        int soonDelayMs = Math.max(0, AgentRuntimeConfig.cfg.POT_CHECK_RETRY_SOON_MS);
        AgentBotPotionStateRuntime.requestPotionCheckSoon(entry, soonDelayMs);
    }
}
