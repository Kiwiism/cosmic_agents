package server.agents.capabilities.combat;

import client.Character;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotGrindWanderStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.bots.BotEntry;
import server.life.Monster;

import java.awt.Point;

public final class AgentGrindTargetCommitmentService {
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
        Monster select(BotEntry entry, Character agent, Point agentPosition, Monster preferredTarget);
    }

    @FunctionalInterface
    public interface CloserThreatFinder {
        Monster find(Character agent, Point agentPosition, Point targetPosition);
    }

    public static Result commitTarget(BotEntry entry,
                                      Character agent,
                                      Point agentPosition,
                                      Monster target,
                                      AgentAttackPlan attackPlan,
                                      Hooks hooks) {
        AgentBotGrindTargetStateRuntime.setTarget(entry, target);
        AgentBotGrindWanderStateRuntime.clearWanderDirection(entry);
        AgentBotPatrolStateRuntime.clearPatrolWanderTarget(entry);
        Point targetPosition = target.getPosition();

        Monster rangedPriorityTarget = hooks.rangedPriorityTargetSelector().select(
                entry, agent, agentPosition, target);
        if (rangedPriorityTarget != null && rangedPriorityTarget != target) {
            target = rangedPriorityTarget;
            AgentBotGrindTargetStateRuntime.setTarget(entry, rangedPriorityTarget);
            targetPosition = target.getPosition();
            attackPlan = null;
        }

        Monster closerThreat = rangedPriorityTarget == null
                ? hooks.closerThreatFinder().find(agent, agentPosition, targetPosition)
                : null;
        if (closerThreat != null && closerThreat != target) {
            target = closerThreat;
            AgentBotGrindTargetStateRuntime.setTarget(entry, closerThreat);
            targetPosition = target.getPosition();
            attackPlan = null;
        }

        return new Result(target, targetPosition, attackPlan, rangedPriorityTarget);
    }
}
