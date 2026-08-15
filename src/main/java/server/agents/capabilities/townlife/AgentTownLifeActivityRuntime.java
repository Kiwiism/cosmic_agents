package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

/** Executes only the bounded, local activity state machine. */
final class AgentTownLifeActivityRuntime {
    private AgentTownLifeActivityRuntime() {
    }

    static boolean tick(AgentRuntimeEntry entry,
                        Character agent,
                        AgentTownLifeState state,
                        long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        return switch (state.stage()) {
            case DISABLED -> false;
            case SETTLING -> AgentTownLifeRuntime.tickSettling(entry, agent, state, nowMs, gateway);
            case CHOOSE_ACTIVITY, RESERVE_DESTINATION ->
                    AgentTownLifeRuntime.chooseActivity(entry, agent, state, nowMs, gateway);
            case MOVE_TO_ACTIVITY ->
                    AgentTownLifeRuntime.moveToActivity(entry, agent, state, nowMs, gateway);
            case DWELL -> AgentTownLifeRuntime.tickDwell(entry, agent, state, nowMs, gateway);
            case COOLDOWN -> AgentTownLifeRuntime.tickCooldown(state, nowMs);
            case EXITING -> true;
        };
    }
}
