package server.agents.runtime;

import server.agents.capabilities.recovery.AgentLeaderSafetyService;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentSessionLifecycleRuntime;

/**
 * Agent-owned session control boundary over AgentRuntimeEntry-backed session
 * storage and owner-away side effects.
 */
public final class AgentSessionControlRuntime {
    private AgentSessionControlRuntime() {
    }

    public static boolean isPrimarySession(AgentRuntimeEntry entry) {
        if (entry == null) {
            return false;
        }
        return AgentRuntimeRegistry.entriesForCohort(AgentRelationshipRuntime.cohortId(entry))
                .stream()
                .findFirst()
                .filter(first -> first == entry)
                .isPresent();
    }

    public static boolean shouldOfferTownForAwayCommand(AgentRuntimeEntry entry) {
        return AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(AgentRuntimeIdentityRuntime.botMap(entry));
    }

    public static void issueOwnerAwaySafeModeForLeader(int leaderCharId, boolean town) {
        AgentSessionLifecycleRuntime.issueOwnerAwaySafeModeForLeader(leaderCharId, town);
    }
}
