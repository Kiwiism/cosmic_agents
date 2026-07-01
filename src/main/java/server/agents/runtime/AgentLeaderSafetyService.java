package server.agents.runtime;

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
}
