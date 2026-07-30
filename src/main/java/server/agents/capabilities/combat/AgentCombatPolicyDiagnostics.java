package server.agents.capabilities.combat;

import server.agents.capabilities.looting.AgentPostKillLootState;
import server.agents.capabilities.looting.AgentPreExitLootRuntime;
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
        AgentRouteBlockerState.Snapshot route = entry.capabilityStates()
                .find(AgentRouteBlockerState.STATE_KEY)
                .map(state -> state.snapshot(nowMs))
                .orElse(null);
        return new Snapshot(
                directive == null ? "" : directive.directiveId(),
                directive == null ? "" : directive.objectiveId(),
                directive == null ? Set.of() : directive.requiredMobIds(),
                directive == null ? AgentIncidentalMobPolicy.IGNORE : directive.incidentalPolicy(),
                AgentCombatDirectiveRuntime.tacticalSnapshot(entry),
                loot,
                AgentPreExitLootRuntime.active(entry, nowMs),
                route,
                AgentCombatPolicyConfig.questLocalClearEnforced(),
                AgentCombatPolicyConfig.questLocalClearShadowEnabled());
    }

    public record Snapshot(String directiveId,
                           String objectiveId,
                           Set<Integer> requiredMobIds,
                           AgentIncidentalMobPolicy incidentalPolicy,
                           AgentCombatTacticalState.Snapshot tactical,
                           AgentPostKillLootState.Snapshot postKillLoot,
                           boolean preExitLootActive,
                           AgentRouteBlockerState.Snapshot routeBlocker,
                           boolean localClearEnforced,
                           boolean shadowEnabled) {
        private static Snapshot empty() {
            return new Snapshot("", "", Set.of(), AgentIncidentalMobPolicy.IGNORE,
                    null, null, false, null, false, false);
        }
    }
}
