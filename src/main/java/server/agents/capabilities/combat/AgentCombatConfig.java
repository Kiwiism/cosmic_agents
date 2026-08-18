package server.agents.capabilities.combat;

import config.YamlConfig;
import server.agents.capabilities.mobcontrol.AgentMobReactionMode;
import server.agents.capabilities.mobcontrol.AgentMobReactionRouter;
import server.agents.capabilities.mobcontrol.AgentMobPhysicsConfig;

import java.util.ArrayList;
import java.util.List;

public final class AgentCombatConfig {
    public static final Config cfg = new Config();

    private AgentCombatConfig() {
    }

    /** Admin/debug: "FIELD = value" for every public combat config field, sorted. */
    public static List<String> configFieldLines() {
        List<String> out = new ArrayList<>();
        for (java.lang.reflect.Field f : configFields()) {
            if (!java.lang.reflect.Modifier.isPublic(f.getModifiers())) {
                continue;
            }
            try {
                out.add(f.getName() + " = " + f.get(fieldOwner(f)));
            } catch (IllegalAccessException ignored) {
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Admin/debug: "FIELD = value" for one field (case-insensitive), or null if unknown. */
    public static String configFieldLine(String name) {
        for (java.lang.reflect.Field f : configFields()) {
            if (java.lang.reflect.Modifier.isPublic(f.getModifiers()) && f.getName().equalsIgnoreCase(name)) {
                try {
                    return f.getName() + " = " + f.get(fieldOwner(f));
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
        for (java.lang.reflect.Field f : configFields()) {
            if (!java.lang.reflect.Modifier.isPublic(f.getModifiers()) || !f.getName().equalsIgnoreCase(name)) {
                continue;
            }
            try {
                Object owner = fieldOwner(f);
                Object previous = f.get(owner);
                Object parsed = parseConfigValue(f.getType(), rawValue.trim());
                String validationFailure = validateConfigValue(f.getName(), parsed);
                if (validationFailure != null) {
                    return validationFailure;
                }
                f.set(owner, parsed);
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

    private static List<java.lang.reflect.Field> configFields() {
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        fields.addAll(List.of(Config.class.getFields()));
        fields.addAll(List.of(AgentMobPhysicsConfig.Config.class.getFields()));
        return fields;
    }

    private static Object fieldOwner(java.lang.reflect.Field field) {
        return field.getDeclaringClass() == AgentMobPhysicsConfig.Config.class
                ? AgentMobPhysicsConfig.cfg
                : cfg;
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
        if (type == AgentCombatAoeRepositionMode.class) {
            try {
                return AgentCombatAoeRepositionMode.parse(v);
            } catch (IllegalArgumentException invalid) {
                throw new NumberFormatException(invalid.getMessage());
            }
        }
        throw new NumberFormatException(v);
    }

    private static String validateConfigValue(String name, Object parsed) {
        if (!(parsed instanceof Integer value)) {
            return null;
        }
        int minimum;
        int maximum;
        switch (name) {
            case "MOB_PHYSICS_PUBLICATION_INTERVAL_MS" -> { minimum = 20; maximum = 1_000; }
            case "MOB_PHYSICS_MAX_CATCH_UP_STEPS" -> { minimum = 1; maximum = 100; }
            case "MOB_PHYSICS_STOP_DISTANCE_X", "MOB_PHYSICS_RESUME_DISTANCE_X",
                 "MOB_PHYSICS_FLY_DEAD_ZONE_X", "MOB_PHYSICS_FLY_DEAD_ZONE_Y",
                 "MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX", "MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX" -> {
                minimum = 0; maximum = 2_000;
            }
            case "MOB_PHYSICS_JUMP_TARGET_HEIGHT" -> { minimum = 1; maximum = 2_000; }
            case "MOB_PHYSICS_MAX_SAFE_EDGE_PX", "MOB_PHYSICS_LEFT_EDGE_INSET_PX",
                 "MOB_PHYSICS_RIGHT_EDGE_INSET_PX" -> { minimum = 0; maximum = 1_000; }
            case "MOB_PHYSICS_SPEED_PERCENT", "MOB_PHYSICS_KNOCKBACK_PERCENT" -> {
                minimum = 0; maximum = 300;
            }
            case "MOB_PHYSICS_EDGE_RETREAT_CHANCE_PERCENT",
                 "MOB_PHYSICS_STUCK_RETREAT_CHANCE_PERCENT" -> { minimum = 0; maximum = 100; }
            case "MOB_PHYSICS_JUMP_COOLDOWN_MS", "MOB_PHYSICS_JUMP_COOLDOWN_JITTER_MS",
                 "MOB_PHYSICS_BEHAVIOR_JITTER_MS", "MOB_PHYSICS_DIRECTION_REACTION_MAX_MS",
                 "MOB_PHYSICS_EDGE_IDLE_MIN_MS", "MOB_PHYSICS_EDGE_IDLE_MAX_MS",
                 "MOB_PHYSICS_EDGE_RETREAT_MIN_MS", "MOB_PHYSICS_EDGE_RETREAT_MAX_MS",
                 "MOB_PHYSICS_STUCK_DETECT_MS", "MOB_PHYSICS_FLINCH_RECOVERY_MS",
                 "MOB_PHYSICS_POST_FLINCH_CHASE_RAMP_MS",
                 "MOB_PHYSICS_OBSERVER_WARMUP_MS",
                 "MOB_PHYSICS_AGGRO_TIMEOUT_MS" -> { minimum = 0; maximum = 60_000; }
            case "MOB_PHYSICS_IMPACT_DELAY_PERCENT" -> { minimum = 0; maximum = 200; }
            case "MOB_PHYSICS_IMPACT_DELAY_OFFSET_MS" -> { minimum = -60_000; maximum = 60_000; }
            default -> { return null; }
        }
        if (value < minimum || value > maximum) {
            return "value for " + name + " must be between " + minimum + " and " + maximum;
        }
        return validatePairedPhysicsValue(name, value);
    }

    private static String validatePairedPhysicsValue(String name, int value) {
        return switch (name) {
            case "MOB_PHYSICS_STOP_DISTANCE_X" -> value > AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RESUME_DISTANCE_X
                    ? "MOB_PHYSICS_STOP_DISTANCE_X cannot exceed MOB_PHYSICS_RESUME_DISTANCE_X" : null;
            case "MOB_PHYSICS_RESUME_DISTANCE_X" -> value < AgentMobPhysicsConfig.cfg.MOB_PHYSICS_STOP_DISTANCE_X
                    ? "MOB_PHYSICS_RESUME_DISTANCE_X cannot be below MOB_PHYSICS_STOP_DISTANCE_X" : null;
            case "MOB_PHYSICS_EDGE_IDLE_MIN_MS" -> value > AgentMobPhysicsConfig.cfg.MOB_PHYSICS_EDGE_IDLE_MAX_MS
                    ? "MOB_PHYSICS_EDGE_IDLE_MIN_MS cannot exceed MOB_PHYSICS_EDGE_IDLE_MAX_MS" : null;
            case "MOB_PHYSICS_EDGE_IDLE_MAX_MS" -> value < AgentMobPhysicsConfig.cfg.MOB_PHYSICS_EDGE_IDLE_MIN_MS
                    ? "MOB_PHYSICS_EDGE_IDLE_MAX_MS cannot be below MOB_PHYSICS_EDGE_IDLE_MIN_MS" : null;
            case "MOB_PHYSICS_EDGE_RETREAT_MIN_MS" -> value > AgentMobPhysicsConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_MAX_MS
                    ? "MOB_PHYSICS_EDGE_RETREAT_MIN_MS cannot exceed MOB_PHYSICS_EDGE_RETREAT_MAX_MS" : null;
            case "MOB_PHYSICS_EDGE_RETREAT_MAX_MS" -> value < AgentMobPhysicsConfig.cfg.MOB_PHYSICS_EDGE_RETREAT_MIN_MS
                    ? "MOB_PHYSICS_EDGE_RETREAT_MAX_MS cannot be below MOB_PHYSICS_EDGE_RETREAT_MIN_MS" : null;
            case "MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX" -> value > AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX
                    ? "MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX cannot exceed MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX" : null;
            case "MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX" -> value < AgentMobPhysicsConfig.cfg.MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX
                    ? "MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX cannot be below MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX" : null;
            default -> null;
        };
    }

    public static class Config {

        // Basic attack fallback when weapon data cannot produce a real normal-attack hit box.
        public int ATTACK_RANGE_X = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.ATTACK_RANGE_X");
        public int ATTACK_RANGE_Y = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.ATTACK_RANGE_Y");
        public int ATTACK_DOWN_MAX = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.ATTACK_DOWN_MAX");
        public int ATTACK_JUMP_Y = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.ATTACK_JUMP_Y");
        public int ATTACK_JUMP_X_EXTRA = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.ATTACK_JUMP_X_EXTRA");
        public int RANGED_DEGENERATE_RANGE_X = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.RANGED_DEGENERATE_RANGE_X");
        public int RANGED_DEGENERATE_RANGE_Y = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.RANGED_DEGENERATE_RANGE_Y");
        public int RANGED_RETREAT_THRESHOLD_X = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.RANGED_RETREAT_THRESHOLD_X");
        public int RANGED_RETREAT_DISTANCE_X = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.RANGED_RETREAT_DISTANCE_X");
        public int BREAKOUT_MAX_MS = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.BREAKOUT_MAX_MS");

        // Grind / AoE
        public int GRIND_SEEK_RANGE = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.GRIND_SEEK_RANGE");
        public int GRIND_RETARGET_INTERVAL_MS = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.GRIND_RETARGET_INTERVAL_MS");
        // AoE repositioning: when the best fire-now plan is single-target but stepping into the
        // cluster centroid would let the AoE skill beat it by this DPS factor, defer the shot and
        // walk in. Bounded by distance/time so the bot never chases scattering mobs.
        public AgentCombatAoeRepositionMode AOE_REPOSITION_MODE = AgentCombatAoeRepositionMode.parse(
                config.AgentTuning.stringValue(
                        "server.agents.capabilities.combat.AgentCombatConfig.AOE_REPOSITION_MODE"));
        public boolean AOE_REPOSITION_DEBUG = config.AgentTuning.booleanValue("server.agents.capabilities.combat.AgentCombatConfig.AOE_REPOSITION_DEBUG");
        public double AOE_REPOSITION_DPS_FACTOR = config.AgentTuning.doubleValue("server.agents.capabilities.combat.AgentCombatConfig.AOE_REPOSITION_DPS_FACTOR");
        public int AOE_REPOSITION_MAX_DISTANCE_X = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.AOE_REPOSITION_MAX_DISTANCE_X");
        public int AOE_REPOSITION_ARRIVAL_X = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.AOE_REPOSITION_ARRIVAL_X");
        public long AOE_REPOSITION_MAX_MS = config.AgentTuning.longValue("server.agents.capabilities.combat.AgentCombatConfig.AOE_REPOSITION_MAX_MS");
        public int GRIND_REGION_OCCUPANCY_PENALTY = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.GRIND_REGION_OCCUPANCY_PENALTY");
        public int GRIND_REGION_OCCUPANCY_PENALTY_CAP = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.GRIND_REGION_OCCUPANCY_PENALTY_CAP");
        public int GRIND_TARGET_OCCUPANCY_PENALTY = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.GRIND_TARGET_OCCUPANCY_PENALTY");
        public int GRIND_TARGET_OCCUPANCY_PENALTY_CAP = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.GRIND_TARGET_OCCUPANCY_PENALTY_CAP");

        // Mob damage
        public int MOB_TOUCH_SWEEP_HEIGHT = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.MOB_TOUCH_SWEEP_HEIGHT");
        public int MOB_HIT_COOLDOWN_MS = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.MOB_HIT_COOLDOWN_MS");
        // Support
        public int SUPPORT_RANGE = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.SUPPORT_RANGE");
        public int SUPPORT_VERTICAL_RANGE = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.SUPPORT_VERTICAL_RANGE");
        public int SUPPORT_REBUFF_CD_MS = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.SUPPORT_REBUFF_CD_MS");
        // Heal until every member in range (including the cleric itself) is above this HP ratio.
        // Cadence: animation lock (attackCooldownMs) then HEAL_MOVE_WINDOW_MS walk window.
        // Heal is also gated by moveWindowMs > 0, so it cannot fire mid-attack-movement-window.
        public float SUPPORT_HEAL_TARGET_RATIO = config.AgentTuning.floatValue("server.agents.capabilities.combat.AgentCombatConfig.SUPPORT_HEAL_TARGET_RATIO");
        public int HEAL_MOVE_WINDOW_MS = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.HEAL_MOVE_WINDOW_MS");
        // Jump-heal: while following, if the leader is at least this many px ahead horizontally,
        // kick a diagonal jump toward them just before the heal cast so the bot keeps closing
        // distance instead of stopping to plant the heal animation. 0 disables.
        public int JUMP_HEAL_LEADER_AHEAD_PX = config.AgentTuning.intValue("server.agents.capabilities.combat.AgentCombatConfig.JUMP_HEAL_LEADER_AHEAD_PX");
    }
}
