package server.agents.capabilities.partyquest.kpq;

import client.BuffStat;
import client.Character;
import client.Job;
import constants.skills.Rogue;
import server.agents.capabilities.combat.AgentCombatBuffRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * A bounded KPQ Stage 1 landing guard. It owns only the scenario intent and
 * delegates skill execution and movement to their shared capabilities.
 */
final class AgentKpqStageOneThiefLandingRuntime {
    private static final PrimitiveCapabilityGateway ACTIONS =
            AgentPrimitiveCapabilityGatewayRuntime.gateway();
    private static final int EDGE_MARGIN_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqStageOneThiefLandingRuntime.EDGE_MARGIN_PX");
    private static final int TARGET_REACHED_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqStageOneThiefLandingRuntime.TARGET_REACHED_PX");
    private static final long REPOSITION_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqStageOneThiefLandingRuntime.REPOSITION_TIMEOUT_MS");

    private AgentKpqStageOneThiefLandingRuntime() {
    }

    /** Returns true while ordinary Stage 1 grinding should yield to repositioning. */
    static boolean tick(
            AgentKpqMemberState member, AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (member == null || entry == null || agent == null || agent.getPosition() == null
                || agent.getJob() == null || !agent.getJob().isA(Job.THIEF)) {
            return false;
        }

        Point activeTarget = member.stage1LandingSafetyTarget();
        if (activeTarget != null) {
            if (nowMs >= member.stage1LandingSafetyDeadlineMs()
                    || AgentKpqStageOneThiefLandingPolicy.reached(
                    agent.getPosition(), activeTarget, TARGET_REACHED_PX)) {
                finish(member, entry, agent);
                return false;
            }
            ACTIONS.navigate(entry, activeTarget, true);
            return true;
        }

        boolean climbing = AgentMovementStateRuntime.climbing(entry);
        if (climbing) {
            int currentY = agent.getPosition().y;
            boolean descending = AgentKpqStageOneThiefLandingPolicy.descending(
                    member.stage1RopeObserved(), member.stage1LastRopeY(), currentY,
                    AgentMovementStateRuntime.movementVelocityY(entry));
            member.observeStage1Rope(currentY, descending);
            return false;
        }

        if (!member.stage1RopeObserved()) {
            return false;
        }
        boolean descended = member.stage1DescendingRopeObserved();
        member.clearStage1RopeObservation();
        if (!descended || AgentMovementStateRuntime.inAir(entry)) {
            return false;
        }

        Point target = landingInteriorTarget(agent);
        if (target == null || AgentKpqStageOneThiefLandingPolicy.reached(
                agent.getPosition(), target, TARGET_REACHED_PX)) {
            return false;
        }
        boolean alreadyHidden = agent.getBuffedValue(BuffStat.DARKSIGHT) != null;
        boolean cast = alreadyHidden
                || AgentCombatBuffRuntime.tryCastExplicitUtilityBuff(entry, agent, Rogue.DARK_SIGHT);
        if (!cast) {
            return false;
        }
        member.beginStage1LandingSafety(
                target, nowMs + REPOSITION_TIMEOUT_MS, !alreadyHidden);
        ACTIONS.navigate(entry, target, true);
        return true;
    }

    static void cancel(
            AgentKpqMemberState member, AgentRuntimeEntry entry, Character agent) {
        if (member == null) return;
        member.clearStage1RopeObservation();
        if (entry == null || agent == null || member.stage1LandingSafetyTarget() == null) {
            member.clearStage1LandingSafety();
            return;
        }
        finish(member, entry, agent);
    }

    private static Point landingInteriorTarget(Character agent) {
        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(agent.getMap());
        if (graph == null) return null;
        int regionId = graph.findRegionId(agent.getMap(), agent.getPosition());
        AgentNavigationGraph.Region region = graph.regionsById.get(regionId);
        return AgentKpqStageOneThiefLandingPolicy.nearestInteriorPoint(
                agent.getPosition(), region, EDGE_MARGIN_PX);
    }

    private static void finish(
            AgentKpqMemberState member, AgentRuntimeEntry entry, Character agent) {
        ACTIONS.stop(entry);
        if (member.stage1ManagedDarkSight()
                && agent.getBuffedValue(BuffStat.DARKSIGHT) != null) {
            agent.cancelEffectFromBuffStat(BuffStat.DARKSIGHT);
        }
        member.clearStage1RopeObservation();
        member.clearStage1LandingSafety();
    }
}
