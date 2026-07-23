package server.agents.capabilities.recovery;

/** Resolves the HP granted after an Agent's delayed town respawn. */
public final class AgentRespawnHealthPolicy {
    private static final int DEFAULT_FIXED_HP = config.AgentTuning.intValue("server.agents.capabilities.recovery.AgentRespawnHealthPolicy.DEFAULT_FIXED_HP");

    private AgentRespawnHealthPolicy() {
    }

    public static int restoredHp(int maxHp, int configuredPercent) {
        int boundedMaxHp = Math.max(1, maxHp);
        if (configuredPercent <= 0) {
            return Math.min(DEFAULT_FIXED_HP, boundedMaxHp);
        }
        int boundedPercent = Math.min(100, configuredPercent);
        return Math.max(1, (int) Math.ceil(boundedMaxHp * boundedPercent / 100.0d));
    }
}
