package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.runtime.AgentCommandModeService;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentScriptTaskQueueService;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Agent-owned movement command facade.
 */
public final class AgentMovementCommandRuntime {
    private AgentMovementCommandRuntime() {
    }

    public static void followOwner(AgentRuntimeEntry entry) {
        follow(entry, AgentRuntimeIdentityRuntime.owner(entry));
    }

    public static void follow(AgentRuntimeEntry entry, Character target) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startFollow(entry, target));
    }

    public static void stop(AgentRuntimeEntry entry) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startStop(entry));
    }

    public static void moveTo(AgentRuntimeEntry entry, Point dest, boolean precise) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> dest != null,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startMoveTo(entry, dest, precise));
    }

    public static void farmHere(AgentRuntimeEntry entry, Point dest) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> dest != null && AgentRuntimeIdentityRuntime.hasBot(entry),
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startFarmHere(entry, dest, AgentMovementStateResetService::clearNavigationState));
    }

    public static void patrol(AgentRuntimeEntry entry, Point ownerPos) {
        if (entry == null || ownerPos == null || !AgentRuntimeIdentityRuntime.hasBot(entry)) {
            return;
        }
        MapleMap map = AgentRuntimeIdentityRuntime.botMap(entry);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                map, AgentMovementStateRuntime.movementProfile(entry));
        int regionId = graph != null ? graph.findRegionId(map, ownerPos) : -1;
        if (regionId < 0) {
            AgentReplyRuntime.replyNow(entry, "can't find a patrol region here");
            return;
        }
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startPatrol(entry, regionId, AgentMovementStateResetService::clearNavigationState));
    }

    public static void grind(AgentRuntimeEntry entry) {
        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.startGrind(entry, AgentMovementStateResetService::clearNavigationState));
    }
}
