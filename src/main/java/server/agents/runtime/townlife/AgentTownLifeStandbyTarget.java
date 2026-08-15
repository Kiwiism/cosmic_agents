package server.agents.runtime.townlife;

/** Caller-owned location where a test Agent waits between TownLife sessions. */
public record AgentTownLifeStandbyTarget(Type type, String value) {
    public AgentTownLifeStandbyTarget {
        type = type == null ? Type.FALLBACK : type;
        value = value == null ? "" : value.trim();
        if ((type == Type.PORTAL || type == Type.NPC || type == Type.FACILITY)
                && value.isBlank()) {
            throw new IllegalArgumentException("standby target value is required for " + type);
        }
        if (type == Type.NPC) {
            try {
                if (Integer.parseInt(value) <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("NPC standby target requires a positive NPC id");
            }
        }
    }

    public static AgentTownLifeStandbyTarget fallback() {
        return new AgentTownLifeStandbyTarget(Type.FALLBACK, "");
    }

    public static AgentTownLifeStandbyTarget parse(String token) {
        if (token == null || token.isBlank() || "fallback".equalsIgnoreCase(token)) {
            return fallback();
        }
        int separator = token.indexOf(':');
        if (separator <= 0 || separator == token.length() - 1) {
            throw new IllegalArgumentException(
                    "standby must be fallback, portal:<name>, npc:<id>, or facility:<id>");
        }
        Type type;
        try {
            type = Type.valueOf(token.substring(0, separator).trim()
                    .toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("unknown standby target " + token);
        }
        return new AgentTownLifeStandbyTarget(type, token.substring(separator + 1));
    }

    public String display() {
        return type == Type.FALLBACK ? "a safe local standby point"
                : type.name().toLowerCase(java.util.Locale.ROOT) + ':' + value;
    }

    public enum Type {
        FALLBACK,
        PORTAL,
        NPC,
        FACILITY
    }
}
