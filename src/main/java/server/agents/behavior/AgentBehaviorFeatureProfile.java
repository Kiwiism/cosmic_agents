package server.agents.behavior;

import java.util.Locale;

/** Deployment profile for optional personality-driven decision variation. */
public enum AgentBehaviorFeatureProfile {
    OFF,
    STANDARD;

    public static AgentBehaviorFeatureProfile current() {
        return parse(config.AgentYamlConfig.config.agent.AGENT_BEHAVIOR_PROFILE);
    }

    static AgentBehaviorFeatureProfile parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("AGENT_BEHAVIOR_PROFILE is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(
                    "AGENT_BEHAVIOR_PROFILE must be OFF or STANDARD: " + value, invalid);
        }
    }

    public boolean enabled() {
        return this == STANDARD;
    }
}
