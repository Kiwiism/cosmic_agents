package server.agents.plans.amherst;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.objective.AgentPlanCompletionMode;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentSouthperryPostPlanService {
    private static final List<AgentFidgetMode> STATIONARY_FIDGETS = List.of(
            AgentFidgetMode.WAIT, AgentFidgetMode.PRONE, AgentFidgetMode.SPAM_PRONE);

    private AgentSouthperryPostPlanService() {
    }

    public static boolean startWander(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || agent.getMapId() != MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID) {
            return false;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        int regionId = graph == null ? -1 : graph.findRegionId(agent.getMap(), agent.getPosition());
        if (regionId < 0) {
            return false;
        }
        AgentModeService.startPatrol(entry, regionId, AgentMovementStateResetService::clearNavigationState);
        return true;
    }

    static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null
                || agent.getMapId() != MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID
                || MapleIslandObjectiveRandomnessRuntime.selectPostPlanBehavior(
                entry, agent.getMapId()) != AgentPlanCompletionMode.FIDGET) {
            return false;
        }
        Point position = agent.getPosition();
        if (position == null) {
            return false;
        }
        if (AgentFidgetStateRuntime.active(entry)) {
            return AgentFidgetService.tryHandleProfileNavigationTick(entry, position, nowMs);
        }
        long nextAtMs = entry.behaviorProfileState().nextNavigationFidgetAtMs();
        if (nextAtMs == 0L) {
            entry.behaviorProfileState().setNextNavigationFidgetAtMs(
                    nowMs + ThreadLocalRandom.current().nextLong(3_000L, 8_001L));
            return false;
        }
        if (nowMs < nextAtMs) {
            return false;
        }
        AgentFidgetMode mode = STATIONARY_FIDGETS.get(
                ThreadLocalRandom.current().nextInt(STATIONARY_FIDGETS.size()));
        int durationMs = ThreadLocalRandom.current().nextInt(2_000, 5_001);
        AgentFidgetService.startFidget(
                entry, mode, nowMs, durationMs, AgentFidgetTrigger.PROFILE_NAVIGATION);
        entry.behaviorProfileState().setNextNavigationFidgetAtMs(
                nowMs + durationMs + ThreadLocalRandom.current().nextLong(8_000L, 20_001L));
        return AgentFidgetService.tryHandleProfileNavigationTick(entry, position, nowMs);
    }
}
