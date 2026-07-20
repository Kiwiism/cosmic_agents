package server.agents.capabilities.combat;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public final class AgentDeathTickService {
    private AgentDeathTickService() {
    }

    public static boolean handleDeadTick(AgentRuntimeEntry entry,
                                         Character agent,
                                         BooleanSupplier shouldEnterDeadState,
                                         BiConsumer<AgentRuntimeEntry, Character> enterDeadState,
                                         Runnable respawnAction,
                                         long nowMs) {
        if (shouldEnterDeadState.getAsBoolean()) {
            enterDeadState.accept(entry, agent);
        }
        if (!AgentDeathStateRuntime.isDead(entry)) {
            return false;
        }
        if (AgentDeathStateRuntime.isRespawnDue(entry, nowMs)) {
            respawnAction.run();
        }
        return true;
    }
}
