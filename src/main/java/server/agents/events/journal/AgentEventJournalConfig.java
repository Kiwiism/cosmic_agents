package server.agents.events.journal;

import java.nio.file.Path;

/** Process-level configuration for the bounded Agent event journal. */
public record AgentEventJournalConfig(
        boolean enabled,
        Path path,
        int capacity,
        long maxFileBytes) {
    static final int DEFAULT_CAPACITY = 8192;
    static final long DEFAULT_MAX_FILE_BYTES = 64L * 1024L * 1024L;

    public AgentEventJournalConfig {
        if (path == null || capacity <= 0 || maxFileBytes <= 0) {
            throw new IllegalArgumentException("Valid journal path and bounds are required");
        }
    }

    public static AgentEventJournalConfig fromSystemProperties() {
        return new AgentEventJournalConfig(
                Boolean.parseBoolean(System.getProperty("agents.events.journal.enabled", "false")),
                Path.of(System.getProperty("agents.events.journal.path",
                        "runtime/agents/events/agent-events.jsonl")),
                positiveInt("agents.events.journal.capacity", DEFAULT_CAPACITY),
                positiveLong("agents.events.journal.maxFileBytes", DEFAULT_MAX_FILE_BYTES));
    }

    private static int positiveInt(String property, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(System.getProperty(property, String.valueOf(fallback))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long positiveLong(String property, long fallback) {
        try {
            return Math.max(1L, Long.parseLong(System.getProperty(property, String.valueOf(fallback))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
