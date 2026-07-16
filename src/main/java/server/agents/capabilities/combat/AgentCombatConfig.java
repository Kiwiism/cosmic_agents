package server.agents.capabilities.combat;

import config.YamlConfig;
import server.agents.capabilities.mobcontrol.AgentMobReactionMode;
import server.agents.capabilities.mobcontrol.AgentMobReactionRouter;

import java.util.ArrayList;
import java.util.List;

public final class AgentCombatConfig {
    public static final Config cfg = new Config();

    private AgentCombatConfig() {
    }

    /** Admin/debug: "FIELD = value" for every public combat config field, sorted. */
    public static List<String> configFieldLines() {
        List<String> out = new ArrayList<>();
        for (java.lang.reflect.Field f : Config.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isPublic(f.getModifiers())) {
                continue;
            }
            try {
                out.add(f.getName() + " = " + f.get(cfg));
            } catch (IllegalAccessException ignored) {
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Admin/debug: "FIELD = value" for one field (case-insensitive), or null if unknown. */
    public static String configFieldLine(String name) {
        for (java.lang.reflect.Field f : Config.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isPublic(f.getModifiers()) && f.getName().equalsIgnoreCase(name)) {
                try {
                    return f.getName() + " = " + f.get(cfg);
                } catch (IllegalAccessException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Set a combat config field by name (case-insensitive) on the live cfg.
     * Returns a human-readable result; success messages start with "OK".
     */
    public static String setConfigField(String name, String rawValue) {
        for (java.lang.reflect.Field f : Config.class.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isPublic(f.getModifiers()) || !f.getName().equalsIgnoreCase(name)) {
                continue;
            }
            try {
                Object previous = f.get(cfg);
                Object parsed = parseConfigValue(f.getType(), rawValue.trim());
                f.set(cfg, parsed);
                if (f.getName().equals("AGENT_MOB_REACTION_MODE")) {
                    AgentMobReactionRouter.modeChanged(
                            (AgentMobReactionMode) previous, (AgentMobReactionMode) parsed);
                }
                return "OK: " + f.getName() + " = " + parsed;
            } catch (NumberFormatException e) {
                return "bad value '" + rawValue + "' for " + f.getName() + " (" + f.getType().getSimpleName() + ")";
            } catch (IllegalAccessException e) {
                return "cannot set " + f.getName();
            }
        }
        return "unknown field: " + name;
    }

    private static Object parseConfigValue(Class<?> type, String v) {
        if (type == boolean.class || type == Boolean.class) {
            if (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("on")) {
                return Boolean.TRUE;
            }
            if (v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("off")) {
                return Boolean.FALSE;
            }
            throw new NumberFormatException(v);
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(v);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(v);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(v);
        }
        if (type == float.class || type == Float.class) {
            return Float.parseFloat(v);
        }
        if (type == AgentMobReactionMode.class) {
            try {
                return AgentMobReactionMode.parse(v);
            } catch (IllegalArgumentException invalid) {
                throw new NumberFormatException(invalid.getMessage());
            }
        }
        throw new NumberFormatException(v);
    }

    public static class Config {
        // Physics (combat use only)
        // OpenStory Player::damage sets hspeed = +/-1.5 and vforce -= 3.5 on mob knockback.
        public float KNOCKBACK_HSPEED = 1.5f;
        public float KNOCKBACK_VFORCE = 3.5f;
        public AgentMobReactionMode AGENT_MOB_REACTION_MODE = AgentMobReactionMode.parse(
                YamlConfig.config.server.AGENT_MOB_REACTION_MODE);
        public int SYNTHETIC_MOB_KNOCKBACK_DISTANCE_X =
                YamlConfig.config.server.AGENT_SYNTHETIC_MOB_KNOCKBACK_DISTANCE_X;
        public int SYNTHETIC_MOB_KNOCKBACK_DURATION_MS =
                YamlConfig.config.server.AGENT_SYNTHETIC_MOB_KNOCKBACK_DURATION_MS;
        public int SYNTHETIC_MOB_CONTROL_HOLD_MS =
                YamlConfig.config.server.AGENT_SYNTHETIC_MOB_CONTROL_HOLD_MS;
        public int MOB_PHYSICS_PUBLICATION_INTERVAL_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_PUBLICATION_INTERVAL_MS;
        public int MOB_PHYSICS_MAX_CATCH_UP_STEPS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_MAX_CATCH_UP_STEPS;
        public int MOB_PHYSICS_STOP_DISTANCE_X =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_STOP_DISTANCE_X;
        public int MOB_PHYSICS_RESUME_DISTANCE_X =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_RESUME_DISTANCE_X;
        public int MOB_PHYSICS_FLY_DEAD_ZONE_X =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_FLY_DEAD_ZONE_X;
        public int MOB_PHYSICS_FLY_DEAD_ZONE_Y =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_FLY_DEAD_ZONE_Y;
        public int MOB_PHYSICS_JUMP_COOLDOWN_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_JUMP_COOLDOWN_MS;
        public int MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS;
        public int MOB_PHYSICS_JUMP_TARGET_HEIGHT =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_JUMP_TARGET_HEIGHT;
        public int MOB_PHYSICS_MAX_SAFE_EDGE_PX =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_MAX_SAFE_EDGE_PX;
        public int MOB_PHYSICS_LEFT_EDGE_INSET_PX =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_LEFT_EDGE_INSET_PX;
        public int MOB_PHYSICS_RIGHT_EDGE_INSET_PX =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_RIGHT_EDGE_INSET_PX;
        public int MOB_PHYSICS_SPEED_PERCENT =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_SPEED_PERCENT;
        public int MOB_PHYSICS_BEHAVIOR_JITTER_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_BEHAVIOR_JITTER_MS;
        public int MOB_PHYSICS_DIRECTION_REACTION_MAX_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_DIRECTION_REACTION_MAX_MS;
        public int MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT;
        public int MOB_PHYSICS_EDGE_IDLE_MIN_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_EDGE_IDLE_MIN_MS;
        public int MOB_PHYSICS_EDGE_IDLE_MAX_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_EDGE_IDLE_MAX_MS;
        public int MOB_PHYSICS_EDGE_RETREAT_MIN_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_EDGE_RETREAT_MIN_MS;
        public int MOB_PHYSICS_EDGE_RETREAT_MAX_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_EDGE_RETREAT_MAX_MS;
        public int MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX;
        public int MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX;
        public int MOB_PHYSICS_STUCK_DETECT_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_STUCK_DETECT_MS;
        public int MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT;
        public int MOB_PHYSICS_KNOCKBACK_PERCENT =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_KNOCKBACK_PERCENT;
        public int MOB_PHYSICS_IMPACT_DELAY_PERCENT =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_IMPACT_DELAY_PERCENT;
        public int MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS;
        public boolean MOB_PHYSICS_DIAGNOSTIC_LOGGING =
                YamlConfig.config.server.AGENT_MOB_PHYSICS_DIAGNOSTIC_LOGGING;

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
