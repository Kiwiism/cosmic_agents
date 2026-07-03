package server.agents.capabilities.movement;

import server.maps.Foothold;
import server.maps.MapleMap;

import java.util.HashMap;
import java.util.Map;

public final class AgentFootholdIndexService {
    private AgentFootholdIndexService() {
    }

    public static Map<Integer, Foothold> buildFhIndex(MapleMap map) {
        Map<Integer, Foothold> index = new HashMap<>();
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            index.put(foothold.getId(), foothold);
        }
        return index;
    }
}
