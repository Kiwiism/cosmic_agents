package server.agents.capabilities.combat;

import client.Character;
import server.agents.integration.AgentBotAoeRepositionStateRuntime;
import server.agents.integration.AgentBotCombatAoeRepositionRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;

/**
 * Agent-owned AoE reposition commitment service.
 */
public final class AgentAoeRepositionService {
    private AgentAoeRepositionService() {
    }

    public static Point resolveAoeReposition(AgentRuntimeEntry entry,
                                             Character agent,
                                             Monster target,
                                             AgentAttackPlan attackPlan,
                                             Point agentPosition) {
        long now = System.currentTimeMillis();
        if (AgentBotAoeRepositionStateRuntime.hasAnchor(entry)) {
            boolean done = AgentBotAoeRepositionStateRuntime.isExpiredOrArrived(
                    entry, agentPosition, now, AgentCombatConfig.cfg.AOE_REPOSITION_ARRIVAL_X)
                    || target == null || !target.isAlive();
            if (done) {
                AgentBotAoeRepositionStateRuntime.clear(entry);
                return null;
            }
            return AgentBotAoeRepositionStateRuntime.anchor(entry);
        }
        Point anchor = AgentBotCombatAoeRepositionRuntime.aoeRepositionTarget(
                entry, agent, target, attackPlan, AgentCombatConfig.cfg);
        if (anchor != null) {
            AgentBotAoeRepositionStateRuntime.setAnchor(
                    entry, anchor, now + AgentCombatConfig.cfg.AOE_REPOSITION_MAX_MS);
        }
        return anchor;
    }
}
