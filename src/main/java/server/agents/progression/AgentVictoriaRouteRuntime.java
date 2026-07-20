package server.agents.progression;

import client.Character;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentVictoriaRouteRuntime {
    private static final int PORTAL_DISTANCE_PX = 45;
    private static final long FAILED_EDGE_BLOCK_MS = 120_000L;

    public enum Status {
        ARRIVED,
        MOVING,
        NO_ROUTE,
        PORTAL_UNAVAILABLE
    }

    public record TravelOutcome(Status status, int sourceMapId, int nextMapId, int destinationMapId,
                                boolean edgeBlocked) {
    }

    private AgentVictoriaRouteRuntime() {
    }

    /** Returns true while travel is still required. */
    static boolean travel(AgentRuntimeEntry entry,
                          Character agent,
                          int destinationMapId,
                          PrimitiveCapabilityGateway gateway) {
        return travelStatus(entry, agent, destinationMapId, gateway, System.currentTimeMillis()).status()
                != Status.ARRIVED;
    }

    public static TravelOutcome travelStatus(AgentRuntimeEntry entry,
                                             Character agent,
                                             int destinationMapId,
                                             PrimitiveCapabilityGateway gateway,
                                             long nowMs) {
        int sourceMapId = agent.getMapId();
        AgentVictoriaRouteState state = entry.capabilityStates().require(AgentVictoriaRouteState.STATE_KEY);
        state.observeMap(sourceMapId, nowMs);
        if (sourceMapId == destinationMapId) {
            return new TravelOutcome(Status.ARRIVED, sourceMapId, sourceMapId, destinationMapId, false);
        }
        Integer nextMap = AgentVictoriaTrainingRouteCatalog.nextHop(
                sourceMapId, destinationMapId, state.blockedEdges(nowMs));
        if (nextMap == null) {
            return new TravelOutcome(Status.NO_ROUTE, sourceMapId, -1, destinationMapId, false);
        }
        Integer portalId = gateway.directPortalIdTo(agent, nextMap);
        if (portalId == null) {
            portalId = AgentVictoriaTrainingRouteCatalog.scriptedPortalId(sourceMapId, nextMap);
        }
        if (portalId == null) {
            return unavailable(state, sourceMapId, nextMap, destinationMapId, nowMs);
        }
        Point portal = gateway.portalPosition(agent, portalId);
        if (portal == null) {
            return unavailable(state, sourceMapId, nextMap, destinationMapId, nowMs);
        }
        if (agent.getPosition().distanceSq(portal) <= PORTAL_DISTANCE_PX * PORTAL_DISTANCE_PX) {
            if (gateway.enterPortal(agent, portalId)) {
                state.recordPortalSuccess(nowMs);
            } else {
                return unavailable(state, sourceMapId, nextMap, destinationMapId, nowMs);
            }
        } else {
            gateway.navigate(entry, portal, true);
        }
        return new TravelOutcome(Status.MOVING, sourceMapId, nextMap, destinationMapId, false);
    }

    private static TravelOutcome unavailable(AgentVictoriaRouteState state,
                                             int sourceMapId,
                                             int nextMapId,
                                             int destinationMapId,
                                             long nowMs) {
        long edge = AgentVictoriaTrainingRouteCatalog.edgeKey(sourceMapId, nextMapId);
        boolean blocked = state.recordFailure(edge, nowMs, FAILED_EDGE_BLOCK_MS);
        return new TravelOutcome(Status.PORTAL_UNAVAILABLE, sourceMapId, nextMapId,
                destinationMapId, blocked);
    }
}
