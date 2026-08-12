package server.agents.capabilities.navigation;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.movement.AgentMovementSkillConfig;
import server.agents.capabilities.movement.AgentMovementSkillState;
import server.agents.capabilities.movement.AgentMovementSkillStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.List;

/** Read-only comparison between the live route and movement-skill-aware routing. */
public final class AgentMovementSkillShadowDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(AgentMovementSkillShadowDiagnostics.class);

    private AgentMovementSkillShadowDiagnostics() {
    }

    public static void compare(AgentNavigationGraph graph,
                               AgentRuntimeEntry entry,
                               Character agent,
                               Point startPosition,
                               int startRegionId,
                               int targetRegionId,
                               Point targetPosition,
                               AgentNavigationGraph.Edge liveEdge) {
        if (graph == null || entry == null || agent == null
                || startPosition == null || targetPosition == null
                || startRegionId < 0 || targetRegionId < 0
                || !AgentMovementSkillPolicy.shouldCompareShadowMovementSkill(agent)) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        AgentMovementSkillState state = AgentMovementSkillStateRuntime.state(entry);
        if (nowMs - state.lastShadowLogAtMs() < AgentMovementSkillConfig.SHADOW_LOG_INTERVAL_MS) {
            return;
        }
        state.setLastShadowLogAtMs(nowMs);

        List<AgentNavigationGraph.Edge> shadowPath = AgentNavigationPathService.findShadowSkillPath(
                graph, agent, startPosition, startRegionId, targetRegionId, targetPosition);
        AgentNavigationGraph.Edge firstSkillEdge = shadowPath.stream()
                .filter(AgentMovementSkillPolicy::isSkillEdge)
                .findFirst()
                .orElse(null);
        if (firstSkillEdge == null) {
            log.debug("Agent movement-skill shadow route found no usable skill edge agent={} map={} targetRegion={}",
                    agent.getName(), agent.getMapId(), targetRegionId);
            return;
        }
        log.info("Agent movement-skill shadow route agent={} map={} liveEdge={} skillEdge={} "
                        + "skillFrom={} skillTo={} shadowEdges={}",
                agent.getName(), agent.getMapId(),
                liveEdge != null ? liveEdge.type : "none",
                firstSkillEdge.type, firstSkillEdge.fromRegionId, firstSkillEdge.toRegionId,
                shadowPath.size());
    }
}
