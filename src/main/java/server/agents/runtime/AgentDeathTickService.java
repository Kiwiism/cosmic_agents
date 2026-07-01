package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotDeathStateRuntime;
import server.bots.BotEntry;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public final class AgentDeathTickService {
    private AgentDeathTickService() {
    }

    public static boolean handleDeadTick(BotEntry entry,
                                         Character agent,
                                         BooleanSupplier shouldEnterDeadState,
                                         BiConsumer<BotEntry, Character> enterDeadState,
                                         Runnable respawnAction,
                                         long nowMs) {
        if (shouldEnterDeadState.getAsBoolean()) {
            enterDeadState.accept(entry, agent);
        }
        if (!AgentBotDeathStateRuntime.isDead(entry)) {
            return false;
        }
        if (AgentBotDeathStateRuntime.isRespawnDue(entry, nowMs)) {
            respawnAction.run();
        }
        return true;
    }
}
