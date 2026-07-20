package server.agents.capabilities.supplies;

import server.agents.capabilities.contracts.AgentProcurementRequest;
import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyNeed;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.events.AgentEventPriority;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

/** Side-effect-free supply observation plus event/procurement projection. */
public final class AgentResourcePlanningRuntime {
    private AgentResourcePlanningRuntime() {
    }

    public static AgentSupplyNeed observe(AgentRuntimeEntry entry, AgentResourceCategory category,
                                          int current, int lowThreshold, int stopThreshold,
                                          int target, long nowMs) {
        AgentSupplyUrgency urgency = urgency(current, lowThreshold, stopThreshold);
        AgentObjectiveDefinition objective = AgentObjectiveKernel.active(entry);
        AgentSupplyNeed need = new AgentSupplyNeed(category, Math.max(0, current),
                Math.max(Math.max(0, current), target), urgency,
                objective == null ? "" : objective.objectiveId(), nowMs);
        AgentResourcePlanningState state = entry.capabilityStates().require(AgentResourcePlanningState.STATE_KEY);
        AgentSupplyNeed previous = state.need(category);
        AgentProcurementRequest procurement = need.shortfall() > 0 && urgency != AgentSupplyUrgency.HEALTHY
                ? AgentSupplyPlanner.plan(need, Long.MAX_VALUE, nowMs + 10 * 60_000L)
                : null;
        state.update(need, procurement);
        if (previous == null || previous.urgency() != urgency) {
            publish(entry, previous == null ? null : previous.urgency(), need, nowMs);
        }
        return need;
    }

    private static AgentSupplyUrgency urgency(int current, int lowThreshold, int stopThreshold) {
        if (current <= 0) return AgentSupplyUrgency.EMPTY;
        if (current < Math.max(1, stopThreshold)) return AgentSupplyUrgency.CRITICAL;
        if (current < Math.max(stopThreshold, lowThreshold)) return AgentSupplyUrgency.LOW;
        return AgentSupplyUrgency.HEALTHY;
    }

    private static void publish(AgentRuntimeEntry entry, AgentSupplyUrgency previousUrgency,
                                AgentSupplyNeed need, long nowMs) {
        int agentId = entry.bot() == null ? 0 : entry.bot().getId();
        if (agentId <= 0) return;
        int mapId = entry.bot().getMap() == null ? -1 : entry.bot().getMapId();
        AgentSessionEventRuntime.bus(entry).publish(new AgentSupplyThresholdChangedEvent(
                        agentId,
                        nowMs,
                        AgentRelationshipRuntime.cohortId(entry),
                        mapId,
                        need.category(),
                        need.currentQuantity(),
                        need.targetQuantity(),
                        previousUrgency,
                        need.urgency(),
                        need.objectiveId()),
                need.urgency().ordinal() >= AgentSupplyUrgency.CRITICAL.ordinal()
                        ? AgentEventPriority.IMPORTANT : AgentEventPriority.NORMAL);
    }
}
