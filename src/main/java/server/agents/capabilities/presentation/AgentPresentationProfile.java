package server.agents.capabilities.presentation;

import java.util.Locale;

/** Deployment profile for optional personality and combat presentation. */
public enum AgentPresentationProfile {
    OFF,
    STANDARD;

    public static AgentPresentationProfile current() {
        return parse(config.AgentYamlConfig.config.agent.AGENT_PRESENTATION_PROFILE);
    }

    static AgentPresentationProfile parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("AGENT_PRESENTATION_PROFILE is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(
                    "AGENT_PRESENTATION_PROFILE must be OFF or STANDARD: " + value, invalid);
        }
    }

    public boolean enabled() {
        return this == STANDARD;
    }
}
