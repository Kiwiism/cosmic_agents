package server.agents.progression;

import client.Character;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentVictoriaRouteRuntime {
    private static final int PORTAL_DISTANCE_PX = config.AgentTuning.intValue("server.agents.progression.AgentVictoriaRouteRuntime.PORTAL_DISTANCE_PX");
    private static final long FAILED_EDGE_BLOCK_MS = config.AgentTuning.longValue("server.agents.progression.AgentVictoriaRouteRuntime.FAILED_EDGE_BLOCK_MS");
    private static final long PORTAL_ARRIVAL_SETTLE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentVictoriaRouteRuntime.PORTAL_ARRIVAL_SETTLE_MS");
    private static final long SCRIPTED_PORTAL_OBSERVER_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentVictoriaRouteRuntime.SCRIPTED_PORTAL_OBSERVER_GRACE_MS");

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
        if (state.settlingAt(sourceMapId, nowMs, gateway.observedByPlayer(agent))) {
            gateway.stop(entry);
            return new TravelOutcome(Status.MOVING, sourceMapId, sourceMapId, destinationMapId, false);
        }
        if (sourceMapId == destinationMapId) {
            state.clearActiveTravel();
            return new TravelOutcome(Status.ARRIVED, sourceMapId, sourceMapId, destinationMapId, false);
        }
        Integer nextMap = AgentVictoriaTrainingRouteCatalog.nextHop(
                sourceMapId, destinationMapId, state.blockedEdges(nowMs));
        if (nextMap == null) {
            state.clearActiveTravel();
            return new TravelOutcome(Status.NO_ROUTE, sourceMapId, -1, destinationMapId, false);
        }
        // Authored scripted entrances are semantic route contracts. They must win over
        // inferred/direct edges so job rooms and other scripted interiors use the same
        // entrance and destination portal as a real client.
        Integer portalId = AgentVictoriaTrainingRouteCatalog.scriptedPortalId(sourceMapId, nextMap);
        boolean scriptedPortal = portalId != null;
        if (portalId == null) {
            portalId = gateway.directPortalIdTo(agent, nextMap);
        }
        if (portalId == null) {
            state.clearActiveTravel();
            return unavailable(state, sourceMapId, nextMap, destinationMapId, nowMs);
        }
        Point portal = gateway.portalPosition(agent, portalId);
        if (portal == null) {
            state.clearActiveTravel();
            return unavailable(state, sourceMapId, nextMap, destinationMapId, nowMs);
        }
        state.markActiveTravel(sourceMapId, destinationMapId);
        if (agent.getPosition().distanceSq(portal) <= PORTAL_DISTANCE_PX * PORTAL_DISTANCE_PX) {
            if (gateway.enterPortal(agent, portalId)) {
                boolean destinationObserved = gateway.observedByPlayer(agent);
                state.recordPortalSuccess(nextMap, nowMs, PORTAL_ARRIVAL_SETTLE_MS,
                        scriptedPortal && !destinationObserved,
                        SCRIPTED_PORTAL_OBSERVER_GRACE_MS);
            } else {
                return unavailable(state, sourceMapId, nextMap, destinationMapId, nowMs);
            }
        } else {
            gateway.navigate(entry, portal, true);
        }
        return new TravelOutcome(Status.MOVING, sourceMapId, nextMap, destinationMapId, false);
    }

    /** True only while a Victoria plan is physically crossing this map toward another map. */
    public static boolean activeInterMapTravel(AgentRuntimeEntry entry, int currentMapId) {
        if (entry == null) {
            return false;
        }
        return entry.capabilityStates().require(AgentVictoriaRouteState.STATE_KEY)
                .activeTravelIn(currentMapId);
    }

    /** Prevents a plan reconciler from skipping the visible arrival boundary after a portal transition. */
    static boolean settlingAfterPortalArrival(AgentRuntimeEntry entry,
                                              Character agent,
                                              PrimitiveCapabilityGateway gateway,
                                              long nowMs) {
        return entry != null && entry.capabilityStates().require(AgentVictoriaRouteState.STATE_KEY)
                .settlingAt(agent.getMapId(), nowMs, gateway.observedByPlayer(agent));
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
