package server.agents.capabilities.mobcontrol;

import java.util.Locale;

/** The single authoritative Agent-to-monster reaction mode. */
public enum AgentMobReactionMode {
    OFF,
    SYNTHETIC,
    PHYSICS;

    public static AgentMobReactionMode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AGENT_MOB_REACTION_MODE is required (OFF, SYNTHETIC, or PHYSICS)");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid AGENT_MOB_REACTION_MODE '" + value
                    + "'; expected OFF, SYNTHETIC, or PHYSICS", invalid);
        }
    }
}
