package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.recovery.AgentRespawnCoordinator;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Coordinates dead-state entry and respawn handling for one Agent tick.
 */
public final class AgentDeathTickCoordinator {
    private AgentDeathTickCoordinator() {
    }

    public static boolean handleDeadTick(AgentRuntimeEntry entry, Character agent) {
        LongSupplier nowMs = System::currentTimeMillis;
        return handleDeadTick(
                entry,
                agent,
                () -> AgentDeathStateRuntime.shouldEnterDeadState(entry, agent.getHp()),
                (deadEntry, deadAgent) -> AgentCombatDeathRuntime.enterDeadState(
                        deadEntry, deadAgent, false, AgentCombatConfig.cfg),
                () -> AgentRespawnCoordinator.recover(entry, agent, nowMs.getAsLong()),
                nowMs);
    }

    static boolean handleDeadTick(AgentRuntimeEntry entry,
                                  Character agent,
                                  BooleanSupplier shouldEnterDeadState,
                                  BiConsumer<AgentRuntimeEntry, Character> enterDeadState,
                                  Runnable respawnAction,
                                  LongSupplier nowMs) {
        return AgentDeathTickService.handleDeadTick(
                entry,
                agent,
                shouldEnterDeadState,
                enterDeadState,
                respawnAction,
                nowMs.getAsLong());
    }
}
