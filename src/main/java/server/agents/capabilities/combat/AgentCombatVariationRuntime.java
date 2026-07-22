package server.agents.capabilities.combat;

import client.Character;
import constants.id.MapId;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.util.Collection;
import java.util.Set;

/** Combat-facing adapter for seeded target diversity and automatic platform anchors. */
public final class AgentCombatVariationRuntime {
    private AgentCombatVariationRuntime() {
    }

    public static void configure(AgentRuntimeEntry entry, AgentCombatVariationSettings settings) {
        if (entry != null) {
            clearAutomaticAnchor(entry);
            state(entry).configure(settings);
        }
    }

    public static void clear(AgentRuntimeEntry entry) {
        if (entry != null) {
            clearAutomaticAnchor(entry);
            entry.capabilityStates().remove(AgentCombatVariationState.STATE_KEY);
        }
    }

    public static AgentCombatVariationSettings settings(AgentRuntimeEntry entry) {
        return entry == null
                ? AgentCombatVariationSettings.disabled()
                : entry.capabilityStates().find(AgentCombatVariationState.STATE_KEY)
                .map(AgentCombatVariationState::settings)
                .orElse(AgentCombatVariationSettings.disabled());
    }

    public static int selectTargetIndex(AgentRuntimeEntry entry, int candidateCount) {
        return entry == null ? 0 : state(entry).selectTargetIndex(candidateCount);
    }

    public static boolean isPlatformAnchorRole(AgentRuntimeEntry entry) {
        return entry != null && state(entry).platformAnchorRole();
    }

    public static void maybeAnchorAtTarget(AgentRuntimeEntry entry,
                                           Character agent,
                                           Monster target,
                                           int targetRegionId) {
        if (entry == null || agent == null || agent.getMap() == null || target == null
                || targetRegionId < 0 || AgentPatrolStateRuntime.hasPatrolRegion(entry)
                || !MapId.isMapleIsland(agent.getMapId())
                || !isPlatformAnchorRole(entry)) {
            return;
        }
        if (AgentCombatObjectiveTargetStateRuntime.hasPreferredTargets(entry)
                && !AgentCombatObjectiveTargetStateRuntime.prefers(entry, target.getId())) {
            return;
        }

        AgentPatrolStateRuntime.startPatrol(entry, targetRegionId, agent.getMapId());
        state(entry).markAutomaticAnchor(target.getId(), agent.getMapId(), targetRegionId);
    }

    public static void retainAutomaticAnchorFor(AgentRuntimeEntry entry, Collection<Integer> allowedMobIds) {
        if (entry == null) {
            return;
        }
        AgentCombatVariationState state = state(entry);
        int anchoredMobId = state.automaticAnchorMobId();
        if (anchoredMobId < 0) {
            return;
        }
        Set<Integer> allowed = allowedMobIds == null ? Set.of() : Set.copyOf(allowedMobIds);
        if (!allowed.contains(anchoredMobId)) {
            clearAutomaticAnchor(entry);
        }
    }

    public static void clearAutomaticAnchor(AgentRuntimeEntry entry) {
        if (entry == null) {
            return;
        }
        AgentCombatVariationState state = state(entry);
        if (state.automaticAnchorMobId() >= 0
                && (state.automaticAnchorMapId() < 0
                || (AgentPatrolStateRuntime.patrolMapId(entry) == state.automaticAnchorMapId()
                && AgentPatrolStateRuntime.patrolRegionId(entry) == state.automaticAnchorRegionId()))) {
            AgentPatrolStateRuntime.clearPatrol(entry);
        }
        state.clearAutomaticAnchor();
    }

    public static boolean isAutomaticPlatformAnchor(AgentRuntimeEntry entry) {
        if (entry == null) {
            return false;
        }
        return entry.capabilityStates().find(AgentCombatVariationState.STATE_KEY)
                .map(state -> state.automaticAnchorMobId() >= 0
                        && AgentPatrolStateRuntime.hasPatrolRegion(entry)
                        && AgentPatrolStateRuntime.patrolMapId(entry) == state.automaticAnchorMapId()
                        && AgentPatrolStateRuntime.patrolRegionId(entry) == state.automaticAnchorRegionId())
                .orElse(false);
    }

    private static AgentCombatVariationState state(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatVariationState.STATE_KEY);
    }
}
