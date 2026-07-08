package server.agents.capabilities.movement;

import java.awt.Point;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;

public final class AgentGroundActionPlanner {
    private AgentGroundActionPlanner() {
    }

    public static AgentGroundAction planGroundAction(AgentRuntimeEntry entry, Foothold currentFoothold, Point botPos, Point targetPos) {
        AgentNavigationGraph.Edge navEdge = (AgentNavigationGraph.Edge) AgentNavigationDebugStateRuntime.activeNavigationEdge(entry);
        boolean directionalDrop = AgentGroundMovementPolicy.isDirectionalDropEdge(navEdge);
        int stopDist = directionalDrop ? 0 : AgentNavigationDebugStateRuntime.navPreciseTarget(entry)
                ? AgentGroundMovementPolicy.preciseNavStopDist(navEdge)
                : AgentMovementPhysicsConfig.configuredStopDist();
        // No hysteresis when navigating to an edge: always move toward the waypoint.
        int followDist = directionalDrop ? 0
                : (navEdge != null || AgentNavigationDebugStateRuntime.navPreciseTarget(entry))
                ? stopDist
                : AgentMovementPhysicsConfig.configuredFollowDist();
        int stepX = AgentGroundMovementService.resolveGroundStepX(entry, botPos, targetPos, stopDist, followDist);
        if (stepX == 0) {
            return AgentGroundAction.idle();
        }
        boolean canWalkStep = AgentGroundCollisionService.canWalkGroundStep(AgentRuntimeIdentityRuntime.botMap(entry), botPos, stepX);
        if (!canWalkStep) {
            boolean blockedByWall = AgentGroundCollisionService.isGroundStepBlockedByWall(AgentRuntimeIdentityRuntime.botMap(entry), botPos, stepX);
            if (!blockedByWall
                    && ((directionalDrop && Integer.signum(stepX) == Integer.signum(navEdge.launchStepX))
                    || AgentFallbackMovementService.shouldWalkOffLedge(entry, botPos, targetPos, stepX))) {
                return AgentGroundAction.walk(stepX);
            }
            if (blockedByWall && navEdge != null) {
                AgentMovementStateResetService.clearNavigationState(entry);
            } else if (navEdge != null && navEdge.type == AgentNavigationGraph.EdgeType.WALK) {
                AgentMovementStateResetService.clearNavigationState(entry);
            }
            return AgentGroundAction.idle();
        }
        if (AgentMobAvoidanceService.shouldJumpToAvoidMob(entry, currentFoothold, botPos, stepX)) {
            return AgentGroundAction.jump(stepX);
        }
        return AgentGroundAction.walk(stepX);
    }
}
