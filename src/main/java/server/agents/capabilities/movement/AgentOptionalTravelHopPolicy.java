package server.agents.capabilities.movement;

import server.agents.capabilities.navigation.AgentFootholdDetourService;
import server.agents.capabilities.navigation.AgentTravelVariationRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;

/** Safe, optional presentation hop used only when run-scoped settings enable it. */
public final class AgentOptionalTravelHopPolicy {
    private AgentOptionalTravelHopPolicy() {
    }

    public static boolean shouldHop(AgentRuntimeEntry entry,
                                    Foothold currentFoothold,
                                    Point agentPosition,
                                    Point targetPosition,
                                    int stepX,
                                    long nowMs) {
        if (entry == null || currentFoothold == null || agentPosition == null
                || targetPosition == null || stepX == 0
                || AgentMovementStateRuntime.inAir(entry)
                || AgentClimbStateRuntime.climbing(entry)
                || AgentMovementStateRuntime.downJumpPending(entry)
                || AgentNavigationDebugStateRuntime.navPreciseTarget(entry)
                || AgentFootholdDetourService.active(entry)) {
            return false;
        }

        AgentNavigationGraph.Edge activeEdge =
                (AgentNavigationGraph.Edge) AgentNavigationDebugStateRuntime.activeNavigationEdge(entry);
        if (activeEdge != null && activeEdge.type != AgentNavigationGraph.EdgeType.WALK) {
            return false;
        }
        int targetDx = targetPosition.x - agentPosition.x;
        if (targetDx == 0 || Integer.signum(targetDx) != Integer.signum(stepX)) {
            return false;
        }

        MapleMap map = AgentRuntimeIdentityRuntime.botMap(entry);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                map, AgentMovementStateRuntime.movementProfile(entry));
        if (graph == null) {
            return false;
        }
        int currentRegionId = graph.regionIdByFootholdId.getOrDefault(currentFoothold.getId(), -1);
        if (currentRegionId < 0
                || !AgentTravelVariationRuntime.shouldAttemptTravelHop(
                entry, nowMs)) {
            return false;
        }

        AgentJumpLanding landing = AgentJumpProbeService.simulateJumpLanding(
                map, agentPosition, stepX, AgentMovementStateRuntime.movementProfile(entry));
        if (landing == null || landing.point() == null || landing.foothold() == null) {
            return false;
        }
        int landingRegionId = graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
        if (landingRegionId != currentRegionId) {
            return false;
        }

        int travelDx = landing.point().x - agentPosition.x;
        if (travelDx == 0 || Integer.signum(travelDx) != Integer.signum(stepX)
                || Math.abs(targetPosition.x - landing.point().x) >= Math.abs(targetDx)) {
            return false;
        }

        AgentTravelVariationRuntime.markTravelHopStarted(entry, nowMs);
        return true;
    }
}
