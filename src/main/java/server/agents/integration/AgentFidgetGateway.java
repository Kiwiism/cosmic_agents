package server.agents.integration;

import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Narrow cross-capability boundary for combat-triggered presentation fidgets. */
public final class AgentFidgetGateway {
    public enum Action {
        WAIT,
        JUMP,
        PRONE
    }

    private AgentFidgetGateway() {
    }

    public static boolean combatIdleActive(AgentRuntimeEntry entry) {
        return AgentFidgetStateRuntime.trigger(entry) == AgentFidgetTrigger.COMBAT_IDLE;
    }

    public static boolean tickCombatIdle(AgentRuntimeEntry entry, Point position, long nowMs) {
        return AgentFidgetService.tryHandleCombatIdleTick(entry, position, nowMs);
    }

    public static void startCombatIdle(AgentRuntimeEntry entry,
                                       Action action,
                                       long nowMs,
                                       int durationMs) {
        AgentFidgetMode mode = switch (action) {
            case WAIT -> AgentFidgetMode.WAIT;
            case JUMP -> AgentFidgetMode.JUMP;
            case PRONE -> AgentFidgetMode.SPAM_PRONE;
        };
        AgentFidgetService.startFidget(
                entry, mode, nowMs, durationMs, AgentFidgetTrigger.COMBAT_IDLE);
    }
}
