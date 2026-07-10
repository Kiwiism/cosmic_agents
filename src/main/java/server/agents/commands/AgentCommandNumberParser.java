package server.agents.commands;

public final class AgentCommandNumberParser {
    private AgentCommandNumberParser() {
    }

    public static Integer parseIntInRange(String value, int minimum, int maximum) {
        if (value == null || minimum > maximum) {
            return null;
        }

        try {
            int parsed = Integer.parseInt(value);
            return parsed >= minimum && parsed <= maximum ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
