package server.agents.behavior;

import java.util.List;

public record AgentBehaviorPolicyCatalog(int schemaVersion,
                                         String defaultPersonalityProfileId,
                                         List<AgentBehaviorPolicyProfile> policies) {
    public AgentBehaviorPolicyCatalog {
        if (schemaVersion <= 0 || defaultPersonalityProfileId == null || policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("valid behavior policy catalog is required");
        }
        policies = List.copyOf(policies);
    }
}
