package server.agents.capabilities.recovery;

/** Death and respawn tuning owned by the recovery capability. */
public final class AgentDeathConfig {
    public static final Config cfg = new Config();

    private AgentDeathConfig() {
    }

    public static final class Config {
        public long RESPAWN_DELAY_MS = config.AgentYamlConfig.config.agent.AGENT_DEATH_RESPAWN_DELAY_MS;
        public int RESPAWN_HP_PERCENT = config.AgentYamlConfig.config.agent.AGENT_DEATH_RESPAWN_HP_PERCENT;
    }
}
