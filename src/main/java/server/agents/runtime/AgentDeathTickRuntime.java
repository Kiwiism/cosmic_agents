package server.agents.runtime;

import server.agents.capabilities.combat.AgentDeathTickService;

import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatDeathRuntime;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Runtime wiring for handling an Agent that is in or entering dead state.
 */
public final class AgentDeathTickRuntime {
    private AgentDeathTickRuntime() {
    }

    public static boolean handleDeadTick(AgentRuntimeEntry entry, Character agent, Character leader) {
        return handleDeadTick(
                entry,
                agent,
                () -> AgentDeathStateRuntime.shouldEnterDeadState(entry, agent.getHp()),
                (deadEntry, deadAgent) -> AgentCombatDeathRuntime.enterDeadState(
                        deadEntry, deadAgent, false, AgentCombatConfig.cfg),
                () -> AgentRespawnRuntime.respawnNearLeader(entry, agent, leader),
                System::currentTimeMillis);
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
