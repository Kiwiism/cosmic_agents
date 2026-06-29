package server.agents.capabilities.combat;

public final class AgentCombatConfig {
    public static final Config cfg = new Config();

    private AgentCombatConfig() {
    }

    public static class Config {
        // Physics (combat use only)
        // OpenStory Player::damage sets hspeed = +/-1.5 and vforce -= 3.5 on mob knockback.
        public float KNOCKBACK_HSPEED = 1.5f;
        public float KNOCKBACK_VFORCE = 3.5f;

        // Basic attack fallback when weapon data cannot produce a real normal-attack hit box.
        public int ATTACK_RANGE_X = 80;
        public int ATTACK_RANGE_Y = 50;
        public int ATTACK_DOWN_MAX = 20;
        public int ATTACK_JUMP_Y = 130;
        public int ATTACK_JUMP_X_EXTRA = 60;
        public int RANGED_DEGENERATE_RANGE_X = 50;
        public int RANGED_DEGENERATE_RANGE_Y = 50;
        public int RANGED_RETREAT_THRESHOLD_X = 80;
        public int RANGED_RETREAT_DISTANCE_X = 100;
        public int BREAKOUT_MAX_MS = 3000;

        // Ammo
        public int AMMO_LOW_WARN = 500;

        // Grind / AoE
        public int GRIND_SEEK_RANGE = 800;
        public int GRIND_RETARGET_INTERVAL_MS = 400;
        public int AOE_MOB_THRESHOLD = 2;
        // AoE repositioning: when the best fire-now plan is single-target but stepping into the
        // cluster centroid would let the AoE skill beat it by this DPS factor, defer the shot and
        // walk in. Bounded by distance/time so the bot never chases scattering mobs.
        public boolean AOE_REPOSITION_ENABLED = true;
        public boolean AOE_REPOSITION_DEBUG = false;
        public double AOE_REPOSITION_DPS_FACTOR = 1.5d;
        public int AOE_REPOSITION_MAX_DISTANCE_X = 150;
        public int AOE_REPOSITION_ARRIVAL_X = 20;
        public long AOE_REPOSITION_MAX_MS = 800L;
        public int GRIND_REGION_OCCUPANCY_PENALTY = 1200;
        public int GRIND_REGION_OCCUPANCY_PENALTY_CAP = 3600;

        // Mob damage
        public int MOB_TOUCH_SWEEP_HEIGHT = 50;
        public int MOB_HIT_COOLDOWN_MS = 1500;
        public long BOT_DEAD_MS = 30_000L;

        // Support
        public int SUPPORT_RANGE = 400;
        public int SUPPORT_VERTICAL_RANGE = 220;
        public int SUPPORT_REBUFF_CD_MS = 3_000;
        // Heal until every member in range (including the cleric itself) is above this HP ratio.
        // Cadence: animation lock (attackCooldownMs) then HEAL_MOVE_WINDOW_MS walk window.
        // Heal is also gated by moveWindowMs > 0, so it cannot fire mid-attack-movement-window.
        public float SUPPORT_HEAL_TARGET_RATIO = 0.90f;
        public int HEAL_MOVE_WINDOW_MS = 600;
        // Jump-heal: while following, if the leader is at least this many px ahead horizontally,
        // kick a diagonal jump toward them just before the heal cast so the bot keeps closing
        // distance instead of stopping to plant the heal animation. 0 disables.
        public int JUMP_HEAL_LEADER_AHEAD_PX = 80;
    }
}
