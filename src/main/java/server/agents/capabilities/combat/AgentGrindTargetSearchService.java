package server.agents.capabilities.combat;

import client.Character;
import server.agents.catalog.decision.AgentDecisionCatalogRuntime;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;

public final class AgentGrindTargetSearchService {
    private AgentGrindTargetSearchService() {
    }

    public record SearchResult(Monster target, AgentAttackPlan attackPlan) {
    }

    public record SearchHooks(TargetFinder patrolTargetFinder,
                              TargetFinder grindTargetFinder,
                              long retargetIntervalMs) {
    }

    @FunctionalInterface
    public interface TargetFinder {
        Monster find(AgentRuntimeEntry entry, Character agent);
    }

    public static SearchResult searchIfDue(AgentRuntimeEntry entry,
                                           Character agent,
                                           Monster currentTarget,
                                           AgentAttackPlan currentAttackPlan,
                                           boolean runAiTick,
                                           long nowMs,
                                           SearchHooks hooks) {
        if (!runAiTick || !AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, currentTarget, currentAttackPlan, nowMs)) {
            return new SearchResult(currentTarget, currentAttackPlan);
        }

        Monster searchedTarget = AgentPatrolStateRuntime.hasPatrolRegion(entry)
                ? hooks.patrolTargetFinder().find(entry, agent)
                : hooks.grindTargetFinder().find(entry, agent);
        if (AgentGrindTargetSearchPolicy.shouldSwitchToSearchedTarget(
                entry, agent, currentTarget, searchedTarget, currentAttackPlan)) {
            currentTarget = searchedTarget;
            currentAttackPlan = null;
        }
        observeCatalogRecommendation(entry, agent, currentTarget, nowMs);
        AgentGrindSearchStateRuntime.scheduleNextSearch(entry, nowMs + hooks.retargetIntervalMs());
        return new SearchResult(currentTarget, currentAttackPlan);
    }

    private static void observeCatalogRecommendation(AgentRuntimeEntry entry,
                                                       Character agent,
                                                       Monster target,
                                                       long nowMs) {
        if (agent == null || agent.getMap() == null || target == null
                || agent.getPosition() == null || target.getPosition() == null) {
            return;
        }
        Point agentPosition = agent.getPosition();
        Point targetPosition = target.getPosition();
        AgentDecisionCatalogRuntime.observeCombatTarget(
                entry,
                agent.getMapId(),
                agentPosition.x,
                agentPosition.y,
                target.getId(),
                targetPosition.x,
                targetPosition.y,
                nowMs);
    }
}
