package server.agents.integration;

import client.Character;
import server.maps.MapleMap;

import java.awt.Point;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Map registration and transfer use the same concurrent Cosmic APIs as live client handlers.")
public interface MapGateway {
    MapleMap resolveMap(int world, int channel, int mapId);

    void addChannelPlayer(int world, int channel, Character agent);

    void addWorldPlayer(int world, int channel, Character agent);

    void addMapPlayer(MapleMap map, Character agent);

    void changeMap(Character agent, MapleMap map, Point position);

    void changeMapNear(Character agent, MapleMap map, Point position);

    boolean enterPortal(Character agent, int portalId);

    boolean isSwimMap(Character agent);

    @AgentGatewayAffinity(
            value = AgentGatewayThreadAffinity.READ_ONLY_SNAPSHOT,
            rationale = "MapleMap maintains an O(1) real-player observer count.")
    boolean isObservedByPlayer(MapleMap map);

    Point pointBelow(MapleMap map, Point position);
}

