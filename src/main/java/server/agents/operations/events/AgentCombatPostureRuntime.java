package server.agents.operations.events;

import client.Character;
import server.agents.events.AgentEventPriority;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Publishes posture evidence without changing combat selection or execution. */
public final class AgentCombatPostureRuntime {
    private AgentCombatPostureRuntime() {
    }

    public static void observe(
            AgentRuntimeEntry entry,
            Character agent,
            AgentCombatPostureChangedEvent.Posture posture,
            int targetMobId,
            Point targetPosition,
            String reason,
            long nowMs) {
        if (entry == null || agent == null || agent.getMap() == null || posture == null
                || !entry.capabilityStates().require(AgentCombatPostureState.STATE_KEY)
                .transition(posture)) {
            return;
        }
        AgentOperationalEventPublisher.publish(entry, objectiveId ->
                new AgentCombatPostureChangedEvent(
                        agent.getId(), nowMs, agent.getMapId(), posture,
                        Math.max(0, targetMobId), targetPosition, reason, objectiveId),
                AgentEventPriority.AMBIENT);
    }
}
