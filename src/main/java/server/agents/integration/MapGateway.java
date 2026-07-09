package server.agents.integration;

import client.Character;
import server.maps.MapleMap;

import java.awt.Point;

public interface MapGateway {
    MapleMap resolveMap(int world, int channel, int mapId);

    void addChannelPlayer(int world, int channel, Character agent);

    void addWorldPlayer(int world, int channel, Character agent);

    void addMapPlayer(MapleMap map, Character agent);

    void changeMap(Character agent, MapleMap map, Point position);

    void changeMapNear(Character agent, MapleMap map, Point position);

    Point pointBelow(MapleMap map, Point position);
}

