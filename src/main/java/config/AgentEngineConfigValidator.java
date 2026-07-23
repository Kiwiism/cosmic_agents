package config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/** Fail-fast validation for deployment and algorithm tuning loaded from Agent YAML. */
final class AgentEngineConfigValidator {
    private AgentEngineConfigValidator() {
    }

    static void validate(AgentYamlConfig config) {
        if (config == null || config.agent == null || config.tuning == null) {
            throw new IllegalStateException("Agent configuration sections are required");
        }
        validateDeployment(config.agent);
        validateTuning(config.tuning);
    }

    private static void validateDeployment(AgentEngineConfig agent) {
        requirePercent("AGENT_DEATH_RESPAWN_HP_PERCENT", agent.AGENT_DEATH_RESPAWN_HP_PERCENT);
        requirePercent(
                "AGENT_MAP_MAX_ACTIVE_COMBAT_PERCENT",
                agent.AGENT_MAP_MAX_ACTIVE_COMBAT_PERCENT);
        requirePercent(
                "AGENT_MAPLE_ISLAND_FULL_PLATFORM_ANCHOR_PERCENT",
                agent.AGENT_MAPLE_ISLAND_FULL_PLATFORM_ANCHOR_PERCENT);
        requirePercent(
                "AGENT_MAPLE_ISLAND_FULL_MIDDLE_TARGET_PERCENT",
                agent.AGENT_MAPLE_ISLAND_FULL_MIDDLE_TARGET_PERCENT);
        requireRange(
                "mob edge idle",
                agent.AGENT_MOB_PHYSICS_EDGE_IDLE_MIN_MS,
                agent.AGENT_MOB_PHYSICS_EDGE_IDLE_MAX_MS);
        requireRange(
                "mob edge retreat",
                agent.AGENT_MOB_PHYSICS_EDGE_RETREAT_MIN_MS,
                agent.AGENT_MOB_PHYSICS_EDGE_RETREAT_MAX_MS);
        requireRange(
                "mob retreat distance",
                agent.AGENT_MOB_PHYSICS_RETREAT_MIN_DISTANCE_PX,
                agent.AGENT_MOB_PHYSICS_RETREAT_MAX_DISTANCE_PX);
        for (Field field : AgentEngineConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            try {
                if (field.get(agent) == null) {
                    throw new IllegalStateException(
                            "Missing Agent deployment setting " + field.getName());
                }
            } catch (IllegalAccessException impossible) {
                throw new IllegalStateException("Could not validate " + field.getName(), impossible);
            }
        }
    }

    private static void validateTuning(Map<String, String> tuning) {
        if (tuning.isEmpty()) {
            throw new IllegalStateException("Agent tuning section cannot be empty");
        }
        for (Map.Entry<String, String> entry : tuning.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                throw new IllegalStateException("Agent tuning keys and values must be non-blank");
            }
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                continue;
            }
            final double numeric;
            try {
                numeric = Double.parseDouble(value);
            } catch (NumberFormatException failure) {
                throw new IllegalStateException(
                        "Agent tuning key " + key + " must be numeric", failure);
            }
            if (!Double.isFinite(numeric) || numeric < 0.0d) {
                throw new IllegalStateException(
                        "Agent tuning key " + key + " must be finite and non-negative");
            }
            if (key.contains("PERCENT") && numeric > 100.0d) {
                throw new IllegalStateException(
                        "Agent tuning percentage " + key + " must be between 0 and 100");
            }
        }
        for (Map.Entry<String, String> entry : tuning.entrySet()) {
            String maxKey = pairedMaximumKey(entry.getKey());
            if (maxKey == null || !tuning.containsKey(maxKey)) {
                continue;
            }
            double minimum = Double.parseDouble(entry.getValue());
            double maximum = Double.parseDouble(tuning.get(maxKey));
            if (minimum > maximum) {
                throw new IllegalStateException(
                        "Agent tuning minimum " + entry.getKey()
                                + " exceeds " + maxKey);
            }
        }
    }

    private static String pairedMaximumKey(String key) {
        if (key.contains("_MIN_")) {
            return key.replace("_MIN_", "_MAX_");
        }
        if (key.endsWith("_MIN")) {
            return key.substring(0, key.length() - 4) + "_MAX";
        }
        return null;
    }

    private static void requirePercent(String name, int value) {
        if (value < 0 || value > 100) {
            throw new IllegalStateException(name + " must be between 0 and 100");
        }
    }

    private static void requireRange(String name, long minimum, long maximum) {
        if (minimum < 0L || maximum < minimum) {
            throw new IllegalStateException(name + " must be non-negative and ordered");
        }
    }
}
