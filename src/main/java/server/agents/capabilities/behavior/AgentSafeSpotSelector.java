package server.agents.capabilities.behavior;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationRegionService;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.life.Monster;
import server.agents.perception.AgentMapPerception;

import java.awt.Point;
import java.util.Comparator;
import java.util.List;

/** Chooses a reachable ground-region center away from mobs and other respite destinations. */
public final class AgentSafeSpotSelector {
    private AgentSafeSpotSelector() {
    }

    public static Point select(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || agent.getMap() == null) return null;
        AgentNavigationGraph graph = AgentNavigationGraphService.peekClosestGraph(
                agent.getMap(), AgentMovementStateRuntime.movementProfileOrCharacter(entry, agent));
        if (graph == null) return new Point(agent.getPosition());
        int currentRegionId = AgentNavigationRegionService.resolveCurrentRegionId(
                graph, entry, agent.getMap(), agent.getPosition());
        int connectedComponent = graph.connectedComponentId(currentRegionId);
        List<Point> threats = AgentMapPerception.monsters(agent.getMap()).stream().filter(Monster::isAlive)
                .map(Monster::getPosition).toList();
        List<Point> reservations = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .filter(peer -> peer != entry)
                .map(peer -> peer.capabilityStates().find(AgentCrowdRespiteState.STATE_KEY)
                        .map(AgentCrowdRespiteState::safeSpot).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
        return graph.regions.stream().filter(region -> !region.isRopeRegion && region.width() >= 24
                        && (connectedComponent < 0 || graph.connectedComponentId(region.id) == connectedComponent))
                .map(AgentNavigationGraph.Region::centerPoint)
                .max(Comparator.comparingLong(point -> score(point, threats, reservations)))
                .map(Point::new).orElseGet(() -> new Point(agent.getPosition()));
    }

    private static long score(Point point, List<Point> threats, List<Point> reservations) {
        long threatDistance = threats.stream().mapToLong(other -> distanceSquared(point, other)).min().orElse(250_000L);
        long reservationDistance = reservations.stream().mapToLong(other -> distanceSquared(point, other)).min().orElse(100_000L);
        return threatDistance + reservationDistance / 2;
    }

    private static long distanceSquared(Point left, Point right) {
        long dx = left.x - right.x;
        long dy = left.y - right.y;
        return dx * dx + dy * dy;
    }
}
