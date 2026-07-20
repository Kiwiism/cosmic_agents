package server.agents.progression;

import java.util.List;

public record AgentCareerBuildBundleCatalog(int schemaVersion, List<AgentCareerBuildBundle> bundles) {
    public AgentCareerBuildBundleCatalog {
        if (schemaVersion <= 0 || bundles == null || bundles.isEmpty()) {
            throw new IllegalArgumentException("career bundle catalog must contain versioned bundles");
        }
        bundles = List.copyOf(bundles);
    }
}
