package server.agents.progression;

import java.util.List;

public record AgentProgressionProfileCatalog(
        int schemaVersion,
        String defaultProfileId,
        List<AgentProgressionProfile> profiles) {

    public AgentProgressionProfileCatalog {
        if (schemaVersion <= 0 || defaultProfileId == null || defaultProfileId.isBlank()
                || profiles == null || profiles.isEmpty()) {
            throw new IllegalArgumentException("a default and at least one progression profile are required");
        }
        profiles = List.copyOf(profiles);
    }
}
