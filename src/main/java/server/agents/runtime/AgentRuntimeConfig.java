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
        public int AI_TICK_MS = 100; // ms between heavier bot decision passes

        // Passive loot
        public int LOOT_RADIUS = 100; // px; pickup items within this box radius
        public int INV_FULL_WARN_CD_MS = 10_000;

        // Potion management
        public int POT_LOW_WARN = 100; // warn on grind start below this count
        public int POT_STOP = 10; // stop grinding below this HP pot count
        public int POT_CHECK_INTERVAL_MS = 45_000;
        public int POT_CHECK_RETRY_SOON_MS = 250;
        public int MP_RECOVERY_INTERVAL_MS = 10_000;
        public int BASE_HP_RECOVERY = 10;
        public int BASE_MP_RECOVERY = 3;
        public float AUTOPOT_HP_THRESH = 0.7f; // use HP pot when HP falls below this ratio
        public float AUTOPOT_MP_THRESH = 0.5f; // use MP pot when MP falls below this ratio

        // Follow stagger: each bot is offset this many px from the owner (index-based, alternating left/right)
        public int FOLLOW_STAGGER = 60;

        // Owner inactivity (offline or dead) before bot scrolls/warps to nearest town and idles.
        public long OWNER_INACTIVE_TOWN_RETURN_MS = 5L * 60_000L;

        // Grind recovery is looser than follow recovery so bots can work nearby platforms,
        // but still get pulled back to a same-map party anchor if they fall far out of bounds.
        public int GRIND_PARTY_TELEPORT_DIST_MULTIPLIER = 2;

        // Grind loot convenience: loot competes with mob navigation only when
        // lootDistSq < mobDistSq * ratio. 0.09 ~= loot within 30% of mob distance.
        public float GRIND_LOOT_CONVENIENCE_RATIO = 0.09f;
        public int GRIND_LOOT_RETRY_SUPPRESS_MS = 5_000;

        // Debug aid: keep stuck detection/logging active, but disable automatic recovery jumps
        // so pathing failures remain visible in logs and at runtime.
        public boolean ENABLE_UNSTUCK = false;
    }
}
