package server.agents.integration;

import server.bots.BotEntry;
import server.bots.BotManager;
import server.bots.BotMovementManager;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.runtime.AgentCommandModeService;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentScriptTaskQueueService;

import java.awt.Point;

/**
 * Agent-owned movement command facade over temporary BotManager side effects.
 */
public final class AgentBotMovementCommandRuntime {
    private AgentBotMovementCommandRuntime() {
    }

    public static void followOwner(BotEntry entry) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startFollow(entry, AgentBotRuntimeIdentityRuntime.owner(entry)));
    }

    public static void stop(BotEntry entry) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startStop(entry));
    }

    public static void moveTo(BotEntry entry, Point dest, boolean precise) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> dest != null,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startMoveTo(entry, dest, precise));
    }

    public static void farmHere(BotEntry entry, Point dest) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> dest != null && AgentBotRuntimeIdentityRuntime.hasBot(entry),
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startFarmHere(entry, dest, BotMovementManager::clearNavigationState));
    }

    public static void patrol(BotEntry entry, Point ownerPos) {
        BotManager.getInstance().issuePatrol(entry, ownerPos);
    }

    public static void grind(BotEntry entry) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startGrind(entry, BotMovementManager::clearNavigationState));
    }
}
