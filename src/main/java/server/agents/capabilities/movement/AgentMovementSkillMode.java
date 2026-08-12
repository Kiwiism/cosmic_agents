package server.agents.capabilities.movement;

import java.util.Locale;

/** Rollout state for one navigation-level movement skill. */
public enum AgentMovementSkillMode {
    OFF,
    SHADOW,
    ACTIVE;

    static AgentMovementSkillMode parse(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(
                    key + " must be OFF, SHADOW, or ACTIVE: " + value, invalid);
        }
    }

    public boolean active() {
        return this == ACTIVE;
    }

    public boolean visibleToShadowRouting() {
        return this == SHADOW || this == ACTIVE;
    }

    public boolean shadowOnly() {
        return this == SHADOW;
    }
}
