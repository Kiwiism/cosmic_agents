package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

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
        AgentGrindSearchStateRuntime.scheduleNextSearch(entry, nowMs + hooks.retargetIntervalMs());
        return new SearchResult(currentTarget, currentAttackPlan);
    }
}
