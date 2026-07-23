package server.agents.capabilities.townlife;

import java.util.List;

/** Immutable, bounded view exposed to deterministic or external TownLife controllers. */
public record AgentTownLifeDecisionContext(
        int agentId,
        int townMapId,
        String profileId,
        AgentTownLifeState.Stage currentStage,
        AgentTownLifeState.Activity currentActivity,
        String currentVenueId,
        AgentTownLifeState.Role role,
        AgentTownLifeState.District homeDistrict,
        AgentTownLifeState.PlatformKind platformPreference,
        int townAgentCount,
        boolean realObserverPresent,
        PersonalityView personality,
        List<AgentTownLifeState.Activity> recentActivities,
        EncounterView encounter,
        List<VenueView> venues,
        long nowMs,
        long decisionSequence) {

    public AgentTownLifeDecisionContext {
        if (agentId <= 0 || townMapId <= 0 || profileId == null || profileId.isBlank()
                || currentStage == null || currentActivity == null || currentVenueId == null
                || role == null || homeDistrict == null || platformPreference == null
                || townAgentCount < 0 || personality == null || venues == null
                || recentActivities == null || encounter == null || nowMs < 0 || decisionSequence < 0) {
            throw new IllegalArgumentException("valid immutable TownLife decision context is required");
        }
        venues = List.copyOf(venues);
        recentActivities = List.copyOf(recentActivities);
    }

    public record PersonalityView(int patience,
                                  int activity,
                                  int curiosity,
                                  int sociability,
                                  int routinePreference,
                                  int expressiveness) {
        public static PersonalityView neutral() {
            return new PersonalityView(50, 50, 50, 50, 50, 50);
        }
    }

    public record VenueView(String id,
                            String label,
                            AgentTownLifeState.District district,
                            AgentTownLifeState.PlatformKind platformKind,
                            int capacity,
                            int currentOccupancy,
                            List<AgentTownLifeProfile.Affordance> affordances) {
        public VenueView {
            affordances = List.copyOf(affordances == null ? List.of() : affordances);
        }
    }

    public record EncounterView(boolean active,
                                String type,
                                String phase,
                                int peerAgentId,
                                int turnOwnerAgentId) {
        public EncounterView {
            type = type == null ? "" : type;
            phase = phase == null ? "" : phase;
        }

        public static EncounterView none() {
            return new EncounterView(false, "", "", 0, 0);
        }
    }
}
