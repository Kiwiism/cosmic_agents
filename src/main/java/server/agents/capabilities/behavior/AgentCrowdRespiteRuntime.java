package server.agents.capabilities.behavior;

import client.Character;
import config.YamlConfig;
import constants.id.ItemId;
import server.agents.behavior.AgentBehaviorPolicyProfile;
import server.agents.behavior.AgentBehaviorRuntime;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.movement.AgentMovementTickCoordinator;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.plans.AgentPlanPauseRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.agents.operations.events.AgentCrowdRespiteEvent;

import java.awt.Point;

/** Execution gate for crowd rests. It pauses time, not the current objective. */
public final class AgentCrowdRespiteRuntime {
    public static final String PAUSE_REASON = "map-crowd-respite";

    private AgentCrowdRespiteRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs, boolean runAiTick) {
        AgentCrowdRespiteState state = entry.capabilityStates().require(AgentCrowdRespiteState.STATE_KEY);
        boolean eligible = AgentBehaviorRuntime.enabled(entry)
                && YamlConfig.config.server.AGENT_MAP_CROWD_RESPITE_ENABLED
                && AgentModeStateRuntime.grinding(entry) && agent.getHp() > 0;
        if (!eligible) {
            if (state.active()) finish(entry, agent, state, nowMs);
            return false;
        }
        if (!state.active()) {
            if (!AgentMapActivityPolicy.shouldRest(entry, agent, nowMs)) return false;
            AgentBehaviorPolicyProfile.Crowd policy = AgentBehaviorRuntime.policy(entry).crowd();
            int duration = policy.minRestMs() + AgentBehaviorRuntime.calibration(entry)
                    .nextPercent("crowd-duration") * Math.max(1, policy.maxRestMs() - policy.minRestMs()) / 100;
            boolean chair = AgentBehaviorRuntime.calibration(entry).nextPercent("crowd-chair") < policy.chairPercent();
            state.start(nowMs, nowMs + duration, AgentSafeSpotSelector.select(entry, agent), chair);
            AgentPlanPauseRuntime.pause(entry, PAUSE_REASON, nowMs);
            AgentBehaviorTelemetry.crowdRestStarted();
            publish(entry, agent, nowMs, AgentCrowdRespiteEvent.Stage.STARTED);
        }
        if (nowMs >= state.resumeAtMs() && !AgentMapActivityPolicy.shouldRest(entry, agent, nowMs)) {
            finish(entry, agent, state, nowMs);
            return false;
        }
        Point spot = state.safeSpot();
        if (spot == null) return true;
        Point position = agent.getPosition();
        if (Math.abs(position.x - spot.x) > 24 || Math.abs(position.y - spot.y) > 45) {
            if (agent.getChair() > 0) AgentChairService.stand(entry, agent);
            AgentMovementTickCoordinator.stepMovementCore(entry, spot, runAiTick);
            return true;
        }
        if (state.markSettledEventSent()) {
            publish(entry, agent, nowMs, AgentCrowdRespiteEvent.Stage.SETTLED);
        }
        if (state.chairPreferred()
                && AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(agent, ItemId.RELAXER) > 0) {
            if (agent.getChair() <= 0) AgentChairService.sit(entry, agent, ItemId.RELAXER);
            return true;
        }
        if (AgentFidgetStateRuntime.trigger(entry) != AgentFidgetTrigger.CROWD_RESPITE) {
            AgentFidgetMode mode = AgentBehaviorRuntime.calibration(entry).nextPercent("crowd-fidget") < 65
                    ? AgentFidgetMode.WAIT : AgentFidgetMode.PRONE;
            AgentFidgetService.startFidget(entry, mode, nowMs, 4000, AgentFidgetTrigger.CROWD_RESPITE);
        }
        AgentFidgetService.tryHandleCrowdRespiteTick(entry, position, nowMs);
        return true;
    }

    private static void finish(AgentRuntimeEntry entry, Character agent,
                               AgentCrowdRespiteState state, long nowMs) {
        if (agent != null && agent.getChair() > 0) AgentChairService.stand(entry, agent);
        AgentFidgetService.clear(entry);
        state.clear();
        AgentPlanPauseRuntime.resume(entry, PAUSE_REASON, nowMs);
        AgentBehaviorTelemetry.crowdRestResumed();
        publish(entry, agent, nowMs, AgentCrowdRespiteEvent.Stage.RESUMED);
    }

    private static void publish(AgentRuntimeEntry entry, Character agent, long nowMs,
                                AgentCrowdRespiteEvent.Stage stage) {
        if (agent != null) AgentSessionEventRuntime.bus(entry).publish(new AgentCrowdRespiteEvent(
                agent.getId(), nowMs, agent.getMapId(), stage, ""));
    }
}
