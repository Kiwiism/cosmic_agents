package server.agents.runtime;

import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotBuffStateRuntime;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;
import server.life.Monster;

import java.awt.Point;
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
}
