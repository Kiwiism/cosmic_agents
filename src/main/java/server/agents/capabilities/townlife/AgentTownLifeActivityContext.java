package server.agents.capabilities.townlife;

import java.util.List;
import java.util.Map;

/** Immutable deterministic-policy input assembled by the Cosmic adapter. */
record AgentTownLifeActivityContext(int agentId,
                                    long behaviorSeed,
                                    boolean initialPlacementComplete,
                                    AgentTownLifeState.Role role,
                                    int sequence,
                                    AgentTownLifeDecisionContext.PersonalityView personality,
                                    boolean personalityAssigned,
                                    Map<AgentTownLifeState.Activity, Integer> profileWeights,
                                    List<AgentTownLifeState.Activity> recentActivities) {
    AgentTownLifeActivityContext {
        if (agentId <= 0 || role == null || sequence < 0 || personality == null
                || profileWeights == null || recentActivities == null) {
            throw new IllegalArgumentException("valid immutable TownLife activity context is required");
        }
        profileWeights = Map.copyOf(profileWeights);
        recentActivities = List.copyOf(recentActivities);
    }
}
