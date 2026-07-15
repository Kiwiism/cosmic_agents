package server.agents.capabilities.quest;

import config.YamlConfig;
import server.agents.integration.cosmic.CosmicAmherstTestResetPort;

import java.util.Set;

public final class AmherstTestResetService {
    private AmherstTestResetService() {
    }

    public static AmherstTestResetHarness configuredHarness() {
        AmherstTestResetConfig config = AmherstTestResetConfig.fromSystemProperties();
        return new GuardedAmherstTestResetHarness(
                config.enabled(),
                config.allowedCharacterIds(),
                config.allowedCharacterNames(),
                CosmicAmherstTestResetPort.INSTANCE);
    }

    public static AmherstTestResetHarness showcaseHarness() {
        String agentName = YamlConfig.config.server.AGENT_AMHERST_SHOWCASE_AGENT_NAME;
        boolean enabled = YamlConfig.config.server.AGENT_AMHERST_SHOWCASE_ENABLED
                && agentName != null && !agentName.isBlank();
        return showcaseHarness(enabled, agentName);
    }

    public static AmherstTestResetHarness showcaseHarness(boolean enabled, String agentName) {
        enabled = enabled && agentName != null && !agentName.isBlank();
        return new GuardedAmherstTestResetHarness(
                enabled,
                Set.of(),
                enabled ? Set.of(agentName) : Set.of(),
                CosmicAmherstTestResetPort.INSTANCE);
    }
}
