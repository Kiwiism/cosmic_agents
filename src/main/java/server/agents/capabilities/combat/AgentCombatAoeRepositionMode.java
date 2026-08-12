package server.agents.capabilities.combat;

import java.util.Locale;

/** Explicit rollout mode for the bounded AoE reposition tactic. */
public enum AgentCombatAoeRepositionMode {
    OFF,
    ACTIVE;

    public static AgentCombatAoeRepositionMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AOE_REPOSITION_MODE is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "AOE_REPOSITION_MODE must be OFF or ACTIVE: " + value, invalid);
        }
    }

    public boolean enabled() {
        return this == ACTIVE;
    }
}
