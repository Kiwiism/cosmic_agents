package server.agents.integration.cosmic;

import client.Character;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentMapTransitionReceiptRuntime;
import server.agents.integration.MapGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.events.AgentEventPriority;
import server.agents.operations.events.AgentMapTransitionedEvent;
import server.agents.operations.events.AgentOperationalEventPublisher;
import server.maps.MapleMap;
import server.maps.MapItem;
import server.maps.Portal;
import net.server.Server;
import tools.PacketCreator;

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
        int previousMapId = agent.getMapId();
        agent.changeMap(map, position);
        recordTransition(agent, previousMapId, -1, -1);
        publishTransition(agent, previousMapId, -1, "change-map");
    }

    @Override
    public void changeMapNear(Character agent, MapleMap map, Point position) {
        if (agent == null || map == null) {
            return;
        }
        if (position == null) {
            position = new Point(agent.getPosition());
        }
        int previousMapId = agent.getMapId();
        Portal portal = map.findClosestPortal(position);
        if (portal == null) {
            changeMap(agent, map, position);
            return;
        }
        // forceChangeMap unregisters and re-registers event participants. Within the same
        // instance that can dispose the event when this is its last registered character.
        if (agent.getEventInstance() != null && agent.getEventInstance() == map.getEventInstance()) {
            agent.changeMap(map, portal);
        } else {
            agent.forceChangeMap(map, portal);
        }
        recordTransition(agent, previousMapId, -1, portal.getId());
        publishTransition(agent, previousMapId, portal.getId(), "change-map-near");
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
        String destinationPortalName = portal.getTarget();
        portal.enterPortal(agent.getClient());
        boolean transitioned = agent.getMapId() != oldMapId || !agent.getPosition().equals(oldPos);
        if (transitioned) {
            // Character.changeMap places the character at the destination portal, but an agent
            // also owns a physics pose. Synchronize it immediately so the next physics tick
            // cannot restore the source-map X coordinate on the destination map.
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
            if (entry != null) {
                Portal destinationPortal = agent.getMap() == null || destinationPortalName == null
                        ? null : agent.getMap().getPortal(destinationPortalName);
                AgentMapTransitionReceiptRuntime.record(entry, oldMapId, portalId, agent.getMapId(),
                        destinationPortal == null ? -1 : destinationPortal.getId(), System.currentTimeMillis());
                AgentMovementPoseService.teleportTo(entry, agent, new Point(agent.getPosition()));
                AgentMovementStateResetService.resetEntryStateAfterTeleport(entry);
            }
            publishTransition(agent, oldMapId, portalId, "portal");
        }
        return transitioned;
    }

    private static void recordTransition(Character agent,
                                         int sourceMapId,
                                         int sourcePortalId,
                                         int destinationPortalId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
        if (entry != null) {
            AgentMapTransitionReceiptRuntime.record(entry, sourceMapId, sourcePortalId,
                    agent.getMapId(), destinationPortalId, System.currentTimeMillis());
        }
    }

    private static void publishTransition(Character agent,
                                          int previousMapId,
                                          int portalId,
                                          String reason) {
        AgentOperationalEventPublisher.publishFor(agent,
                objectiveId -> new AgentMapTransitionedEvent(
                        agent.getId(), System.currentTimeMillis(), previousMapId,
                        agent.getMapId(), portalId, reason, objectiveId),
                AgentEventPriority.IMPORTANT);
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

    @Override
    public void removeItemDrop(MapleMap map, MapItem drop, int animation, int fromCharacterId) {
        if (map == null || drop == null) return;
        map.pickItemDrop(PacketCreator.removeItemFromMap(
                drop.getObjectId(), animation, fromCharacterId), drop);
    }
}
