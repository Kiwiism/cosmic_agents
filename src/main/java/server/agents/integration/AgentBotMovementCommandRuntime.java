package server.agents.integration;

import server.bots.BotEntry;
import server.bots.BotManager;

import java.awt.Point;

/**
 * Agent-owned movement command facade over temporary BotManager side effects.
 */
public final class AgentBotMovementCommandRuntime {
    private AgentBotMovementCommandRuntime() {
    }

    public static void followOwner(BotEntry entry) {
        BotManager.getInstance().issueFollowOwner(entry);
    }

    public static void stop(BotEntry entry) {
        BotManager.getInstance().issueStop(entry);
    }

    public static void moveTo(BotEntry entry, Point dest, boolean precise) {
        BotManager.getInstance().issueMoveTo(entry, dest, precise);
    }

    public static void farmHere(BotEntry entry, Point dest) {
        BotManager.getInstance().issueFarmHere(entry, dest);
    }

    public static void patrol(BotEntry entry, Point ownerPos) {
        BotManager.getInstance().issuePatrol(entry, ownerPos);
    }

    public static void grind(BotEntry entry) {
        BotManager.getInstance().issueGrind(entry);
    }
}
