package server.agents.runtime;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentSessionLifecycleSideEffects;

/**
 * Agent-owned session control boundary over AgentRuntimeEntry-backed session
 * storage and owner-away side effects.
 */
public final class AgentSessionControlRuntime {
    private AgentSessionControlRuntime() {
    }

    public static boolean isPrimarySession(AgentRuntimeEntry entry) {
        if (entry == null || AgentRuntimeIdentityRuntime.owner(entry) == null) {
            return false;
        }
        return AgentSessionLifecycleSideEffects.getBotEntries(AgentRuntimeIdentityRuntime.ownerId(entry))
                .stream()
                .findFirst()
                .filter(first -> first == entry)
                .isPresent();
    }

    public static boolean shouldOfferTownForAwayCommand(AgentRuntimeEntry entry) {
        return AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(AgentRuntimeIdentityRuntime.botMap(entry));
    }

    public static void issueOwnerAwaySafeModeForLeader(int leaderCharId, boolean town) {
        AgentSessionLifecycleSideEffects.issueOwnerAwaySafeModeForLeader(leaderCharId, town);
    }
}
