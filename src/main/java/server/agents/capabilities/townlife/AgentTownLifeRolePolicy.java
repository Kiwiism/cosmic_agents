package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

final class AgentTownLifeRolePolicy {
    private static final String TUNING_PREFIX =
            "server.agents.capabilities.townlife.AgentTownLifeRolePolicy.";
    private static final int HEAVY_CROWD_AGENT_COUNT = tuningInt("HEAVY_CROWD_AGENT_COUNT");
    private static final int MODERATE_CROWD_AGENT_COUNT = tuningInt("MODERATE_CROWD_AGENT_COUNT");
    private static final int HEAVY_CROWD_WANDER_PERCENT =
            tuningInt("HEAVY_CROWD_WANDER_PERCENT");
    private static final int MODERATE_CROWD_WANDER_PERCENT =
            tuningInt("MODERATE_CROWD_WANDER_PERCENT");
    private static final int LIGHT_CROWD_WANDER_PERCENT =
            tuningInt("LIGHT_CROWD_WANDER_PERCENT");
    private static final int TRAIT_ADJUSTMENT_DIVISOR =
            tuningInt("TRAIT_ADJUSTMENT_DIVISOR");
    private static final int MIN_WANDER_PERCENT = tuningInt("MIN_WANDER_PERCENT");
    private static final int MAX_WANDER_PERCENT = tuningInt("MAX_WANDER_PERCENT");
    private static final int PERCENT_BOUND = tuningInt("PERCENT_BOUND");
    private static final long ROLE_DURATION_BASE_MS = tuningLong("ROLE_DURATION_BASE_MS");
    private static final int ROLE_DURATION_JITTER_BOUND_MS =
            tuningInt("ROLE_DURATION_JITTER_BOUND_MS");

    private AgentTownLifeRolePolicy() {
    }

    static AgentTownLifeState.Role resolve(AgentRuntimeEntry entry,
                                           Character agent,
                                           AgentTownLifeState state,
                                           long nowMs) {
        if (nowMs < state.roleUntilMs()) {
            return state.role();
        }
        long sameMap = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(other -> other != null && other.getMapId() == agent.getMapId())
                .count();
        int wanderThreshold = sameMap >= HEAVY_CROWD_AGENT_COUNT
                ? HEAVY_CROWD_WANDER_PERCENT
                : sameMap >= MODERATE_CROWD_AGENT_COUNT
                ? MODERATE_CROWD_WANDER_PERCENT
                : LIGHT_CROWD_WANDER_PERCENT;
        AgentPersonalityState personality = entry.capabilityStates()
                .find(AgentPersonalityState.STATE_KEY).orElse(null);
        long seed = agent.getId();
        if (personality != null && personality.profile() != null) {
            AgentPersonalityProfile.Traits traits = personality.profile().traits();
            wanderThreshold += (traits.activity() + traits.curiosity()
                    - traits.patience() - traits.routinePreference())
                    / TRAIT_ADJUSTMENT_DIVISOR;
            seed ^= personality.behaviorSeed();
        }
        wanderThreshold = Math.max(
                MIN_WANDER_PERCENT,
                Math.min(MAX_WANDER_PERCENT, wanderThreshold));
        int roll = variation(seed, state.sequence(), PERCENT_BOUND, 211);
        AgentTownLifeState.Role role = roll < wanderThreshold
                ? AgentTownLifeState.Role.WANDERER : AgentTownLifeState.Role.STATIONED;
        long duration = ROLE_DURATION_BASE_MS
                + variation(seed, state.sequence(), ROLE_DURATION_JITTER_BOUND_MS, 223);
        state.assignRole(role, nowMs + duration);
        return role;
    }

    static int variation(long seed, int sequence, int bound, int salt) {
        if (bound <= 1) {
            return 0;
        }
        long value = seed * 0x9E3779B97F4A7C15L
                + (long) sequence * 0xBF58476D1CE4E5B9L
                + salt * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return Math.floorMod((int) (value ^ value >>> 31), bound);
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }

    private static long tuningLong(String name) {
        return config.AgentTuning.longValue(TUNING_PREFIX + name);
    }
}
