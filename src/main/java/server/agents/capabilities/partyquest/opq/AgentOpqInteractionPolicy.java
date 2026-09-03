package server.agents.capabilities.partyquest.opq;

import server.maps.Reactor;

import java.awt.Point;

/** Fail-closed legality gates for OPQ physical interactions. */
public final class AgentOpqInteractionPolicy {
    static final int REACTOR_HORIZONTAL_REACH_PX = 90;
    static final int REACTOR_VERTICAL_TOLERANCE_PX = 60;
    static final int REACTOR_GROUND_HORIZONTAL_TOLERANCE_PX = 30;
    static final int REACTOR_GROUND_MAX_OFFSET_Y_PX = 120;
    static final int REACTOR_JUMP_LAUNCH_REACH_PX = 90;
    static final int REACTOR_JUMP_LAUNCH_MAX_OFFSET_Y_PX = 120;
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

    /**
     * Reactor anchors can be above the foothold where a client must stand (the OPQ cloud
     * anchors are 90 px above it). Prove the supplied stand point belongs under that anchor,
     * then apply the ordinary grounded melee envelope around the stand point.
     */
    static boolean mayHitReactorFromGround(int agentMapId, Point agentPosition, boolean grounded,
                                           int reactorMapId, Point reactorPosition, Point standPoint) {
        return grounded && agentMapId == reactorMapId && agentPosition != null
                && legalGroundStrikePoint(reactorPosition, standPoint)
                && Math.abs(agentPosition.x - standPoint.x) <= REACTOR_HORIZONTAL_REACH_PX
                && Math.abs(agentPosition.y - standPoint.y) <= REACTOR_VERTICAL_TOLERANCE_PX;
    }

    static boolean legalGroundStrikePoint(Point reactorPosition, Point standPoint) {
        return reactorPosition != null && standPoint != null
                && Math.abs(standPoint.x - reactorPosition.x) <= REACTOR_GROUND_HORIZONTAL_TOLERANCE_PX
                && standPoint.y >= reactorPosition.y
                && standPoint.y - reactorPosition.y <= REACTOR_GROUND_MAX_OFFSET_Y_PX;
    }

    static boolean legalDirectGroundStrikePoint(Point reactorPosition, Point standPoint) {
        return reactorPosition != null && standPoint != null
                && Math.abs(standPoint.x - reactorPosition.x) <= REACTOR_HORIZONTAL_REACH_PX
                && Math.abs(standPoint.y - reactorPosition.y) <= REACTOR_VERTICAL_TOLERANCE_PX;
    }

    /** A launch point must be a nearby authored foothold above the reactor for a real fall-through strike. */
    static boolean legalJumpLaunchPoint(Point reactorPosition, Point launchPoint) {
        return reactorPosition != null && launchPoint != null
                && Math.abs(launchPoint.x - reactorPosition.x) <= REACTOR_JUMP_LAUNCH_REACH_PX
                && launchPoint.y < reactorPosition.y
                && reactorPosition.y - launchPoint.y <= REACTOR_JUMP_LAUNCH_MAX_OFFSET_Y_PX;
    }

    /** A jump strike is legal only at the actual airborne character position. */
    static boolean mayHitReactorInAir(int agentMapId, Point agentPosition, boolean grounded,
                                      int reactorMapId, Point reactorPosition) {
        return !grounded && agentMapId == reactorMapId && agentPosition != null
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
