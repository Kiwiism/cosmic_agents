package server.agents.runtime;

import config.YamlConfig;

/** Temporary rollback gate for owner-shaped command aliases during migration. */
public final class AgentLegacyOwnerCompatibility {
    private AgentLegacyOwnerCompatibility() {
    }

    public static boolean enabled() {
        return config.AgentYamlConfig.config.agent.AGENT_LEGACY_OWNER_COMPATIBILITY_ENABLED;
    }
}
