package server.agents.personality;

import java.util.List;

public record AgentPersonalityProfileCatalog(
        int schemaVersion,
        String defaultProfileId,
        List<AgentPersonalityProfile> profiles) {
    public AgentPersonalityProfileCatalog {
        if (schemaVersion <= 0 || defaultProfileId == null || defaultProfileId.isBlank()
                || profiles == null || profiles.isEmpty()) {
            throw new IllegalArgumentException("personality catalog identity, default, and profiles are required");
        }
        defaultProfileId = defaultProfileId.trim();
        profiles = List.copyOf(profiles);
    }
}
