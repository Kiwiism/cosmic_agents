package server.agents.integration.cosmic;

import client.Character;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.integration.MapGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
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
    public boolean enterPortal(Character agent, int portalId) {
        if (agent == null || agent.getMap() == null) {
            return false;
        }
        var portal = agent.getMap().getPortal(portalId);
        if (portal == null || !portal.getPortalStatus()) {
            return false;
        }

        int oldMapId = agent.getMapId();
        Point oldPos = new Point(agent.getPosition());
        portal.enterPortal(agent.getClient());
        boolean transitioned = agent.getMapId() != oldMapId || !agent.getPosition().equals(oldPos);
        if (transitioned) {
            // Character.changeMap places the character at the destination portal, but an agent
            // also owns a physics pose. Synchronize it immediately so the next physics tick
            // cannot restore the source-map X coordinate on the destination map.
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
            if (entry != null) {
                AgentMovementPoseService.teleportTo(entry, agent, new Point(agent.getPosition()));
                AgentMovementStateResetService.resetEntryStateAfterTeleport(entry);
            }
        }
        return transitioned;
    }

    @Override
    public boolean isSwimMap(Character agent) {
        return agent != null && agent.getMap() != null && agent.getMap().isSwim();
    }

    @Override
    public boolean isObservedByPlayer(MapleMap map) {
        return map != null && map.isObservedByPlayer();
    }

    @Override
    public int activeCharacterCountIfLoaded(int world, int channel, int mapId) {
        MapleMap map = Server.getInstance().getChannel(world, channel)
                .getMapFactory().getLoadedMap(mapId);
        return map == null ? 0 : map.getCharacterCount();
    }

    @Override
    public Point pointBelow(MapleMap map, Point position) {
        if (map == null || position == null) {
            return null;
        }
        return map.getPointBelow(position);
    }
}
