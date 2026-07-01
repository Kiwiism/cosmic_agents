package server.agents.runtime;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotBuffStateRuntime;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;
import server.life.Monster;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class AgentLeaderSafetyService {
    private AgentLeaderSafetyService() {
    }

    public static boolean shouldTownWarpForInactiveLeader(MapleMap currentMap) {
        return currentMap != null
                && currentMap.getAllMonsters().stream().anyMatch(Monster::isAlive)
                && canReturnToDifferentMap(currentMap);
    }

    public static boolean canReturnToDifferentMap(MapleMap currentMap) {
        if (currentMap == null) {
            return false;
        }
        MapleMap returnMap = currentMap.getReturnMap();
        return returnMap != null && returnMap.getId() != currentMap.getId();
    }

    public static void prepareInactiveIdle(BotEntry entry,
                                           Runnable clearScriptTasks,
                                           Runnable cancelShopVisit,
                                           Runnable clearMode) {
        clearScriptTasks.run();
        cancelShopVisit.run();
        clearMode.run();
        AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
        AgentBotGrindTargetStateRuntime.clear(entry);
        AgentBotDegenerateAttackStateRuntime.clear(entry);
        AgentBotBuffStateRuntime.disable(entry);
        AgentBotActivityStateRuntime.setOwnerAwaySafeMode(entry, true);
    }

    public static void handleActiveLeaderReturn(BotEntry entry,
                                                Runnable clearMoveTarget,
                                                Supplier<Point> removeTownClusterAnchor,
                                                Runnable announceReturnedFromTown) {
        if (AgentBotActivityStateRuntime.ownerAwaySafeMode(entry)
                && !AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry)) {
            return;
        }
        if (!AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry)
                && !AgentBotActivityStateRuntime.ownerReturnedToTown(entry)) {
            return;
        }

        boolean justReturnedFromTown = AgentBotActivityStateRuntime.ownerReturnedToTown(entry);
        AgentBotActivityStateRuntime.clearOwnerInactiveState(entry);
        clearMoveTarget.run();
        Point removedAnchor = removeTownClusterAnchor.get();
        if (justReturnedFromTown && removedAnchor != null) {
            announceReturnedFromTown.run();
        }
    }

    public static boolean shouldEnterInactiveSafeMode(BotEntry entry, long nowMs, long inactiveTownReturnMs) {
        if (AgentBotActivityStateRuntime.ownerReturnedToTown(entry)) {
            if (AgentBotActivityStateRuntime.ownerAwaySafeMode(entry)
                    && !AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry)) {
                AgentBotActivityStateRuntime.startOwnerInactiveTimer(entry, nowMs);
            }
            return false;
        }

        if (!AgentBotActivityStateRuntime.ownerInactiveTimerStarted(entry)) {
            AgentBotActivityStateRuntime.startOwnerInactiveTimer(entry, nowMs);
            return false;
        }

        return nowMs - AgentBotActivityStateRuntime.ownerOfflineOrDeadSinceMs(entry) >= inactiveTownReturnMs;
    }

    public static void idleInactiveAgentInPlace(BotEntry entry,
                                                Runnable idleOnGround,
                                                Runnable broadcastMovement) {
        idleOnGround.run();
        broadcastMovement.run();
        AgentBotActivityStateRuntime.setOwnerReturnedToTown(entry, true);
    }

    public static Point resolveTownClusterTarget(BotEntry entry,
                                                 MapleMap map,
                                                 Point anchor,
                                                 List<BotEntry> leaderEntries,
                                                 AgentFormationService.FormationState formation,
                                                 int platformEdgeInsetPx,
                                                 BiFunction<MapleMap, Point, Point> groundPointResolver) {
        Point base = anchor != null ? new Point(anchor) : new Point(AgentBotRuntimeIdentityRuntime.botPosition(entry));
        if (entry == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry) || map == null) {
            return base;
        }

        int idx = 0;
        for (int i = 0; i < leaderEntries.size(); i++) {
            if (leaderEntries.get(i) == entry) {
                idx = i;
                break;
            }
        }

        int offsetX = formation.offsetFor(idx, Math.max(1, leaderEntries.size()));
        int targetX = base.x + offsetX;

        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(
                map, AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph != null) {
            int anchorRegionId = graph.findRegionId(map, base);
            AgentNavigationGraph.Region anchorRegion = graph.getRegion(anchorRegionId);
            if (anchorRegion != null && !anchorRegion.isRopeRegion) {
                int minX = anchorRegion.minX;
                int maxX = anchorRegion.maxX;
                if (maxX - minX > 2 * platformEdgeInsetPx) {
                    minX += platformEdgeInsetPx;
                    maxX -= platformEdgeInsetPx;
                }
                int clampedX = Math.max(minX, Math.min(maxX, targetX));
                return anchorRegion.pointAt(clampedX);
            }
        }

        Rectangle area = map.getMapArea();
        int minX = area != null ? area.x : targetX;
        int maxX = area != null ? area.x + area.width : targetX;
        if (map.getFootholds() != null && map.getFootholds().getMinDropX() < map.getFootholds().getMaxDropX()) {
            minX = map.getFootholds().getMinDropX();
            maxX = map.getFootholds().getMaxDropX();
        }

        int clampedX = Math.max(minX, Math.min(maxX, targetX));
        Point ground = groundPointResolver.apply(map, new Point(clampedX, base.y - 1));
        if (ground != null) {
            return ground;
        }

        Point anchorGround = groundPointResolver.apply(map, new Point(base.x, base.y - 1));
        return anchorGround != null ? anchorGround : base;
    }

    public static void markInactiveTownReturnHandled(BotEntry entry) {
        AgentBotActivityStateRuntime.setOwnerReturnedToTown(entry, true);
    }

    public static void startInactiveTownClusterMove(BotEntry entry,
                                                    Runnable resetEntryState,
                                                    Runnable startPreciseMoveToClusterTarget) {
        resetEntryState.run();
        startPreciseMoveToClusterTarget.run();
        markInactiveTownReturnHandled(entry);
    }
}
