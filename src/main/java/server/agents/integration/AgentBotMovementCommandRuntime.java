package server.agents.integration;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.runtime.AgentCommandModeService;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentScriptTaskQueueService;
import server.maps.MapleMap;

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
        if (entry == null || ownerPos == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry)) {
            return;
        }
        MapleMap map = AgentBotRuntimeIdentityRuntime.botMap(entry);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                map, AgentBotMovementStateRuntime.movementProfile(entry));
        int regionId = graph != null ? graph.findRegionId(map, ownerPos) : -1;
        if (regionId < 0) {
            AgentBotManagerReplyRuntime.replyNow(entry, "can't find a patrol region here");
            return;
        }
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startPatrol(entry, regionId, BotMovementManager::clearNavigationState));
    }

    public static void grind(BotEntry entry) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startGrind(entry, BotMovementManager::clearNavigationState));
    }
}
