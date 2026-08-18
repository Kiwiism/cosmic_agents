package server.agents.capabilities.supplies;

/** Inventory thresholds owned by the supplies capability. */
public final class AgentSupplyConfig {
    public static final Config cfg = new Config();

    private AgentSupplyConfig() {
    }

    public static final class Config {
        public int AMMO_LOW_WARN = config.AgentTuning.intValue(
                "server.agents.capabilities.supplies.AgentSupplyConfig.AMMO_LOW_WARN");
    }
}
