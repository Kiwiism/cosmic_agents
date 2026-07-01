package server.agents.runtime;

import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotBuffStateRuntime;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;
import server.life.Monster;

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
}
