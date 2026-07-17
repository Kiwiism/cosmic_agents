package server.agents.perception;

import server.life.Monster;
import server.maps.MapItem;
import server.maps.MapObject;
import server.maps.MapPerceptionSnapshot;
import server.maps.MapleMap;

import java.util.ArrayList;
import java.util.List;

public final class AgentMapPerception {
    private AgentMapPerception() {
    }

    public static List<Monster> monsters(MapleMap map) {
        if (map == null) {
            return List.of();
        }
        MapPerceptionSnapshot snapshot = map.getPerceptionSnapshot();
        return snapshot == null ? map.getAllMonsters() : snapshot.monsters();
    }

    public static List<MapItem> items(MapleMap map) {
        if (map == null) {
            return List.of();
        }
        MapPerceptionSnapshot snapshot = map.getPerceptionSnapshot();
        if (snapshot != null) {
            return snapshot.items();
        }
        List<MapItem> items = new ArrayList<>();
        for (MapObject object : map.getItems()) {
            if (object instanceof MapItem item) {
                items.add(item);
            }
        }
        return items;
    }
}
