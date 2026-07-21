package server.agents.progression;

/** Durable decision preferences for autonomous Victoria Island progression. */
public record AgentProgressionProfile(
        String profileId,
        int profileVersion,
        int questPreference,
        int grindPreference,
        int efficiencyPreference,
        int explorationPreference,
        int crowdAvoidance,
        int travelTolerance,
        int riskTolerance,
        int routinePreference) {

    public AgentProgressionProfile {
        if (profileId == null || profileId.isBlank() || profileVersion <= 0
                || invalid(questPreference) || invalid(grindPreference)
                || questPreference + grindPreference == 0
                || invalid(efficiencyPreference) || invalid(explorationPreference)
                || invalid(crowdAvoidance) || invalid(travelTolerance)
                || invalid(riskTolerance) || invalid(routinePreference)) {
            throw new IllegalArgumentException("a versioned progression profile with 0-100 preferences is required");
        }
        profileId = profileId.trim();
    }

    private static boolean invalid(int value) {
        return value < 0 || value > 100;
    }
}
