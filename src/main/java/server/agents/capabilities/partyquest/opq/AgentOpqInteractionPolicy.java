package server.agents.capabilities.partyquest.opq;

import server.maps.Reactor;

import java.awt.Point;

/** Fail-closed legality gates for OPQ physical interactions. */
public final class AgentOpqInteractionPolicy {
    static final int REACTOR_HORIZONTAL_REACH_PX = 90;
    static final int REACTOR_VERTICAL_TOLERANCE_PX = 40;
    static final int PORTAL_ENTER_RADIUS_PX = 45;
    static final int ITEM_DROP_RADIUS_PX = 38;

    private AgentOpqInteractionPolicy() { }

    public static boolean mayHitReactor(int agentMapId, Point agentPosition,
                                        boolean grounded, Reactor reactor) {
        return reactor != null && reactor.isAlive() && reactor.isActive()
                && mayHitReactor(agentMapId, agentPosition, grounded,
                reactor.getMap().getId(), reactor.getPosition());
    }

    static boolean mayHitReactor(int agentMapId, Point agentPosition, boolean grounded,
                                 int reactorMapId, Point reactorPosition) {
        return grounded && agentMapId == reactorMapId && agentPosition != null
                && reactorPosition != null
                && Math.abs(agentPosition.x - reactorPosition.x) <= REACTOR_HORIZONTAL_REACH_PX
                && Math.abs(agentPosition.y - reactorPosition.y) <= REACTOR_VERTICAL_TOLERANCE_PX;
    }

    public static boolean mayEnterPortal(Point agentPosition, Point portalPosition) {
        return inside(agentPosition, portalPosition, PORTAL_ENTER_RADIUS_PX);
    }

    public static boolean mayDropTrigger(Point agentPosition, Point targetPosition) {
        return inside(agentPosition, targetPosition, ITEM_DROP_RADIUS_PX);
    }

    private static boolean inside(Point first, Point second, int radius) {
        return first != null && second != null
                && Math.abs(first.x - second.x) <= radius
                && Math.abs(first.y - second.y) <= radius;
    }
}
