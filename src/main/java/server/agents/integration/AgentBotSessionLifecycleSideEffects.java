package server.agents.integration;

import client.Character;

import java.util.List;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLeaderSafetyRuntime;
import server.agents.runtime.AgentRuntimeRegistry;
import server.bots.BotEntry;

/**
 * Temporary bot-side gateway for session lifecycle side effects while
 * orchestration moves into Agent modules.
 */
public final class AgentBotSessionLifecycleSideEffects {
    private AgentBotSessionLifecycleSideEffects() {
    }

    public static void reloginBot(int charId, int ownerCharId, int world, int channel) {
        AgentInteractionRuntime.reloginAgent(charId, ownerCharId, world, channel);
    }

    public static List<BotEntry> getBotEntries(int ownerCharId) {
        return AgentRuntimeRegistry.entriesForLeader(ownerCharId);
    }

    public static BotEntry getBotEntry(int ownerCharId, String agentName) {
        return AgentRuntimeRegistry.findByName(ownerCharId, agentName);
    }

    public static void issueOwnerAwaySafeModeForLeader(int ownerCharId, boolean town) {
        AgentLeaderSafetyRuntime.issueInactiveSafeModeForLeader(ownerCharId, town);
    }

    public static Character activeLeaderByAgentCharacterId(int agentCharId) {
        return AgentRuntimeRegistry.activeLeaderByAgentCharacterId(agentCharId);
    }
}
