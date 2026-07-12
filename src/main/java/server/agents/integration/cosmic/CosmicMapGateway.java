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
        agent.setMapTransitionComplete();
    }

    @Override
    public void changeMap(Character agent, MapleMap map, Point position) {
        if (agent == null || map == null) {
            return;
        }
        agent.changeMap(map, position);
        agent.setMapTransitionComplete();
    }

    @Override
    public void changeMapNear(Character agent, MapleMap map, Point position) {
        if (agent == null || map == null) {
            return;
        }
        agent.forceChangeMap(map, map.findClosestPortal(position));
        agent.setMapTransitionComplete();
    }

    @Override
    public boolean enterPortal(Character agent, int portalId) {
        if (agent == null || agent.getMap() == null) {
            return false;
        }
        var portal = agent.getMap().getPortal(portalId);
        if (portal == null || !portal.getPortalStatus()) {
            return false;
        }

        int oldMapId = agent.getMapId();
        Point oldPos = agent.getPosition();
        portal.enterPortal(agent.getClient());
        boolean transitioned = agent.getMapId() != oldMapId || !agent.getPosition().equals(oldPos);
        if (transitioned) {
            agent.setMapTransitionComplete();
        }
        return transitioned;
    }

    @Override
    public boolean isSwimMap(Character agent) {
        return agent != null && agent.getMap() != null && agent.getMap().isSwim();
    }

    @Override
    public Point pointBelow(MapleMap map, Point position) {
        if (map == null || position == null) {
            return null;
        }
        return map.getPointBelow(position);
    }
}
