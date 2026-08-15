package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;

public final class AgentGrindTargetCommitmentService {
    private static final long BASE_TARGET_COMMITMENT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.combat.AgentGrindTargetCommitmentService.BASE_TARGET_COMMITMENT_MS");
    private static final long MAX_TARGET_COMMITMENT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.combat.AgentGrindTargetCommitmentService.MAX_TARGET_COMMITMENT_MS");

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
        long durationMs = commitmentDurationMs(switchCount);
        AgentGrindTargetStateRuntime.commitTarget(entry, target, nowMs, durationMs);
    }

    static long commitmentDurationMs(int switchCount) {
        int boundedSwitchCount = Math.clamp(switchCount, 0, 2);
        long scaled = BASE_TARGET_COMMITMENT_MS << boundedSwitchCount;
        return Math.min(MAX_TARGET_COMMITMENT_MS, scaled);
    }

    static void recordDamageProgress(AgentRuntimeEntry entry, int mobObjectId, long nowMs) {
        Monster target = AgentGrindTargetStateRuntime.target(entry);
        if (target != null && target.isAlive() && target.getObjectId() == mobObjectId) {
            commitTarget(entry, target, nowMs);
        }
    }
}
