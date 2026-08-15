package server.agents.capabilities.combat;

import server.agents.capabilities.looting.AgentPostKillLootState;
import server.agents.capabilities.looting.AgentPreExitLootRuntime;
import server.agents.capabilities.looting.AgentLootDecisionTraceState;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Set;

/** Read-only decision evidence for tests, admin tooling, and future LLM context. */
public final class AgentCombatPolicyDiagnostics {
    private AgentCombatPolicyDiagnostics() {
    }

    public static Snapshot snapshot(AgentRuntimeEntry entry, long nowMs) {
        if (entry == null) {
            return Snapshot.empty();
        }
        AgentCombatDirective directive = AgentCombatDirectiveRuntime.directive(entry);
        AgentPostKillLootState.Snapshot loot = entry.capabilityStates()
                .find(AgentPostKillLootState.STATE_KEY)
                .map(state -> state.snapshot(nowMs))
                .orElse(null);
        AgentCombatDecisionState decisionState = entry.capabilityStates()
                .find(AgentCombatDecisionState.STATE_KEY).orElse(null);
        AgentRouteBlockerState.Snapshot route = decisionState == null
                ? null : decisionState.routeBlocker().snapshot(nowMs);
        AgentCombatDecisionTraceState.Snapshot combatDecision = entry.capabilityStates()
                .find(AgentCombatDecisionTraceState.STATE_KEY)
                .map(AgentCombatDecisionTraceState::snapshot)
                .orElse(null);
        AgentLootDecisionTraceState.Snapshot lootDecision = entry.capabilityStates()
                .find(AgentLootDecisionTraceState.STATE_KEY)
                .map(AgentLootDecisionTraceState::snapshot)
                .orElse(null);
        AgentCombatLocalTargetLeaseState.Snapshot localTargetLease = decisionState == null
                ? null : decisionState.localTargetLease().snapshot(nowMs);
        AgentCombatTargetSearchModeState.Snapshot targetSearchMode = decisionState == null
                ? null : decisionState.targetSearch().snapshot();
        AgentCombatPlatformBatchState.Snapshot platformBatch = decisionState == null
                ? null : decisionState.platformBatch().snapshot(nowMs);
        return new Snapshot(
                directive == null ? "" : directive.directiveId(),
                directive == null ? "" : directive.objectiveId(),
                directive == null ? Set.of() : directive.requiredMobIds(),
                directive == null ? AgentIncidentalMobPolicy.IGNORE : directive.incidentalPolicy(),
                AgentCombatDirectiveRuntime.tacticalSnapshot(entry),
                loot,
                AgentPreExitLootRuntime.active(entry, nowMs),
                route,
                combatDecision,
                lootDecision,
                localTargetLease,
                targetSearchMode,
                platformBatch);
    }

    public record Snapshot(String directiveId,
                           String objectiveId,
                           Set<Integer> requiredMobIds,
                           AgentIncidentalMobPolicy incidentalPolicy,
                           AgentCombatTacticalState.Snapshot tactical,
                           AgentPostKillLootState.Snapshot postKillLoot,
                           boolean preExitLootActive,
                           AgentRouteBlockerState.Snapshot routeBlocker,
                           AgentCombatDecisionTraceState.Snapshot combatDecision,
                           AgentLootDecisionTraceState.Snapshot lootDecision,
                           AgentCombatLocalTargetLeaseState.Snapshot localTargetLease,
                           AgentCombatTargetSearchModeState.Snapshot targetSearchMode,
                           AgentCombatPlatformBatchState.Snapshot platformBatch) {
        private static Snapshot empty() {
            return new Snapshot("", "", Set.of(), AgentIncidentalMobPolicy.IGNORE,
                    null, null, false, null, null, null, null, null, null);
        }
    }
}
