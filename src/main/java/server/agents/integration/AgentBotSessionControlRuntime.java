package server.agents.integration;

import server.agents.runtime.AgentLeaderSafetyService;
import server.bots.BotEntry;

/**
 * Agent-owned session control boundary over temporary BotEntry-backed session
 * storage and owner-away side effects.
 */
public final class AgentBotSessionControlRuntime {
    private AgentBotSessionControlRuntime() {
    }

    public static boolean isPrimarySession(BotEntry entry) {
        if (entry == null || AgentBotRuntimeIdentityRuntime.owner(entry) == null) {
            return false;
        }
        return AgentBotSessionLifecycleSideEffects.getBotEntries(AgentBotRuntimeIdentityRuntime.ownerId(entry))
                .stream()
                .findFirst()
                .filter(first -> first == entry)
                .isPresent();
    }

    public static boolean shouldOfferTownForAwayCommand(BotEntry entry) {
        return AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(AgentBotRuntimeIdentityRuntime.botMap(entry));
    }

    public static void issueOwnerAwaySafeModeForLeader(int leaderCharId, boolean town) {
        AgentBotSessionLifecycleSideEffects.issueOwnerAwaySafeModeForLeader(leaderCharId, town);
    }
}
