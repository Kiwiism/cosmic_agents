package server.agents.integration;

import client.Character;

import java.util.List;
import server.bots.BotEntry;
import server.bots.BotManager;

/**
 * Temporary bot-side gateway for session lifecycle side effects that still need
 * BotManager package access while orchestration moves into Agent modules.
 */
public final class AgentBotSessionLifecycleSideEffects {
    private AgentBotSessionLifecycleSideEffects() {
    }

    public static void reloginBot(int charId, int ownerCharId, int world, int channel) {
        BotManager.getInstance().reloginBot(charId, ownerCharId, world, channel);
    }

    public static List<BotEntry> getBotEntries(int ownerCharId) {
        return BotManager.getInstance().getBotEntries(ownerCharId);
    }

    public static BotEntry getBotEntry(int ownerCharId, String agentName) {
        return BotManager.getInstance().getBotEntry(ownerCharId, agentName);
    }

    public static void issueOwnerAwaySafeModeForLeader(int ownerCharId, boolean town) {
        BotManager.getInstance().issueOwnerAwaySafeModeForOwner(ownerCharId, town);
    }

    public static Character activeLeaderByAgentCharacterId(int agentCharId) {
        return BotManager.getInstance().getActiveOwnerByBotCharId(agentCharId);
    }
}
