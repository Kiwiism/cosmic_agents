package server.agents.runtime.activity.control.rollout;

import config.AgentEngineConfig;
import config.AgentYamlConfig;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.LinkedHashSet;
import java.util.Set;

/** Strict YAML boundary for live Director rollout. Empty cohorts remain fail-closed. */
public final class AgentWorldDirectorRolloutConfigLoader {
    private AgentWorldDirectorRolloutConfigLoader() { }

    public static AgentWorldDirectorCanaryConfig assisted() {
        AgentEngineConfig config = AgentYamlConfig.config.agent;
        return new AgentWorldDirectorCanaryConfig(
                config.AGENT_WORLD_DIRECTOR_ASSISTED_ENABLED,
                agentIds(config.AGENT_WORLD_DIRECTOR_ASSISTED_AGENT_IDS),
                config.AGENT_WORLD_DIRECTOR_ASSISTED_MAX_CONCURRENT_HANDOFFS,
                kinds(config.AGENT_WORLD_DIRECTOR_ASSISTED_ALLOWED_TARGETS),
                config.AGENT_WORLD_DIRECTOR_ASSISTED_REQUIRE_ROLLBACK);
    }

    public static AgentWorldDirectorAutonomousConfig autonomous() {
        AgentEngineConfig config = AgentYamlConfig.config.agent;
        return new AgentWorldDirectorAutonomousConfig(
                config.AGENT_WORLD_DIRECTOR_AUTONOMOUS_ENABLED,
                config.AGENT_WORLD_DIRECTOR_AUTONOMOUS_ROLLOUT_BASIS_POINTS,
                agentIds(config.AGENT_WORLD_DIRECTOR_AUTONOMOUS_AGENT_IDS),
                config.AGENT_WORLD_DIRECTOR_AUTONOMOUS_MAX_CONCURRENT_HANDOFFS,
                config.AGENT_WORLD_DIRECTOR_AUTONOMOUS_MIN_OBSERVE_SAMPLES,
                config.AGENT_WORLD_DIRECTOR_AUTONOMOUS_MAX_RECENT_FAILURES,
                kinds(config.AGENT_WORLD_DIRECTOR_AUTONOMOUS_ALLOWED_TARGETS),
                config.AGENT_WORLD_DIRECTOR_AUTONOMOUS_REQUIRE_ROLLBACK);
    }

    static Set<Integer> agentIds(String raw) {
        if (none(raw)) return Set.of();
        Set<Integer> result = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            try {
                int id = Integer.parseInt(token.trim());
                if (id <= 0) throw new NumberFormatException();
                result.add(id);
            } catch (NumberFormatException failure) {
                throw new IllegalStateException("Director Agent allowlist contains an invalid id");
            }
        }
        return Set.copyOf(result);
    }

    static Set<AgentActivityKind> kinds(String raw) {
        if (none(raw)) return Set.of();
        Set<AgentActivityKind> result = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            try {
                result.add(AgentActivityKind.valueOf(token.trim().toUpperCase()));
            } catch (IllegalArgumentException failure) {
                throw new IllegalStateException("Director target allowlist contains an invalid kind");
            }
        }
        return Set.copyOf(result);
    }

    private static boolean none(String raw) {
        return raw == null || raw.isBlank() || raw.equalsIgnoreCase("none") || raw.equals("-");
    }
}
