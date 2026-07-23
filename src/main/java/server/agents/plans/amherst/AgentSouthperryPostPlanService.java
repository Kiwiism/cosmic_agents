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
    private static final long INITIAL_FIDGET_DELAY_MIN_MS = config.AgentTuning.longValue(
            "server.agents.plans.amherst.AgentSouthperryPostPlanService.INITIAL_FIDGET_DELAY_MIN_MS");
    private static final long INITIAL_FIDGET_DELAY_MAX_EXCLUSIVE_MS = config.AgentTuning.longValue(
            "server.agents.plans.amherst.AgentSouthperryPostPlanService.INITIAL_FIDGET_DELAY_MAX_EXCLUSIVE_MS");
    private static final int FIDGET_DURATION_MIN_MS = config.AgentTuning.intValue(
            "server.agents.plans.amherst.AgentSouthperryPostPlanService.FIDGET_DURATION_MIN_MS");
    private static final int FIDGET_DURATION_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.plans.amherst.AgentSouthperryPostPlanService.FIDGET_DURATION_MAX_EXCLUSIVE_MS");
    private static final long REPEAT_FIDGET_DELAY_MIN_MS = config.AgentTuning.longValue(
            "server.agents.plans.amherst.AgentSouthperryPostPlanService.REPEAT_FIDGET_DELAY_MIN_MS");
    private static final long REPEAT_FIDGET_DELAY_MAX_EXCLUSIVE_MS = config.AgentTuning.longValue(
            "server.agents.plans.amherst.AgentSouthperryPostPlanService.REPEAT_FIDGET_DELAY_MAX_EXCLUSIVE_MS");
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
                    nowMs + ThreadLocalRandom.current().nextLong(
                            INITIAL_FIDGET_DELAY_MIN_MS,
                            INITIAL_FIDGET_DELAY_MAX_EXCLUSIVE_MS));
            return false;
        }
        if (nowMs < nextAtMs) {
            return false;
        }
        AgentFidgetMode mode = STATIONARY_FIDGETS.get(
                ThreadLocalRandom.current().nextInt(STATIONARY_FIDGETS.size()));
        int durationMs = ThreadLocalRandom.current().nextInt(
                FIDGET_DURATION_MIN_MS,
                FIDGET_DURATION_MAX_EXCLUSIVE_MS);
        AgentFidgetService.startFidget(
                entry, mode, nowMs, durationMs, AgentFidgetTrigger.PROFILE_NAVIGATION);
        entry.behaviorProfileState().setNextNavigationFidgetAtMs(
                nowMs + durationMs + ThreadLocalRandom.current().nextLong(
                        REPEAT_FIDGET_DELAY_MIN_MS,
                        REPEAT_FIDGET_DELAY_MAX_EXCLUSIVE_MS));
        return AgentFidgetService.tryHandleProfileNavigationTick(entry, position, nowMs);
    }
}
