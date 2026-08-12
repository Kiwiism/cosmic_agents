package server.agents.capabilities.navigation;

import java.util.Locale;

/** Controls whether observed edge failures influence route selection. */
public enum AgentNavigationReliabilityMode {
    OBSERVE,
    ACTIVE;

    static AgentNavigationReliabilityMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Navigation reliability mode is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(
                    "Navigation reliability mode must be OBSERVE or ACTIVE: " + value,
                    invalid);
        }
    }
}
