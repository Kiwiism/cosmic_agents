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
import server.agents.integration.cosmic.CosmicAgentPerceptionSnapshotFactory;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.agents.operations.events.AgentCrowdRespiteEvent;

import java.awt.Point;

/** Execution gate for crowd rests. It pauses time, not the current objective. */
public final class AgentCrowdRespiteRuntime {
    public static final String PAUSE_REASON = "map-crowd-respite";
    private static final int SAFE_SPOT_ARRIVAL_X_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdRespiteRuntime.SAFE_SPOT_ARRIVAL_X_PX");
    private static final int SAFE_SPOT_ARRIVAL_Y_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdRespiteRuntime.SAFE_SPOT_ARRIVAL_Y_PX");
    private static final int WAIT_FIDGET_PERCENT = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdRespiteRuntime.WAIT_FIDGET_PERCENT");
    private static final int FIDGET_DURATION_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdRespiteRuntime.FIDGET_DURATION_MS");
    private static final int REST_REENTRY_COOLDOWN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentCrowdRespiteRuntime.REST_REENTRY_COOLDOWN_MS");

    private AgentCrowdRespiteRuntime() {
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs, boolean runAiTick) {
        AgentCrowdRespiteState state = entry.capabilityStates().require(AgentCrowdRespiteState.STATE_KEY);
        boolean eligible = AgentBehaviorRuntime.enabled(entry)
                && config.AgentYamlConfig.config.agent.AGENT_MAP_CROWD_RESPITE_ENABLED
                && AgentModeStateRuntime.grinding(entry) && agent.getHp() > 0;
        if (!eligible) {
            if (state.active()) finish(entry, agent, state, nowMs);
            return false;
        }
        if (!state.active()) {
            if (!state.eligible(nowMs)) return false;
            if (!AgentMapActivityPolicy.shouldRest(entry, agent, nowMs)) return false;
            AgentBehaviorPolicyProfile.Crowd policy = AgentBehaviorRuntime.policy(entry).crowd();
            int profileDuration = policy.minRestMs() + AgentBehaviorRuntime.calibration(entry)
                    .nextPercent("crowd-duration")
                    * Math.max(1, policy.maxRestMs() - policy.minRestMs()) / 100;
            int characterCount = AgentCrowdScalingPolicy.totalCharacters(
                    CosmicAgentPerceptionSnapshotFactory.capture(agent, nowMs));
            int duration = AgentCrowdScalingPolicy.restDurationMs(
                    profileDuration, characterCount);
            boolean chair = AgentBehaviorRuntime.calibration(entry).nextPercent("crowd-chair") < policy.chairPercent();
            state.start(nowMs, nowMs + duration, AgentSafeSpotSelector.select(entry, agent), chair);
            AgentForegroundPauseRuntime.pause(entry, PAUSE_REASON, nowMs);
            AgentBehaviorTelemetry.crowdRestStarted();
            publish(entry, agent, nowMs, AgentCrowdRespiteEvent.Stage.STARTED);
        }
        if (nowMs >= state.resumeAtMs()
                || !AgentMapActivityPolicy.shouldRest(entry, agent, nowMs)) {
            finish(entry, agent, state, nowMs);
            return false;
        }
        Point spot = state.safeSpot();
        if (spot == null) return true;
        Point position = agent.getPosition();
        if (Math.abs(position.x - spot.x) > SAFE_SPOT_ARRIVAL_X_PX
                || Math.abs(position.y - spot.y) > SAFE_SPOT_ARRIVAL_Y_PX) {
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
            AgentFidgetMode mode = AgentBehaviorRuntime.calibration(entry)
                    .nextPercent("crowd-fidget") < WAIT_FIDGET_PERCENT
                    ? AgentFidgetMode.WAIT : AgentFidgetMode.PRONE;
            AgentFidgetService.startFidget(
                    entry,
                    mode,
                    nowMs,
                    FIDGET_DURATION_MS,
                    AgentFidgetTrigger.CROWD_RESPITE);
        }
        AgentFidgetService.tryHandleCrowdRespiteTick(entry, position, nowMs);
        return true;
    }

    private static void finish(AgentRuntimeEntry entry, Character agent,
                               AgentCrowdRespiteState state, long nowMs) {
        if (agent != null && agent.getChair() > 0) AgentChairService.stand(entry, agent);
        AgentFidgetService.clear(entry);
        state.finish(nowMs + REST_REENTRY_COOLDOWN_MS);
        AgentForegroundPauseRuntime.resume(entry, PAUSE_REASON, nowMs);
        AgentBehaviorTelemetry.crowdRestResumed();
        publish(entry, agent, nowMs, AgentCrowdRespiteEvent.Stage.RESUMED);
    }

    private static void publish(AgentRuntimeEntry entry, Character agent, long nowMs,
                                AgentCrowdRespiteEvent.Stage stage) {
        if (agent != null) AgentSessionEventRuntime.bus(entry).publish(new AgentCrowdRespiteEvent(
                agent.getId(), nowMs, agent.getMapId(), stage, ""));
    }
}
