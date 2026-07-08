package server.agents.capabilities.combat;

import client.Character;
import server.agents.integration.AgentAoeRepositionStateRuntime;
import server.agents.integration.AgentCombatAoeRepositionRuntime;
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
        if (AgentAoeRepositionStateRuntime.hasAnchor(entry)) {
            boolean done = AgentAoeRepositionStateRuntime.isExpiredOrArrived(
                    entry, agentPosition, now, AgentCombatConfig.cfg.AOE_REPOSITION_ARRIVAL_X)
                    || target == null || !target.isAlive();
            if (done) {
                AgentAoeRepositionStateRuntime.clear(entry);
                return null;
            }
            return AgentAoeRepositionStateRuntime.anchor(entry);
        }
        Point anchor = AgentCombatAoeRepositionRuntime.aoeRepositionTarget(
                entry, agent, target, attackPlan, AgentCombatConfig.cfg);
        if (anchor != null) {
            AgentAoeRepositionStateRuntime.setAnchor(
                    entry, anchor, now + AgentCombatConfig.cfg.AOE_REPOSITION_MAX_MS);
        }
        return anchor;
    }
}
