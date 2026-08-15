package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

/** Owns session-level TownLife orchestration; activity execution stays in its local runtime. */
final class AgentTownLifeSessionRuntime {
    private AgentTownLifeSessionRuntime() {
    }

    static boolean tick(AgentRuntimeEntry entry,
                        Character agent,
                        long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        if (entry == null || agent == null || gateway == null) {
            return false;
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (!state.enabled()) {
            return false;
        }
        AgentTownLifeFidelity previousFidelity = state.fidelity();
        AgentTownLifeFidelity fidelity = AgentTownLifeFidelityPolicy.resolve(entry, agent);
        boolean fidelityChanged = state.updateFidelity(fidelity);
        if (fidelityChanged) {
            AgentTownLifeMetrics.fidelityTransition();
        }
        if (fidelityChanged
                && fidelity == AgentTownLifeFidelity.PRESENTATION
                && previousFidelity == AgentTownLifeFidelity.BACKGROUND_ABSTRACT
                && (state.stage() == AgentTownLifeState.Stage.MOVE_TO_ACTIVITY
                || state.stage() == AgentTownLifeState.Stage.DWELL)) {
            AgentTownLifeRuntime.abandonDestination(entry, agent, state, nowMs, gateway);
            return true;
        }
        if (fidelityChanged
                && fidelity == AgentTownLifeFidelity.BACKGROUND_ABSTRACT
                && state.stage() == AgentTownLifeState.Stage.MOVE_TO_ACTIVITY) {
            state.beginDwell(nowMs + AgentTownLifeRuntime.dwellDuration(agent, state));
        }
        if (state.freeTimeExpired(nowMs) && !state.exitRequested()) {
            AgentTownLifeRuntime.requestDefaultGracefulExit(
                    entry, agent, state, "visit budget expired", nowMs);
        }
        if (AgentTownLifeFidelityPolicy.rendersAmbientActions(fidelity)) {
            AgentTownLifeEncounterCoordinator.tickPassive(entry, agent, state, gateway, nowMs);
        }
        if (agent.getMapId() != state.townMapId()) {
            AgentTownLifeRuntime.terminateLocal(
                    entry, agent, AgentTownLifeLifecycleEvent.Phase.FORCED,
                    "Agent left the TownLife map", nowMs);
            return true;
        }
        if (state.stage() == AgentTownLifeState.Stage.EXITING) {
            AgentTownLifeRuntime.requestDefaultGracefulExit(
                    entry, agent, state, "TownLife entered exiting stage", nowMs);
        }
        if (state.exitDeadlineExpired(nowMs)) {
            if (!state.activityResult().terminal()
                    && state.activity() != AgentTownLifeState.Activity.NONE) {
                state.markActivityResult(AgentTownLifeActivityResult.TIMED_OUT);
            }
            AgentTownLifeRuntime.terminateLocal(
                    entry, agent, AgentTownLifeLifecycleEvent.Phase.TIMED_OUT,
                    state.exitReason(), nowMs);
            return true;
        }
        if (AgentTownLifeRuntime.readyForGracefulExit(entry, state)) {
            AgentTownLifeRuntime.terminateLocal(
                    entry, agent, AgentTownLifeLifecycleEvent.Phase.EXITED,
                    state.exitReason(), nowMs);
            return true;
        }
        boolean consumed = AgentTownLifeActivityRuntime.tick(entry, agent, state, nowMs, gateway);
        if (state.exitRequested() && AgentTownLifeRuntime.readyForGracefulExit(entry, state)) {
            AgentTownLifeRuntime.terminateLocal(
                    entry, agent, AgentTownLifeLifecycleEvent.Phase.EXITED,
                    state.exitReason(), nowMs);
            return true;
        }
        return consumed;
    }
}
