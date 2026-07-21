package server.agents.catalog.decision;

import java.util.Comparator;
import java.util.Optional;

/** Read-only combat-anchor and party-partition recommendations over shared topology components. */
public final class AgentCombatPolicyQueryService {
    private final AgentDecisionCatalogRepository repository;
    private final AgentTopologyQueryService topology;

    public AgentCombatPolicyQueryService(AgentDecisionCatalogRepository repository,
                                         AgentTopologyQueryService topology) {
        if (repository == null || topology == null) {
            throw new IllegalArgumentException("Decision catalog repository and topology query are required");
        }
        this.repository = repository;
        this.topology = topology;
    }

    public Optional<AgentDecisionCatalogSnapshot.CombatMapPolicy> policy(int mapId) {
        return repository.snapshot().combat(mapId);
    }

    public Optional<AgentDecisionCatalogSnapshot.CombatPartition> partition(int mapId, int partySize) {
        return policy(mapId).map(value -> value.partitionsByPartySize().get(partySize));
    }

    public Optional<TargetRecommendation> recommendTarget(int mapId,
                                                          int agentX,
                                                          int agentY,
                                                          int mobId,
                                                          int targetX,
                                                          int targetY) {
        AgentDecisionCatalogSnapshot.CombatMapPolicy policy = policy(mapId).orElse(null);
        if (policy == null) {
            return Optional.empty();
        }
        AgentTopologyQueryService.Location agentLocation = topology.locate(mapId, agentX, agentY).orElse(null);
        AgentTopologyQueryService.Location targetLocation = topology.locate(mapId, targetX, targetY).orElse(null);
        int targetComponentId = targetLocation == null ? -1 : targetLocation.componentId();

        AgentDecisionCatalogSnapshot.CombatAnchor anchor = policy.anchorsById().values().stream()
                .filter(candidate -> candidate.mobIds().contains(mobId)
                        && candidate.componentId() == targetComponentId)
                .min(Comparator.comparingDouble(candidate -> distanceSquared(candidate.center(), targetX, targetY)))
                .orElseGet(() -> policy.anchorsById().values().stream()
                        .filter(candidate -> candidate.mobIds().contains(mobId))
                        .min(Comparator.comparingDouble(candidate ->
                                distanceSquared(candidate.center(), targetX, targetY)))
                        .orElse(null));

        boolean mobRecognized = policy.anchorsById().values().stream()
                .anyMatch(candidate -> candidate.mobIds().contains(mobId));
        return Optional.of(new TargetRecommendation(
                mapId,
                mobId,
                agentLocation == null ? -1 : agentLocation.componentId(),
                targetComponentId,
                anchor == null ? "" : anchor.anchorId(),
                anchor == null ? -1 : anchor.componentId(),
                mobRecognized,
                anchor != null && anchor.componentId() == targetComponentId,
                policy.recommendedAgents(),
                policy.maximumAgents()));
    }

    private static double distanceSquared(AgentDecisionCatalogSnapshot.Point point, int x, int y) {
        long dx = (long) x - point.x();
        long dy = (long) y - point.y();
        return (double) dx * dx + (double) dy * dy;
    }

    public record TargetRecommendation(int mapId,
                                       int mobId,
                                       int agentComponentId,
                                       int targetComponentId,
                                       String anchorId,
                                       int anchorComponentId,
                                       boolean mobRecognized,
                                       boolean targetOnRecommendedAnchor,
                                       int recommendedAgents,
                                       int maximumAgents) {
    }
}
