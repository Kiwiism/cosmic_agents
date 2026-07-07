package server.agents.runtime;

import client.Character;
import server.agents.capabilities.combat.AgentAoeRepositionService;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentRangedPriorityTargetSelector;
import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;
import server.life.Monster;

import java.awt.Point;

/**
 * Temporary Agent-owned runtime bridge for grind combat helper callbacks.
 */
public final class AgentGrindCombatRuntime {
    private AgentGrindCombatRuntime() {
    }

    public static Point resolveAoeReposition(BotEntry entry,
                                             Character agent,
                                             Monster target,
                                             AgentAttackPlan attackPlan,
                                             Point agentPosition) {
        return AgentAoeRepositionService.resolveAoeReposition(entry, agent, target, attackPlan, agentPosition);
    }

    public static Monster selectPriorityRangedAttackTarget(AgentRuntimeEntry entry,
                                                           Character agent,
                                                           Point agentPosition,
                                                           Monster preferredTarget) {
        return AgentRangedPriorityTargetSelector.selectPriorityRangedAttackTarget(
                entry,
                agent,
                agentPosition,
                preferredTarget);
    }
}
