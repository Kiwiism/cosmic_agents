package server.agents.integration.cosmic;

import client.Character;
import server.agents.integration.MapGateway;
import server.maps.MapleMap;

import java.awt.Point;

public enum CosmicMapGateway implements MapGateway {
    INSTANCE;

    @Override
    public void changeMapNear(Character agent, MapleMap map, Point position) {
        if (agent == null || map == null) {
            return;
        }
        agent.forceChangeMap(map, map.findClosestPortal(position));
    }

    @Override
    public Point pointBelow(MapleMap map, Point position) {
        if (map == null || position == null) {
            return null;
        }
        return map.getPointBelow(position);
    }
}
