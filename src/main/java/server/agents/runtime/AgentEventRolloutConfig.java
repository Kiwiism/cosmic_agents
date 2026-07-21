package server.agents.runtime;

/** Independent startup gates for event consumers while publication remains observable. */
public record AgentEventRolloutConfig(
        boolean reactionsEnabled,
        boolean dialogueEnabled,
        boolean coordinationEnabled,
        boolean llmContextEnabled) {

    public static AgentEventRolloutConfig fromSystemProperties() {
        return new AgentEventRolloutConfig(
                enabled("agents.events.reactions.enabled"),
                enabled("agents.events.dialogue.enabled"),
                enabled("agents.events.coordination.enabled"),
                enabled("agents.events.llmContext.enabled"));
    }

    private static boolean enabled(String property) {
        return Boolean.parseBoolean(System.getProperty(property, "true"));
    }
}
