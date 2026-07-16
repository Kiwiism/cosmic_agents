package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;

public final class AgentGrindTargetCommitmentService {
    static final long[] TARGET_COMMITMENT_MS = {12_000L, 20_000L, 35_000L, 60_000L};

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
        commitTarget(entry, target, nowMs);
        AgentGrindWanderStateRuntime.clearWanderDirection(entry);
        AgentPatrolStateRuntime.clearPatrolWanderTarget(entry);
        Point targetPosition = target.getPosition();

        Monster rangedPriorityTarget = alreadyCommitted ? null : hooks.rangedPriorityTargetSelector().select(
                entry, agent, agentPosition, target);
        if (rangedPriorityTarget != null && rangedPriorityTarget != target) {
            target = rangedPriorityTarget;
            commitTarget(entry, rangedPriorityTarget, nowMs);
            targetPosition = target.getPosition();
            attackPlan = null;
        }

        Monster closerThreat = !alreadyCommitted && rangedPriorityTarget == null
                ? hooks.closerThreatFinder().find(entry, agent, agentPosition, targetPosition)
                : null;
        if (closerThreat != null && closerThreat != target) {
            target = closerThreat;
            commitTarget(entry, closerThreat, nowMs);
            targetPosition = target.getPosition();
            attackPlan = null;
        }

        return new Result(target, targetPosition, attackPlan, rangedPriorityTarget);
    }

    private static void commitTarget(AgentRuntimeEntry entry, Monster target, long nowMs) {
        Monster previous = AgentGrindTargetStateRuntime.target(entry);
        int switchCount = AgentGrindTargetStateRuntime.targetSwitchCount(entry);
        if (previous != null && previous != target) {
            switchCount = previous.isAlive() ? switchCount + 1 : 0;
        }
        long durationMs = TARGET_COMMITMENT_MS[Math.min(switchCount, TARGET_COMMITMENT_MS.length - 1)];
        AgentGrindTargetStateRuntime.commitTarget(entry, target, nowMs, durationMs);
    }
}
