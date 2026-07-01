package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.bots.BotEntry;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Agent-owned ownerless/offline-leader tick branch.
 */
public final class AgentOwnerlessTickService {
    private AgentOwnerlessTickService() {
    }

    public static void tickOwnerless(BotEntry entry,
                                     Character agent,
                                     boolean runAiTick,
                                     BiPredicate<BotEntry, Character> groundAfterMapChange,
                                     OwnerlessMoveTick standaloneMoveTick,
                                     BooleanSupplier idleTick) {
        AgentBotModeStateRuntime.setFollowing(entry, false);
        if (groundAfterMapChange.test(entry, agent)) {
            return;
        }
        if (AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)) {
            standaloneMoveTick.tick(entry, agent, runAiTick);
        } else {
            idleTick.getAsBoolean();
        }
    }

    @FunctionalInterface
    public interface OwnerlessMoveTick {
        void tick(BotEntry entry, Character agent, boolean runAiTick);
    }
}
