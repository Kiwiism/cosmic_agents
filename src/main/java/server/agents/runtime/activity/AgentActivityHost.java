package server.agents.runtime.activity;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.decision.AgentDecisionProvenanceState;

import java.util.List;

/** Selects the single controller that owns foreground execution for this tick. */
public final class AgentActivityHost {
    private final AgentActivityControllerRegistry registry;

    public AgentActivityHost(AgentActivityControllerRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Activity controller registry is required");
        }
        this.registry = registry;
    }

    public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        AgentActivityHostState state =
                entry.capabilityStates().require(AgentActivityHostState.STATE_KEY);
        for (AgentActivityController controller : registry.controllers()) {
            if (!controller.active(entry, agent)) {
                continue;
            }
            AgentActivityTick outcome = controller.tick(entry, agent, nowMs);
            if (outcome == null) {
                throw new IllegalStateException(
                        "Activity controller returned no outcome: " + controller.id());
            }
            if (outcome.ownsExecution()) {
                if (state.select(controller.id(), controller.activityKind(), nowMs)) {
                    entry.capabilityStates().require(AgentDecisionProvenanceState.STATE_KEY).record(
                            nowMs, "activity-host", controller.id(), "activity-host",
                            "activity-host-v1", "highest-precedence admitted controller", "",
                            registry.controllers().stream()
                                    .map(AgentActivityController::id).toList());
                }
                return outcome.consumedTick();
            }
        }
        if (state.clear(nowMs)) {
            entry.capabilityStates().require(AgentDecisionProvenanceState.STATE_KEY).record(
                    nowMs, "activity-host", "none", "activity-host", "activity-host-v1",
                    "no admitted controller retained foreground execution", "", List.of());
        }
        return false;
    }
}
