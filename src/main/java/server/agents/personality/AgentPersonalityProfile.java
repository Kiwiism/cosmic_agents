package server.agents.personality;

/** Durable semantic traits. Runtime tuning belongs to presentation policy, not identity. */
public record AgentPersonalityProfile(
        String profileId,
        int profileVersion,
        Traits traits) {

    public AgentPersonalityProfile {
        if (profileId == null || profileId.isBlank() || profileVersion <= 0 || traits == null) {
            throw new IllegalArgumentException("versioned personality identity and traits are required");
        }
        profileId = profileId.trim();
    }

    public record Traits(
            int activity,
            int patience,
            int expressiveness,
            int curiosity,
            int sociability,
            int riskTolerance,
            int routinePreference) {
        public Traits {
            if (invalid(activity) || invalid(patience) || invalid(expressiveness)
                    || invalid(curiosity) || invalid(sociability) || invalid(riskTolerance)
                    || invalid(routinePreference)) {
                throw new IllegalArgumentException("personality traits must be between 0 and 100");
            }
        }

        private static boolean invalid(int value) {
            return value < 0 || value > 100;
        }
    }
}
