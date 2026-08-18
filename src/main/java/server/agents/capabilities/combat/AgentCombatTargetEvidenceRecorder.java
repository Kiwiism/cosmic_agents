package server.agents.capabilities.combat;

import client.Character;
import server.agents.progression.events.AgentProgressionEventPublisher;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

final class AgentCombatTargetEvidenceRecorder {
    private AgentCombatTargetEvidenceRecorder() {
    }

    static void decision(AgentRuntimeEntry entry,
                         AgentCombatDecisionTraceState.Mode mode,
                         AgentCombatDecisionTraceState.Outcome outcome,
                         int baseCandidates,
                         int objectiveCandidates,
                         int policyCandidates,
                         int claimCandidates,
                         int scoredCandidates,
                         boolean mapWidePreferredEscalation,
                         boolean rankedVariationConsumed,
                         Monster selected) {
        if (entry == null) {
            return;
        }
        entry.capabilityStates().require(AgentCombatDecisionTraceState.STATE_KEY).record(
                mode, outcome, System.currentTimeMillis(), baseCandidates, objectiveCandidates,
                policyCandidates, claimCandidates, scoredCandidates, mapWidePreferredEscalation,
                rankedVariationConsumed,
                selected == null ? 0 : selected.getObjectId(),
                selected == null ? 0 : selected.getId());
    }

    static void policySelection(AgentRuntimeEntry entry,
                                Character bot,
                                Monster selected,
                                AgentCombatCandidateClass candidateClass,
                                AgentCombatDecisionReason reason,
                                int regionId) {
        if (entry == null || bot == null || selected == null) {
            return;
        }
        AgentCombatCandidateClass selectedClass = candidateClass;
        if (reason == AgentCombatDecisionReason.PLATFORM_BATCH_CLEAR) {
            AgentCombatDirective directive = AgentCombatDirectiveRuntime.directive(entry);
            selectedClass = directive != null
                    && !directive.requiredMobIds().isEmpty()
                    && !directive.requiredMobIds().contains(selected.getId())
                    ? AgentCombatCandidateClass.INCIDENTAL
                    : AgentCombatCandidateClass.REQUIRED;
        }
        long nowMs = System.currentTimeMillis();
        AgentCombatDirectiveRuntime.state(entry).selected(
                bot.getMapId(), regionId, selected.getId(), selectedClass, reason, nowMs);
        AgentCombatTargetSearchModeState searchMode = state(entry, bot, nowMs);
        if (searchMode != null
                && selectedClass == AgentCombatCandidateClass.INCIDENTAL
                && searchMode.snapshot().mode() != AgentCombatTargetSearchMode.REGION_HARVEST) {
            searchMode.enter(AgentCombatTargetSearchMode.SPAWN_PRESSURE,
                    "clearing local incidental mobs while required population is unavailable",
                    regionId, nowMs);
        }
    }

    static void searchRanking(AgentRuntimeEntry entry,
                              Character bot,
                              List<AgentScoredGrindTarget> scoredTargets,
                              int localCandidateCount,
                              int preferredCandidateCount,
                              ToIntFunction<Monster> regionResolver) {
        AgentCombatTargetSearchModeState state = state(entry, bot, System.currentTimeMillis());
        if (state == null) {
            return;
        }
        List<AgentCombatTargetSearchModeState.RankedRegion> ranked = scoredTargets.stream()
                .sorted(Comparator.comparingLong(AgentScoredGrindTarget::routeCost)
                        .thenComparingLong(AgentScoredGrindTarget::localScore)
                        .thenComparingDouble(AgentScoredGrindTarget::distanceSq))
                .limit(3)
                .map(target -> new AgentCombatTargetSearchModeState.RankedRegion(
                        regionResolver.applyAsInt(target.monster()), target.routeCost(),
                        target.localScore(), target.monster().getObjectId(), target.monster().getId()))
                .toList();
        state.recordEvidence(localCandidateCount, preferredCandidateCount, ranked);
    }

    static void synchronize(AgentRuntimeEntry entry, Character bot, long nowMs) {
        state(entry, bot, nowMs);
    }

    static AgentCombatTargetSearchModeState state(AgentRuntimeEntry entry,
                                                   Character bot,
                                                   long nowMs) {
        if (entry == null || bot == null) {
            return null;
        }
        AgentCombatTargetSearchModeState state =
                AgentCombatDecisionStateRuntime.state(entry).targetSearch();
        state.synchronizeScope(
                bot.getMapId(), AgentProgressionEventPublisher.objectiveId(entry), nowMs);
        return state;
    }
}
