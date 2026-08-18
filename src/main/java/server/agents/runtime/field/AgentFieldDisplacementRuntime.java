package server.agents.runtime.field;

import client.Character;
import server.agents.capabilities.combat.AgentCombatDirective;
import server.agents.capabilities.combat.AgentCombatDirectiveRuntime;
import server.agents.capabilities.looting.AgentLootCollectionContextRuntime;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.field.AgentFieldAssignmentState;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Set;

/** Executes the movement/mode side of a field-coordinator displacement transaction. */
public final class AgentFieldDisplacementRuntime {
    private AgentFieldDisplacementRuntime() {
    }

    public static void suspendAtSafeSpot(
            AgentRuntimeEntry entry,
            Character agent,
            AgentCombatDirective baselineDirective) {
        if (entry == null || agent == null) {
            return;
        }
        AgentLootCollectionContextRuntime.leaveFieldGrind(entry);
        AgentCombatDirectiveRuntime.assignExact(entry, baselineDirective);
        entry.capabilityStates().remove(AgentFieldAssignmentState.STATE_KEY)
                .ifPresent(AgentFieldAssignmentState::clear);
        AgentModeService.startStop(entry);
        Point safeSpot = AgentFieldSafeSpotPolicy.select(entry, agent, Set.of());
        if (safeSpot != null) {
            AgentModeService.startMoveTo(entry, safeSpot, true);
        }
    }

    public static void resumeGrinding(AgentRuntimeEntry entry) {
        if (entry != null) {
            AgentModeService.startGrind(
                    entry, AgentMovementStateResetService::clearNavigationState);
        }
    }
}
