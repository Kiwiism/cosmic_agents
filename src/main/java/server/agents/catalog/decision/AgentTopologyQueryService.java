package server.agents.catalog.decision;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Shared read-only topology queries for navigation, combat, interaction, and recovery planners. */
public final class AgentTopologyQueryService {
    private final AgentDecisionCatalogRepository repository;

    public AgentTopologyQueryService(AgentDecisionCatalogRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("Decision catalog repository is required");
        }
        this.repository = repository;
    }

    public Optional<AgentDecisionCatalogSnapshot.MapTopology> topology(int mapId) {
        return repository.snapshot().navigation(mapId);
    }

    public Optional<AgentDecisionCatalogSnapshot.Component> component(int mapId, int componentId) {
        return topology(mapId).map(map -> map.componentsById().get(componentId));
    }

    public Optional<AgentDecisionCatalogSnapshot.Foothold> foothold(int mapId, int footholdId) {
        return topology(mapId).map(map -> map.footholdsById().get(footholdId));
    }

    public Optional<Location> locate(int mapId, int x, int y) {
        return topology(mapId).flatMap(topology -> topology.componentsById().values().stream()
                .min(Comparator
                        .comparingDouble((AgentDecisionCatalogSnapshot.Component component) ->
                                distanceSquared(component.bounds(), x, y))
                        .thenComparingDouble(component -> pointDistanceSquared(component.center(), x, y)))
                .map(component -> new Location(
                        mapId,
                        component.componentId(),
                        nearestFoothold(topology, component.componentId(), x, y)
                                .map(AgentDecisionCatalogSnapshot.Foothold::footholdId)
                                .orElse(-1),
                        component.center(),
                        component.safePoint(),
                        distanceSquared(component.bounds(), x, y))));
    }

    public Optional<NavigationRecommendation> recommend(int mapId,
                                                        int sourceX,
                                                        int sourceY,
                                                        int targetX,
                                                        int targetY) {
        Optional<Location> source = locate(mapId, sourceX, sourceY);
        Optional<Location> target = locate(mapId, targetX, targetY);
        if (source.isEmpty() || target.isEmpty()) {
            return Optional.empty();
        }
        AgentDecisionCatalogSnapshot.MapTopology topology = topology(mapId).orElseThrow();
        int sourceComponent = source.get().componentId();
        int targetComponent = target.get().componentId();
        List<AgentDecisionCatalogSnapshot.Transition> candidates = topology.transitions().stream()
                .filter(transition -> transition.connects(sourceComponent, targetComponent))
                .toList();
        return Optional.of(new NavigationRecommendation(
                mapId,
                source.get(),
                target.get(),
                sourceComponent == targetComponent,
                candidates));
    }

    private static Optional<AgentDecisionCatalogSnapshot.Foothold> nearestFoothold(
            AgentDecisionCatalogSnapshot.MapTopology topology,
            int componentId,
            int x,
            int y) {
        return topology.footholdsById().values().stream()
                .filter(foothold -> foothold.componentId() == componentId)
                .min(Comparator.comparingDouble(foothold -> segmentDistanceSquared(foothold, x, y)));
    }

    private static double distanceSquared(AgentDecisionCatalogSnapshot.Bounds bounds, int x, int y) {
        long dx = x < bounds.minX() ? (long) bounds.minX() - x
                : x > bounds.maxX() ? (long) x - bounds.maxX() : 0L;
        long dy = y < bounds.minY() ? (long) bounds.minY() - y
                : y > bounds.maxY() ? (long) y - bounds.maxY() : 0L;
        return (double) dx * dx + (double) dy * dy;
    }

    private static double pointDistanceSquared(AgentDecisionCatalogSnapshot.Point point, int x, int y) {
        long dx = (long) x - point.x();
        long dy = (long) y - point.y();
        return (double) dx * dx + (double) dy * dy;
    }

    private static double segmentDistanceSquared(AgentDecisionCatalogSnapshot.Foothold foothold, int x, int y) {
        double dx = foothold.x2() - foothold.x1();
        double dy = foothold.y2() - foothold.y1();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0.0) {
            return pointDistanceSquared(new AgentDecisionCatalogSnapshot.Point(foothold.x1(), foothold.y1()), x, y);
        }
        double projection = ((x - foothold.x1()) * dx + (y - foothold.y1()) * dy) / lengthSquared;
        double bounded = Math.max(0.0, Math.min(1.0, projection));
        double nearestX = foothold.x1() + bounded * dx;
        double nearestY = foothold.y1() + bounded * dy;
        double resultX = x - nearestX;
        double resultY = y - nearestY;
        return resultX * resultX + resultY * resultY;
    }

    public record Location(int mapId,
                           int componentId,
                           int footholdId,
                           AgentDecisionCatalogSnapshot.Point componentCenter,
                           AgentDecisionCatalogSnapshot.Point safePoint,
                           double componentDistanceSquared) {
    }

    public record NavigationRecommendation(int mapId,
                                           Location source,
                                           Location target,
                                           boolean sameComponent,
                                           List<AgentDecisionCatalogSnapshot.Transition> transitionCandidates) {
        public NavigationRecommendation {
            transitionCandidates = List.copyOf(transitionCandidates);
        }
    }
}
