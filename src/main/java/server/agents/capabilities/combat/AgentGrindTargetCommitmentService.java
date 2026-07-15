package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;

public final class AgentGrindTargetCommitmentService {
    static final long TARGET_COMMITMENT_MS = 12_000L;

    private AgentGrindTargetCommitmentService() {
    }

    public record Result(Monster target,
                         Point targetPosition,
                         AgentAttackPlan attackPlan,
                         Monster rangedPriorityTarget) {
    }

    public record Hooks(RangedPriorityTargetSelector rangedPriorityTargetSelector,
                        CloserThreatFinder closerThreatFinder) {
    }

    @FunctionalInterface
    public interface RangedPriorityTargetSelector {
        Monster select(AgentRuntimeEntry entry, Character agent, Point agentPosition, Monster preferredTarget);
    }

    @FunctionalInterface
    public interface CloserThreatFinder {
        Monster find(AgentRuntimeEntry entry, Character agent, Point agentPosition, Point targetPosition);
    }

    public static Result commitTarget(AgentRuntimeEntry entry,
                                      Character agent,
                                      Point agentPosition,
                                      Monster target,
                                      AgentAttackPlan attackPlan,
                                      long nowMs,
                                      Hooks hooks) {
        boolean alreadyCommitted = AgentGrindTargetStateRuntime.committedTo(entry, target, nowMs);
        AgentGrindTargetStateRuntime.commitTarget(entry, target, nowMs, TARGET_COMMITMENT_MS);
        AgentGrindWanderStateRuntime.clearWanderDirection(entry);
        AgentPatrolStateRuntime.clearPatrolWanderTarget(entry);
        Point targetPosition = target.getPosition();

        Monster rangedPriorityTarget = alreadyCommitted ? null : hooks.rangedPriorityTargetSelector().select(
                entry, agent, agentPosition, target);
        if (rangedPriorityTarget != null && rangedPriorityTarget != target) {
            target = rangedPriorityTarget;
            AgentGrindTargetStateRuntime.commitTarget(
                    entry, rangedPriorityTarget, nowMs, TARGET_COMMITMENT_MS);
            targetPosition = target.getPosition();
            attackPlan = null;
        }

        Monster closerThreat = !alreadyCommitted && rangedPriorityTarget == null
                ? hooks.closerThreatFinder().find(entry, agent, agentPosition, targetPosition)
                : null;
        if (closerThreat != null && closerThreat != target) {
            target = closerThreat;
            AgentGrindTargetStateRuntime.commitTarget(
                    entry, closerThreat, nowMs, TARGET_COMMITMENT_MS);
            targetPosition = target.getPosition();
            attackPlan = null;
        }

        return new Result(target, targetPosition, attackPlan, rangedPriorityTarget);
    }
}
