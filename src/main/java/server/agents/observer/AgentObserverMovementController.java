package server.agents.observer;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.MapGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentMovementOnlyTickCoordinator;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;

/** Mechanics adapter used by observer policy; it never registers a normal Agent lifecycle. */
final class AgentObserverMovementController {
    private static final String TUNING_PREFIX =
            "server.agents.observer.AgentObserverMovementController.";
    private static final Logger log = LoggerFactory.getLogger(AgentObserverMovementController.class);
    private static final int PORTAL_DISTANCE_PX = tuningInt("PORTAL_DISTANCE_PX");
    private static final int ARRIVAL_DISTANCE_PX = tuningInt("ARRIVAL_DISTANCE_PX");
    private static final int CASUAL_OFFSET_PX = tuningInt("CASUAL_OFFSET_PX");

    private final PrimitiveCapabilityGateway capability =
            AgentPrimitiveCapabilityGatewayRuntime.gateway();
    private final MapGateway maps = AgentMapGatewayRuntime.map();

    boolean travelNormally(AgentObserverSession session, Character observer, int destinationMapId,
                           long nowMs) {
        if (observer.getMapId() == destinationMapId) {
            return true;
        }
        Integer nextMapId = AgentObserverPolicy.nextHop(observer.getMapId(), destinationMapId);
        if (nextMapId == null) {
            return false;
        }
        Integer portalId = capability.directPortalIdTo(observer, nextMapId);
        if (portalId == null) {
            return false;
        }
        Point portal = capability.portalPosition(observer, portalId);
        if (portal == null) {
            return false;
        }
        if (observer.getPosition().distanceSq(portal)
                <= (long) PORTAL_DISTANCE_PX * PORTAL_DISTANCE_PX) {
            int previousMapId = observer.getMapId();
            if (capability.enterPortal(observer, portalId)
                    && observer.getMapId() != previousMapId) {
                synchronizePose(session.movementEntry, observer);
            }
        } else {
            capability.navigate(session.movementEntry, portal, true);
            AgentMovementOnlyTickCoordinator.stepMovementOnly(session.movementEntry, nowMs);
        }
        return observer.getMapId() == destinationMapId;
    }

    void warp(AgentObserverSession session, Character observer, int mapId) {
        MapleMap destination = maps.resolveMap(session.world, session.channel, mapId);
        if (destination == null) {
            log.warn("Observer could not resolve destination map {}", mapId);
            return;
        }
        Portal portal = destination.getPortal(0);
        Point arrival = portal == null ? new Point(0, 0) : new Point(portal.getPosition());
        maps.changeMapNear(observer, destination, arrival);
        synchronizePose(session.movementEntry, observer);
    }

    boolean approach(AgentObserverSession session,
                     Character observer,
                     Point destination,
                     long nowMs) {
        if (destination == null) {
            return true;
        }
        if (observer.getPosition().distanceSq(destination)
                <= (long) ARRIVAL_DISTANCE_PX * ARRIVAL_DISTANCE_PX) {
            stop(session);
            return true;
        }
        capability.navigate(session.movementEntry, destination, true);
        AgentMovementOnlyTickCoordinator.stepMovementOnly(session.movementEntry, nowMs);
        return false;
    }

    Point casualPoint(Character observer) {
        int direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        int offset = ThreadLocalRandom.current().nextInt(
                Math.max(1, CASUAL_OFFSET_PX / 2), CASUAL_OFFSET_PX + 1);
        Point candidate = new Point(
                observer.getPosition().x + direction * offset,
                observer.getPosition().y - 20);
        Point ground = AgentMapGatewayRuntime.map().pointBelow(observer.getMap(), candidate);
        return ground == null ? new Point(observer.getPosition()) : ground;
    }

    Point beside(Character target, int distancePx) {
        int direction = (target.getId() & 1) == 0 ? -1 : 1;
        Point candidate = new Point(
                target.getPosition().x + direction * distancePx,
                target.getPosition().y - 20);
        Point ground = AgentMapGatewayRuntime.map().pointBelow(target.getMap(), candidate);
        return ground == null ? new Point(target.getPosition()) : ground;
    }

    void stop(AgentObserverSession session) {
        AgentMovementCommandRuntime.stop(session.movementEntry);
        Character observer = AgentRuntimeIdentityRuntime.bot(session.movementEntry);
        if (observer != null && capability.grounded(observer)) {
            AgentMovementPoseService.idleOnGround(session.movementEntry, observer);
            AgentMovementBroadcastService.broadcastMovement(session.movementEntry);
        }
    }

    private static void synchronizePose(AgentRuntimeEntry entry, Character observer) {
        AgentMovementPoseService.teleportTo(entry, observer, new Point(observer.getPosition()));
        AgentMovementStateResetService.resetEntryStateAfterTeleport(entry);
    }

    private static int tuningInt(String key) {
        return config.AgentTuning.intValue(TUNING_PREFIX + key);
    }
}
