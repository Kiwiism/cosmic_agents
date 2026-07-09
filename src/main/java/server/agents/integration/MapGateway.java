package server.agents.integration;

import client.Character;
import server.maps.MapleMap;

import java.awt.Point;

public interface MapGateway {
    void changeMap(Character agent, MapleMap map, Point position);

    void changeMapNear(Character agent, MapleMap map, Point position);

    Point pointBelow(MapleMap map, Point position);
}

