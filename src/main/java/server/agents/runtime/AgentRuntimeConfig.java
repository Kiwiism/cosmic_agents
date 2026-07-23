package server.agents.runtime;

public final class AgentRuntimeConfig {
    public static final Config cfg = new Config();

    private AgentRuntimeConfig() {
    }

    /**
     * General runtime tunables. Fields are non-final so debug hotswap/live edits
     * keep the legacy mutable behavior.
     */
    public static class Config {
        public int AI_TICK_MS = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.AI_TICK_MS"); // ms between heavier bot decision passes

        // Passive loot
        public int LOOT_RADIUS = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.LOOT_RADIUS"); // px; pickup items within this box radius
        public int INV_FULL_WARN_CD_MS = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.INV_FULL_WARN_CD_MS");

        // Potion management
        public int POT_LOW_WARN = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.POT_LOW_WARN"); // warn on grind start below this count
        public int POT_STOP = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.POT_STOP"); // stop grinding below this HP pot count
        public int POT_CHECK_INTERVAL_MS = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.POT_CHECK_INTERVAL_MS");
        public int POT_CHECK_RETRY_SOON_MS = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.POT_CHECK_RETRY_SOON_MS");
        public int MP_RECOVERY_INTERVAL_MS = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.MP_RECOVERY_INTERVAL_MS");
        public int BASE_HP_RECOVERY = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.BASE_HP_RECOVERY");
        public int BASE_MP_RECOVERY = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.BASE_MP_RECOVERY");
        public float AUTOPOT_HP_THRESH = config.AgentTuning.floatValue("server.agents.runtime.AgentRuntimeConfig.AUTOPOT_HP_THRESH"); // use HP pot when HP falls below this ratio
        public float AUTOPOT_MP_THRESH = config.AgentTuning.floatValue("server.agents.runtime.AgentRuntimeConfig.AUTOPOT_MP_THRESH"); // use MP pot when MP falls below this ratio

        // Follow stagger: each bot is offset this many px from the owner (index-based, alternating left/right)
        public int FOLLOW_STAGGER = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.FOLLOW_STAGGER");

        // Owner inactivity (offline or dead) before bot scrolls/warps to nearest town and idles.
        public long OWNER_INACTIVE_TOWN_RETURN_MS = 5L * 60_000L;

        // Grind recovery is looser than follow recovery so bots can work nearby platforms,
        // but still get pulled back to a same-map party anchor if they fall far out of bounds.
        public int GRIND_PARTY_TELEPORT_DIST_MULTIPLIER = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.GRIND_PARTY_TELEPORT_DIST_MULTIPLIER");

        // Grind loot convenience: loot competes with mob navigation only when
        // lootDistSq < mobDistSq * ratio. 0.09 ~= loot within 30% of mob distance.
        public float GRIND_LOOT_CONVENIENCE_RATIO = config.AgentTuning.floatValue("server.agents.runtime.AgentRuntimeConfig.GRIND_LOOT_CONVENIENCE_RATIO");
        public int GRIND_LOOT_RETRY_SUPPRESS_MS = config.AgentTuning.intValue("server.agents.runtime.AgentRuntimeConfig.GRIND_LOOT_RETRY_SUPPRESS_MS");

        // Debug aid: keep stuck detection/logging active, but disable automatic recovery jumps
        // so pathing failures remain visible in logs and at runtime.
        public boolean ENABLE_UNSTUCK = config.AgentTuning.booleanValue("server.agents.runtime.AgentRuntimeConfig.ENABLE_UNSTUCK");
    }
}
