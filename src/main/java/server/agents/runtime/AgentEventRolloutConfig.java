package server.agents.runtime;

import config.YamlConfig;

/** Independent startup gates for event consumers while publication remains observable. */
public record AgentEventRolloutConfig(
        boolean reactionsEnabled,
        boolean dialogueEnabled,
        boolean coordinationEnabled,
        boolean llmContextEnabled) {

    public static AgentEventRolloutConfig fromSystemProperties() {
        return new AgentEventRolloutConfig(
                enabled("agents.events.reactions.enabled"),
                config.AgentYamlConfig.config.agent.AGENT_LEGACY_DIALOGUE_ENABLED
                        && dialogueTransportEnabled(),
                enabled("agents.events.coordination.enabled"),
                enabled("agents.events.llmContext.enabled"));
    }

    static boolean dialogueTransportEnabled() {
        return enabled("agents.events.dialogue.enabled");
    }

    private static boolean enabled(String property) {
        return Boolean.parseBoolean(System.getProperty(property, "true"));
    }
}
