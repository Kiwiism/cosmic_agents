package server.agents.capabilities.combat;

import server.agents.catalog.AgentMapRegionAssignment;
import server.agents.progression.events.AgentProgressionEventPublisher;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Compatibility bridge from existing grind commands to the combat directive. */
public final class AgentCombatDirectiveRuntime {
    private AgentCombatDirectiveRuntime() {
    }

    public static void assignAllowed(AgentRuntimeEntry entry, Set<Integer> allowedMobIds) {
        assign(entry, allowedMobIds, Set.of(), AgentIncidentalMobPolicy.IGNORE);
    }

    public static void assignPreferences(AgentRuntimeEntry entry,
                                         Set<Integer> preferredMobIds,
                                         Set<Integer> incidentalMobIds) {
        AgentIncidentalMobPolicy policy = incidentalMobIds == null || incidentalMobIds.isEmpty()
                ? AgentIncidentalMobPolicy.IGNORE
                : AgentIncidentalMobPolicy.KILL_FOR_SPAWN_PRESSURE;
        assign(entry, preferredMobIds, incidentalMobIds, policy);
    }

    public static AgentCombatDirective directive(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.capabilityStates()
                .find(AgentCombatDirectiveState.STATE_KEY)
                .map(AgentCombatDirectiveState::directive)
                .orElse(null);
    }

    public static AgentCombatTacticalState.Snapshot tacticalSnapshot(AgentRuntimeEntry entry) {
        return entry == null ? null : state(entry).snapshot();
    }

    public static void clear(AgentRuntimeEntry entry) {
        if (entry == null) {
            return;
        }
        entry.capabilityStates().remove(AgentCombatDirectiveState.STATE_KEY)
                .ifPresent(AgentCombatDirectiveState::clear);
        entry.capabilityStates().remove(AgentCombatTacticalState.STATE_KEY)
                .ifPresent(AgentCombatTacticalState::clear);
        entry.capabilityStates().remove(AgentRouteBlockerState.STATE_KEY)
                .ifPresent(AgentRouteBlockerState::clear);
        entry.capabilityStates().remove(AgentCombatPlatformBatchState.STATE_KEY)
                .ifPresent(AgentCombatPlatformBatchState::clear);
    }

    /** Applies a coordinator-owned soft region lease without changing combat objective policy. */
    public static void assignRegion(
            AgentRuntimeEntry entry, AgentMapRegionAssignment regionAssignment) {
        if (entry == null) {
            return;
        }
        AgentCombatDirective current = directive(entry);
        if (current == null) {
            current = new AgentCombatDirective(
                    "field:runtime", "", Set.of(), Map.of(),
                    AgentIncidentalMobPolicy.IGNORE, null, Long.MAX_VALUE);
        }
        assignExact(entry, new AgentCombatDirective(
                current.directiveId(), current.objectiveId(), current.requiredMobIds(),
                current.requiredKills(), current.incidentalPolicy(), regionAssignment,
                current.deadlineMs()));
    }

    /** Restores an exact directive captured before a temporary field exercise. */
    public static void assignExact(AgentRuntimeEntry entry, AgentCombatDirective directive) {
        if (entry == null) {
            return;
        }
        if (directive == null) {
            clear(entry);
            return;
        }
        boolean changed = entry.capabilityStates()
                .require(AgentCombatDirectiveState.STATE_KEY)
                .assign(directive);
        if (changed) {
            state(entry).clear();
            entry.capabilityStates().require(AgentCombatPlatformBatchState.STATE_KEY).clear();
        }
    }

    public static boolean required(AgentRuntimeEntry entry, int mobId) {
        AgentCombatDirective directive = directive(entry);
        return directive == null || directive.requiredMobIds().isEmpty()
                || directive.requiredMobIds().contains(mobId);
    }

    static AgentCombatTacticalState state(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatTacticalState.STATE_KEY);
    }

    private static void assign(AgentRuntimeEntry entry,
                               Set<Integer> requiredMobIds,
                               Set<Integer> incidentalMobIds,
                               AgentIncidentalMobPolicy incidentalPolicy) {
        if (entry == null) {
            return;
        }
        Set<Integer> required = requiredMobIds == null ? Set.of() : Set.copyOf(requiredMobIds);
        Set<Integer> incidental = incidentalMobIds == null ? Set.of() : Set.copyOf(incidentalMobIds);
        LinkedHashSet<Integer> identity = new LinkedHashSet<>(required);
        identity.addAll(incidental);
        String objectiveId = AgentProgressionEventPublisher.objectiveId(entry);
        String directiveId = "objective:" + (objectiveId.isBlank() ? "runtime" : objectiveId)
                + ':' + identity.hashCode();
        AgentCombatDirective directive = new AgentCombatDirective(
                directiveId, objectiveId, required, Map.of(), incidentalPolicy, null, Long.MAX_VALUE);
        boolean changed = entry.capabilityStates()
                .require(AgentCombatDirectiveState.STATE_KEY)
                .assign(directive);
        if (changed) {
            state(entry).clear();
            entry.capabilityStates().require(AgentCombatPlatformBatchState.STATE_KEY).clear();
        }
    }
}
