package server.agents.capabilities.build.profiles;

import java.util.List;

public record AgentApBuildProfileCatalog(int schemaVersion, List<AgentApBuildProfile> profiles) {
    public AgentApBuildProfileCatalog {
        if (schemaVersion <= 0 || profiles == null || profiles.isEmpty()) {
            throw new IllegalArgumentException("AP build profile catalog is required");
        }
        profiles = List.copyOf(profiles);
    }
}
