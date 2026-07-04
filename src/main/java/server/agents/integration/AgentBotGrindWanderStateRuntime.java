package server.agents.integration;

import server.bots.BotEntry;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned adapter for temporary BotEntry-backed no-target grind wandering state.
 */
public final class AgentBotGrindWanderStateRuntime {
    private AgentBotGrindWanderStateRuntime() {
    }

    public static int wanderDirection(BotEntry entry) {
        return entry.grindWanderState().direction();
    }

    public static void setWanderDirection(BotEntry entry, int direction) {
        entry.grindWanderState().setDirection(direction);
    }

    public static void clearWanderDirection(BotEntry entry) {
        entry.grindWanderState().clear();
    }

    public static int ensureWanderDirection(BotEntry entry) {
        int direction = wanderDirection(entry);
        if (direction == 0) {
            direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            setWanderDirection(entry, direction);
        }
        return direction;
    }
}
