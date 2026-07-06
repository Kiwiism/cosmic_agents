package server.agents.runtime;

import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.integration.AgentBotCombatDeathRuntime;
import server.agents.integration.AgentBotDeathStateRuntime;
import server.bots.BotEntry;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Runtime wiring for handling an Agent that is in or entering dead state.
 */
public final class AgentDeathTickRuntime {
    private AgentDeathTickRuntime() {
    }

    public static boolean handleDeadTick(BotEntry entry, Character agent, Character leader) {
        return handleDeadTick(
                entry,
                agent,
                () -> AgentBotDeathStateRuntime.shouldEnterDeadState(entry, agent.getHp()),
                (deadEntry, deadAgent) -> AgentBotCombatDeathRuntime.enterDeadState(
                        asBotEntry(deadEntry), deadAgent, false, AgentCombatConfig.cfg),
                () -> AgentRespawnRuntime.respawnNearLeader(entry, agent, leader),
                System::currentTimeMillis);
    }

    static boolean handleDeadTick(BotEntry entry,
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

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
