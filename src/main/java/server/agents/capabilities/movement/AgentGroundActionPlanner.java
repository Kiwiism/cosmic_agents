package server.agents.capabilities.movement;

import java.awt.Point;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.maps.Foothold;

public final class AgentGroundActionPlanner {
    private AgentGroundActionPlanner() {
    }

    public static AgentGroundAction planGroundAction(BotEntry entry, Foothold currentFoothold, Point botPos, Point targetPos) {
        AgentNavigationGraph.Edge navEdge = (AgentNavigationGraph.Edge) AgentBotNavigationDebugStateRuntime.activeNavigationEdge(entry);
        boolean directionalDrop = AgentGroundMovementPolicy.isDirectionalDropEdge(navEdge);
        int stopDist = directionalDrop ? 0 : AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry)
                ? AgentGroundMovementPolicy.preciseNavStopDist(navEdge)
                : AgentMovementPhysicsConfig.configuredStopDist();
        // No hysteresis when navigating to an edge: always move toward the waypoint.
        int followDist = directionalDrop ? 0
                : (navEdge != null || AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry))
                ? stopDist
                : AgentMovementPhysicsConfig.configuredFollowDist();
        int stepX = AgentGroundMovementService.resolveGroundStepX(entry, botPos, targetPos, stopDist, followDist);
        if (stepX == 0) {
            return AgentGroundAction.idle();
        }
        boolean canWalkStep = AgentGroundCollisionService.canWalkGroundStep(AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, stepX);
        if (!canWalkStep) {
            boolean blockedByWall = AgentGroundCollisionService.isGroundStepBlockedByWall(AgentBotRuntimeIdentityRuntime.botMap(entry), botPos, stepX);
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
