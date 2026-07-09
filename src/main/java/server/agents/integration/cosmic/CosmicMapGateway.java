package server.agents.integration.cosmic;

import client.Character;
import server.agents.integration.MapGateway;
import server.maps.MapleMap;
import net.server.Server;

import java.awt.Point;

public enum CosmicMapGateway implements MapGateway {
    INSTANCE;

    @Override
    public MapleMap resolveMap(int world, int channel, int mapId) {
        return Server.getInstance().getChannel(world, channel).getMapFactory().getMap(mapId);
    }

    @Override
    public void addChannelPlayer(int world, int channel, Character agent) {
        Server.getInstance().getChannel(world, channel).addPlayer(agent);
    }

    @Override
    public void addWorldPlayer(int world, int channel, Character agent) {
        Server.getInstance().getChannel(world, channel).getWorldServer().addPlayer(agent);
    }

    @Override
    public void addMapPlayer(MapleMap map, Character agent) {
        if (map == null || agent == null) {
            return;
        }
        map.addPlayer(agent);
    }

    @Override
    public void changeMap(Character agent, MapleMap map, Point position) {
        if (agent == null || map == null) {
            return;
        }
        agent.changeMap(map, position);
    }

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
