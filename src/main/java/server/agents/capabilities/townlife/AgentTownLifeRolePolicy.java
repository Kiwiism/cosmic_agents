package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

final class AgentTownLifeRolePolicy {
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
        int wanderThreshold = sameMap >= 30 ? 20 : sameMap >= 12 ? 35 : 55;
        AgentPersonalityState personality = entry.capabilityStates()
                .find(AgentPersonalityState.STATE_KEY).orElse(null);
        long seed = agent.getId();
        if (personality != null && personality.profile() != null) {
            AgentPersonalityProfile.Traits traits = personality.profile().traits();
            wanderThreshold += (traits.activity() + traits.curiosity()
                    - traits.patience() - traits.routinePreference()) / 8;
            seed ^= personality.behaviorSeed();
        }
        wanderThreshold = Math.max(10, Math.min(80, wanderThreshold));
        int roll = variation(seed, state.sequence(), 100, 211);
        AgentTownLifeState.Role role = roll < wanderThreshold
                ? AgentTownLifeState.Role.WANDERER : AgentTownLifeState.Role.STATIONED;
        long duration = 90_000L + variation(seed, state.sequence(), 90_001, 223);
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
}
