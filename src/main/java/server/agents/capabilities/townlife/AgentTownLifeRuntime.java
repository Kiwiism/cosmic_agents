package server.agents.capabilities.townlife;

import client.Character;
import constants.id.ItemId;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.capabilities.navigation.AgentTravelVariationRuntime;
import server.agents.capabilities.navigation.AgentTravelVariationSettings;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.simulation.AgentAbstractExecutionScope;

import java.awt.Point;

public final class AgentTownLifeRuntime {
    private static final String PLAN_PAUSE_REASON = "town-life";
    private static final int ACTIVITY_ARRIVAL_DISTANCE_PX = config.AgentTuning.intValue("server.agents.capabilities.townlife.AgentTownLifeRuntime.ACTIVITY_ARRIVAL_DISTANCE_PX");
    private static final int ACTIVITY_ARRIVAL_VERTICAL_DISTANCE_PX = config.AgentTuning.intValue("server.agents.capabilities.townlife.AgentTownLifeRuntime.ACTIVITY_ARRIVAL_VERTICAL_DISTANCE_PX");
    private static final int MAP_SEAT_ARRIVAL_DISTANCE_PX = config.AgentTuning.intValue("server.agents.capabilities.townlife.AgentTownLifeRuntime.MAP_SEAT_ARRIVAL_DISTANCE_PX");
    private static final int SETTLING_DELAY_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.SETTLING_DELAY_MIN_MS");
    private static final int SETTLING_DELAY_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.SETTLING_DELAY_MAX_EXCLUSIVE_MS");
    private static final int RETRY_DELAY_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.RETRY_DELAY_MIN_MS");
    private static final int RETRY_DELAY_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.RETRY_DELAY_MAX_EXCLUSIVE_MS");
    private static final int ABANDON_RETRY_DELAY_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.ABANDON_RETRY_DELAY_MIN_MS");
    private static final int ABANDON_RETRY_DELAY_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.ABANDON_RETRY_DELAY_MAX_EXCLUSIVE_MS");
    private static final int ACTIVITY_TRANSITION_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.ACTIVITY_TRANSITION_MIN_MS");
    private static final int ACTIVITY_TRANSITION_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.ACTIVITY_TRANSITION_MAX_EXCLUSIVE_MS");
    private static final int MIN_FIDGET_DURATION_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.MIN_FIDGET_DURATION_MS");
    private static final int REST_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.REST_DWELL_MIN_MS");
    private static final int REST_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.REST_DWELL_MAX_EXCLUSIVE_MS");
    private static final int SOCIALIZE_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.SOCIALIZE_DWELL_MIN_MS");
    private static final int SOCIALIZE_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.SOCIALIZE_DWELL_MAX_EXCLUSIVE_MS");
    private static final int LINGER_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.LINGER_DWELL_MIN_MS");
    private static final int LINGER_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.LINGER_DWELL_MAX_EXCLUSIVE_MS");
    private static final int STROLL_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.STROLL_DWELL_MIN_MS");
    private static final int STROLL_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.STROLL_DWELL_MAX_EXCLUSIVE_MS");
    private static final int SHOW_OFF_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.SHOW_OFF_DWELL_MIN_MS");
    private static final int SHOW_OFF_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.SHOW_OFF_DWELL_MAX_EXCLUSIVE_MS");
    private static final int DEFAULT_DWELL_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.DEFAULT_DWELL_MS");
    private static final int BACKGROUND_DWELL_MAX_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.BACKGROUND_DWELL_MAX_MS");
    private static final int BACKGROUND_DWELL_MULTIPLIER = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.BACKGROUND_DWELL_MULTIPLIER");
    private static final long DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS");

    private AgentTownLifeRuntime() {
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates()
                .find(AgentTownLifeState.STATE_KEY)
                .map(AgentTownLifeState::enabled)
                .orElse(false);
    }

    public static boolean abstractEligible(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || AgentTownLifeEncounterCoordinator.active(entry)) {
            return false;
        }
        AgentTownLifeState state = entry.capabilityStates()
                .find(AgentTownLifeState.STATE_KEY)
                .orElse(null);
        if (state == null || !state.enabled() || agent.getMapId() != state.townMapId()) {
            return false;
        }
        return switch (state.stage()) {
            case SETTLING, CHOOSE_ACTIVITY, RESERVE_DESTINATION,
                    MOVE_TO_ACTIVITY, DWELL, COOLDOWN -> true;
            case DISABLED, EXITING -> false;
        };
    }

    public static void start(AgentRuntimeEntry entry,
                             int townMapId,
                             long nowMs,
                             int identitySeed) {
        request(entry, AgentTownLifeVisitRequest.leisure(townMapId), nowMs, identitySeed);
    }

    public static void request(AgentRuntimeEntry entry,
                               AgentTownLifeVisitRequest request,
                               long nowMs,
                               int identitySeed) {
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        AgentTownLifeLifecycleRuntime.start(entry, agent, request,
                AgentTownLifeAdmissionMode.MANUAL_ONLY, nowMs, identitySeed);
    }

    public static AgentTownLifeSessionResult requestLocal(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeVisitRequest request,
            AgentTownLifeAdmissionMode admissionMode,
            long nowMs,
            int identitySeed) {
        return AgentTownLifeLifecycleRuntime.start(
                entry, agent, request, admissionMode, nowMs, identitySeed);
    }

    public static AgentTownLifeSessionResult requestSession(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeEntryRequest request,
            AgentTownLifeAdmissionMode admissionMode,
            long nowMs,
            int identitySeed) {
        return AgentTownLifeLifecycleRuntime.start(
                entry, agent, request, admissionMode, nowMs, identitySeed);
    }

    static void activateLocal(AgentRuntimeEntry entry,
                              Character agent,
                              AgentTownLifeEntryRequest request,
                              String sessionId,
                              long nowMs,
                              int identitySeed) {
        entry.capabilityStates().require(AgentTownLifeState.STATE_KEY)
                .start(nowMs, identitySeed, request, sessionId);
        entry.simulationState().allowAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
        AgentForegroundPauseRuntime.pause(entry, PLAN_PAUSE_REASON, nowMs);
        AgentTravelVariationRuntime.configure(entry,
                new AgentTravelVariationSettings(
                        Integer.toUnsignedLong(identitySeed), true, 1.30d,
                        false, 0.0d, 3_000L, 0L));
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        AgentTownLifeEventPublisher.lifecycle(
                entry, agent, state, AgentTownLifeLifecycleEvent.Phase.STARTED, "", nowMs);
        AgentTownLifeEventPublisher.arrival(entry, agent, state, nowMs);
        AgentTownLifeCheckpointRuntime.persist(entry, agent, nowMs);
    }

    /**
     * Returns true when this tick is fully consumed. A false result while active means
     * the ordinary movement phase should advance the move target selected here.
     */
    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return tick(entry, agent, nowMs, AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    static boolean tick(AgentRuntimeEntry entry,
                        Character agent,
                        long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        if (entry == null || agent == null || gateway == null) {
            return false;
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (!state.enabled()) {
            return false;
        }
        AgentTownLifeFidelity previousFidelity = state.fidelity();
        AgentTownLifeFidelity fidelity = AgentTownLifeFidelityPolicy.resolve(entry, agent);
        boolean fidelityChanged = state.updateFidelity(fidelity);
        if (fidelityChanged) {
            AgentTownLifeMetrics.fidelityTransition();
        }
        if (fidelityChanged
                && fidelity == AgentTownLifeFidelity.PRESENTATION
                && previousFidelity == AgentTownLifeFidelity.BACKGROUND_ABSTRACT
                && (state.stage() == AgentTownLifeState.Stage.MOVE_TO_ACTIVITY
                || state.stage() == AgentTownLifeState.Stage.DWELL)) {
            abandonDestination(entry, agent, state, nowMs, gateway);
            return true;
        }
        if (fidelityChanged
                && fidelity == AgentTownLifeFidelity.BACKGROUND_ABSTRACT
                && state.stage() == AgentTownLifeState.Stage.MOVE_TO_ACTIVITY) {
            state.beginDwell(nowMs + dwellDuration(agent, state));
        }
        if (state.freeTimeExpired(nowMs) && !state.exitRequested()) {
            requestDefaultGracefulExit(entry, agent, state, "visit budget expired", nowMs);
        }
        if (AgentTownLifeFidelityPolicy.rendersAmbientActions(fidelity)) {
            AgentTownLifeEncounterCoordinator.tickPassive(entry, agent, state, gateway, nowMs);
        }
        if (agent.getMapId() != state.townMapId()) {
            terminateLocal(entry, agent, AgentTownLifeLifecycleEvent.Phase.FORCED,
                    "Agent left the TownLife map", nowMs);
            return true;
        }
        if (state.stage() == AgentTownLifeState.Stage.EXITING) {
            requestDefaultGracefulExit(entry, agent, state, "TownLife entered exiting stage", nowMs);
        }
        if (state.exitDeadlineExpired(nowMs)) {
            if (!state.activityResult().terminal() && state.activity() != AgentTownLifeState.Activity.NONE) {
                state.markActivityResult(AgentTownLifeActivityResult.TIMED_OUT);
            }
            terminateLocal(entry, agent, AgentTownLifeLifecycleEvent.Phase.TIMED_OUT,
                    state.exitReason(), nowMs);
            return true;
        }
        if (readyForGracefulExit(entry, state)) {
            terminateLocal(entry, agent, AgentTownLifeLifecycleEvent.Phase.EXITED,
                    state.exitReason(), nowMs);
            return true;
        }
        boolean consumed = AgentTownLifeActivityRuntime.tick(entry, agent, state, nowMs, gateway);
        if (state.exitRequested() && readyForGracefulExit(entry, state)) {
            terminateLocal(entry, agent, AgentTownLifeLifecycleEvent.Phase.EXITED,
                    state.exitReason(), nowMs);
            return true;
        }
        return consumed;
    }

    public static void stop(AgentRuntimeEntry entry, Character agent) {
        requestGracefulStop(entry, agent, "requested", System.currentTimeMillis());
    }

    public static AgentTownLifeExitResult requestGracefulStop(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        if (entry == null || agent == null) {
            return new AgentTownLifeExitResult(
                    AgentTownLifeExitResult.Status.REJECTED_INVALID_REQUEST, "",
                    "entry and agent are required");
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (!state.enabled()) {
            return new AgentTownLifeExitResult(
                    AgentTownLifeExitResult.Status.NOT_ACTIVE, "", "TownLife is not active");
        }
        AgentTownLifeSessionHandle handle = state.sessionHandle(agent.getId());
        if (handle == null) {
            return new AgentTownLifeExitResult(
                    AgentTownLifeExitResult.Status.REJECTED_INVALID_REQUEST, "",
                    "active TownLife session has no handle");
        }
        return requestExit(entry, agent, AgentTownLifeExitRequest.graceful(
                handle, reason, nowMs, nowMs + DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS));
    }

    public static AgentTownLifeExitResult requestExit(
            AgentRuntimeEntry entry, Character agent, AgentTownLifeExitRequest request) {
        return AgentTownLifeLifecycleRuntime.requestExit(entry, agent, request);
    }

    public static void forceStop(AgentRuntimeEntry entry, Character agent, String reason) {
        AgentTownLifeLifecycleRuntime.stop(entry, agent, reason);
    }

    static void terminateLocal(AgentRuntimeEntry entry,
                               Character agent,
                               AgentTownLifeLifecycleEvent.Phase phase,
                               String reason,
                               long nowMs) {
        if (entry == null) {
            return;
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (!state.enabled()) {
            return;
        }
        if (!state.activityResult().terminal()
                && state.activity() != AgentTownLifeState.Activity.NONE) {
            state.markActivityResult(phase == AgentTownLifeLifecycleEvent.Phase.TIMED_OUT
                    ? AgentTownLifeActivityResult.TIMED_OUT
                    : AgentTownLifeActivityResult.CANCELLED);
        }
        AgentTownLifeEventPublisher.lifecycle(entry, agent, state, phase, reason, nowMs);
        entry.simulationState().clearAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
        AgentTownLifeEncounterCoordinator.finish(
                entry, agent, phase == AgentTownLifeLifecycleEvent.Phase.EXITED, nowMs);
        AgentTownLifeActivityExtensionRuntime.cancel(entry, agent, state, nowMs);
        entry.capabilityStates().require(AgentTownLifeActivitySequenceState.STATE_KEY).clear();
        AgentTownLifeActivityExtensionRuntime.clear(entry);
        state.stop();
        AgentForegroundPauseRuntime.resume(entry, PLAN_PAUSE_REASON, nowMs);
        AgentTownLifeDestinationService.release(agent);
        AgentFidgetService.clear(entry);
        if (agent != null && agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
        }
        if (entry.simulationState().mode()
                != server.agents.runtime.simulation.AgentSimulationMode.BACKGROUND_ABSTRACT) {
            AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        }
        AgentTownLifeCheckpointRuntime.delete(agent);
    }

    static boolean tickSettling(AgentRuntimeEntry entry,
                                        Character agent,
                                        AgentTownLifeState state,
                                        long nowMs,
                                        PrimitiveCapabilityGateway gateway) {
        if (AgentTownLifeFidelityPolicy.usesPhysicalNavigation(state.fidelity())) {
            gateway.stop(entry);
        }
        if (nowMs < state.nextActionAtMs()) {
            return true;
        }
        if (AgentTownLifeFidelityPolicy.rendersAmbientActions(state.fidelity())) {
            agent.changeFaceExpression(AgentEmote.HAPPY.getValue());
        }
        state.transition(AgentTownLifeState.Stage.CHOOSE_ACTIVITY,
                nowMs + delay(
                        agent, state, SETTLING_DELAY_MIN_MS, SETTLING_DELAY_MAX_EXCLUSIVE_MS));
        return true;
    }

    static boolean chooseActivity(AgentRuntimeEntry entry,
                                          Character agent,
                                          AgentTownLifeState state,
                                          long nowMs,
                                          PrimitiveCapabilityGateway gateway) {
        if (nowMs < state.nextActionAtMs()) {
            return true;
        }
        if (AgentTownLifeFidelityPolicy.rendersAmbientActions(state.fidelity())
                && agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
        }
        AgentFidgetService.clear(entry);
        AgentTownLifeRolePolicy.resolve(entry, agent, state, nowMs);
        AgentTownLifeDecision decision = AgentTownLifeControllerRuntime.choose(entry, agent, state, nowMs);
        if (!AgentTownLifeFidelityPolicy.createsEncounters(state.fidelity())
                && (decision.activity() == AgentTownLifeState.Activity.SOCIALIZE
                || decision.activity() == AgentTownLifeState.Activity.SHOW_OFF)) {
            decision = AgentTownLifeDecision.deterministic(AgentTownLifeState.Activity.STROLL);
        }
        AgentTownLifeDestinationService.Destination destination =
                AgentTownLifeDestinationService.select(
                        entry, agent, state, decision, nowMs, gateway);
        if (destination == null) {
            state.transition(AgentTownLifeState.Stage.CHOOSE_ACTIVITY,
                    nowMs + delay(
                            agent, state, RETRY_DELAY_MIN_MS, RETRY_DELAY_MAX_EXCLUSIVE_MS));
            return true;
        }
        state.select(destination.activity(), destination.point(), destination.targetCharacterId(),
                destination.key(), destination.venueId(),
                decision.source(), decision.correlationId(), nowMs);
        state.memory().remember(destination.activity(), destination.key(), nowMs);
        entry.capabilityStates().require(AgentTownLifeActivitySequenceState.STATE_KEY).clear();
        AgentTownLifeActivityExtensionRuntime.clear(entry);
        AgentTownLifeEventPublisher.activity(
                entry, agent, state, AgentTownLifeActivityEvent.Phase.SELECTED, nowMs);
        if ((destination.activity() == AgentTownLifeState.Activity.SOCIALIZE
                || destination.activity() == AgentTownLifeState.Activity.SHOW_OFF)
                && !AgentTownLifeEncounterCoordinator.begin(
                entry, agent, state, decision.encounterType(), gateway, nowMs)) {
            abandonDestination(entry, agent, state, nowMs, gateway);
            return true;
        }
        if (!AgentTownLifeFidelityPolicy.usesPhysicalNavigation(state.fidelity())) {
            AgentTownLifeEventPublisher.activity(
                    entry, agent, state, AgentTownLifeActivityEvent.Phase.ARRIVED, nowMs);
            state.beginDwell(nowMs + dwellDuration(agent, state));
            entry.capabilityStates().require(AgentTownLifeActivitySequenceState.STATE_KEY)
                    .start(nowMs, state.nextActionAtMs());
            AgentTownLifeEventPublisher.activity(
                    entry, agent, state, AgentTownLifeActivityEvent.Phase.ORIENTING, nowMs);
        }
        return true;
    }

    static boolean moveToActivity(AgentRuntimeEntry entry,
                                          Character agent,
                                          AgentTownLifeState state,
                                          long nowMs,
                                          PrimitiveCapabilityGateway gateway) {
        Point target = state.target();
        if (target == null) {
            AgentTownLifeDestinationService.release(agent);
            state.markActivityResult(AgentTownLifeActivityResult.ABANDONED);
            AgentTownLifeEventPublisher.activity(
                    entry, agent, state, AgentTownLifeActivityEvent.Phase.ABANDONED, nowMs);
            state.transition(AgentTownLifeState.Stage.CHOOSE_ACTIVITY, nowMs);
            return true;
        }
        int arrivalDistance = state.activity() == AgentTownLifeState.Activity.REST
                && townProfile(state).mapSeatId(target) >= 0
                ? MAP_SEAT_ARRIVAL_DISTANCE_PX
                : ACTIVITY_ARRIVAL_DISTANCE_PX;
        if (!gateway.grounded(agent)
                || Math.abs(agent.getPosition().y - target.y) > ACTIVITY_ARRIVAL_VERTICAL_DISTANCE_PX
                || agent.getPosition().distanceSq(target) > arrivalDistance * arrivalDistance) {
            AgentTownLifeProgressWatchdog.Result progress =
                    state.progressWatchdog().observe(
                            agent.getPosition(), nowMs, navigationTimeoutMs(state));
            if (progress != AgentTownLifeProgressWatchdog.Result.PROGRESSING) {
                AgentTownLifeMetrics.navigationAbandon();
                abandonDestination(entry, agent, state, nowMs, gateway);
                return true;
            }
            gateway.navigate(entry, target, false);
            return false;
        }
        gateway.stop(entry);
        if (state.activity() == AgentTownLifeState.Activity.STROLL) {
            state.markInitialPlacementComplete();
        }
        Point facing = peerPosition(state, agent);
        gateway.facePosition(agent, facing == null ? target : facing);
        AgentTownLifeEventPublisher.activity(
                entry, agent, state, AgentTownLifeActivityEvent.Phase.ARRIVED, nowMs);
        AgentTownLifeEncounterCoordinator.Activation activation =
                AgentTownLifeEncounterCoordinator.activate(entry, agent, nowMs);
        if (activation == AgentTownLifeEncounterCoordinator.Activation.WAITING) {
            return true;
        }
        if (activation == AgentTownLifeEncounterCoordinator.Activation.CANCELLED) {
            abandonDestination(entry, agent, state, nowMs, gateway);
            return true;
        }
        state.beginDwell(nowMs + dwellDuration(agent, state));
        entry.capabilityStates().require(AgentTownLifeActivitySequenceState.STATE_KEY)
                .start(nowMs, state.nextActionAtMs());
        AgentTownLifeEventPublisher.activity(
                entry, agent, state, AgentTownLifeActivityEvent.Phase.ORIENTING, nowMs);
        return true;
    }

    static boolean tickDwell(AgentRuntimeEntry entry,
                                     Character agent,
                                     AgentTownLifeState state,
                                     long nowMs,
                                     PrimitiveCapabilityGateway gateway) {
        AgentTownLifeEncounterState.Snapshot encounter = entry.capabilityStates()
                .require(AgentTownLifeEncounterState.STATE_KEY).snapshot();
        if (state.activity() == AgentTownLifeState.Activity.LOCAL_ACTIVITY) {
            AgentTownLifeActivityResult extensionResult =
                    AgentTownLifeActivityExtensionRuntime.tick(entry, agent, state, nowMs);
            if (extensionResult.terminal()) {
                return finishActivity(
                        entry, agent, state, gateway, nowMs, extensionResult,
                        eventPhase(extensionResult));
            }
        }
        if (nowMs >= state.nextActionAtMs() && encounter.active()
                && encounter.role() == AgentTownLifeEncounterState.Role.RESPONDER) {
            state.beginDwell(nowMs + ACTIVITY_TRANSITION_MIN_MS);
            return true;
        }
        if (nowMs >= state.nextActionAtMs()) {
            return finishActivity(
                    entry, agent, state, gateway, nowMs,
                    AgentTownLifeActivityResult.COMPLETED,
                    AgentTownLifeActivityEvent.Phase.COMPLETED);
        }
        AgentTownLifeActivitySequenceState sequence = entry.capabilityStates()
                .require(AgentTownLifeActivitySequenceState.STATE_KEY);
        if (sequence.phase() == AgentTownLifeActivitySequenceState.Phase.IDLE) {
            sequence.start(nowMs, state.nextActionAtMs());
        }
        AgentTownLifeActivitySequenceState.Phase previousPhase = sequence.phase();
        AgentTownLifeActivitySequenceState.Phase phase = sequence.advance(nowMs);
        if (phase != previousPhase) {
            AgentTownLifeEventPublisher.activity(entry, agent, state, eventPhase(phase), nowMs);
            if (phase == AgentTownLifeActivitySequenceState.Phase.REACTION) {
                AgentTownLifeEncounterCoordinator.requestReaction(entry, agent, nowMs);
            } else if (phase == AgentTownLifeActivitySequenceState.Phase.CLOSING) {
                AgentTownLifeEncounterCoordinator.beginClosing(entry, agent, nowMs);
            }
        }
        boolean render = AgentTownLifeFidelityPolicy.rendersAmbientActions(state.fidelity());
        if (render
                && phase.ordinal() >= AgentTownLifeActivitySequenceState.Phase.OPENING.ordinal()
                && !state.expressionShown()) {
            agent.changeFaceExpression(expressionFor(agent, state));
            state.markExpressionShown();
        }
        if (state.activity() == AgentTownLifeState.Activity.REST) {
            AgentFidgetService.clear(entry);
            if (!render) {
                return true;
            }
            if (phase.ordinal() < AgentTownLifeActivitySequenceState.Phase.OPENING.ordinal()) {
                return true;
            }
            if (agent.getChair() < 0) {
                int mapSeatId = townProfile(state).mapSeatId(state.target());
                if (mapSeatId >= 0) {
                    gateway.sitMapSeat(agent, mapSeatId, state.target());
                } else if (gateway.itemCount(agent, ItemId.RELAXER) > 0) {
                    gateway.sitChair(agent, ItemId.RELAXER);
                }
            }
            return true;
        }
        Point facing = peerPosition(state, agent);
        if (facing == null) {
            facing = state.target() == null ? agent.getPosition() : state.target();
        }
        if (render) {
            gateway.facePosition(agent, facing);
        }
        if (render && phase == AgentTownLifeActivitySequenceState.Phase.PERFORMING
                && state.activity() == AgentTownLifeState.Activity.SHOW_OFF
                && !state.flourishShown()) {
            AgentTownLifeVisualService.flourish(agent, facing);
            state.markFlourishShown();
        }
        if (render && phase == AgentTownLifeActivitySequenceState.Phase.PERFORMING
                && !sequence.performanceStarted()) {
            beginDwellMotion(entry, agent, state, nowMs);
            sequence.markPerformanceStarted();
        }
        if (render && phase == AgentTownLifeActivitySequenceState.Phase.PERFORMING
                && AgentFidgetStateRuntime.active(entry)) {
            AgentFidgetService.tryHandleTownLifeTick(entry, facing, nowMs);
        }
        return true;
    }

    static boolean tickCooldown(AgentTownLifeState state, long nowMs) {
        if (nowMs >= state.nextActionAtMs()) {
            state.transition(AgentTownLifeState.Stage.CHOOSE_ACTIVITY, nowMs);
        }
        return true;
    }

    private static void beginDwellMotion(AgentRuntimeEntry entry,
                                         Character agent,
                                         AgentTownLifeState state,
                                         long nowMs) {
        AgentFidgetMode mode = AgentTownLifeFidgetPolicy.choose(agent, state);
        if (mode != AgentFidgetMode.NONE) {
            int duration = (int) Math.max(
                    MIN_FIDGET_DURATION_MS, state.nextActionAtMs() - nowMs);
            AgentFidgetService.startFidget(entry, mode, nowMs, duration, AgentFidgetTrigger.TOWN_LIFE);
        }
    }

    private static long dwellDuration(Character agent, AgentTownLifeState state) {
        AgentTownLifeProfile.PlatformPolicy platformPolicy = platformPolicy(state);
        if (platformPolicy != null) {
            return delay(agent, state, platformPolicy.dwellMinMs(),
                    platformPolicy.dwellMaxExclusiveMs());
        }
        long duration = switch (state.activity()) {
            case REST -> delay(agent, state, REST_DWELL_MIN_MS, REST_DWELL_MAX_EXCLUSIVE_MS);
            case SOCIALIZE -> delay(
                    agent, state, SOCIALIZE_DWELL_MIN_MS, SOCIALIZE_DWELL_MAX_EXCLUSIVE_MS);
            case LINGER -> delay(agent, state, LINGER_DWELL_MIN_MS, LINGER_DWELL_MAX_EXCLUSIVE_MS);
            case STROLL -> delay(agent, state, STROLL_DWELL_MIN_MS, STROLL_DWELL_MAX_EXCLUSIVE_MS);
            case SHOW_OFF -> delay(
                    agent, state, SHOW_OFF_DWELL_MIN_MS, SHOW_OFF_DWELL_MAX_EXCLUSIVE_MS);
            default -> DEFAULT_DWELL_MS;
        };
        return state.fidelity() == AgentTownLifeFidelity.PRESENTATION
                ? duration
                : Math.min(BACKGROUND_DWELL_MAX_MS, duration * BACKGROUND_DWELL_MULTIPLIER);
    }

    private static void abandonDestination(AgentRuntimeEntry entry,
                                           Character agent,
                                           AgentTownLifeState state,
                                           long nowMs,
                                           PrimitiveCapabilityGateway gateway) {
        gateway.stop(entry);
        AgentFidgetService.clear(entry);
        AgentTownLifeActivityExtensionRuntime.cancel(entry, agent, state, nowMs);
        AgentTownLifeDestinationService.release(agent);
        AgentTownLifeProfile.PlatformPolicy platformPolicy = platformPolicy(state);
        if (platformPolicy == null) {
            state.memory().rememberFailure(state.destinationKey(), nowMs);
        } else {
            state.memory().rememberFailure(
                    platformPolicy.destinationKey(), nowMs, platformPolicy.failureCooldownMs());
        }
        AgentTownLifeEventPublisher.activity(
                entry, agent, state, AgentTownLifeActivityEvent.Phase.ABANDONED, nowMs);
        state.markActivityResult(AgentTownLifeActivityResult.ABANDONED);
        AgentTownLifeEncounterCoordinator.finish(entry, agent, false, nowMs);
        entry.capabilityStates().require(AgentTownLifeActivitySequenceState.STATE_KEY).clear();
        state.progressWatchdog().clear();
        state.transition(AgentTownLifeState.Stage.CHOOSE_ACTIVITY,
                nowMs + delay(
                        agent,
                        state,
                        ABANDON_RETRY_DELAY_MIN_MS,
                        ABANDON_RETRY_DELAY_MAX_EXCLUSIVE_MS));
    }

    private static boolean finishActivity(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeState state,
            PrimitiveCapabilityGateway gateway,
            long nowMs,
            AgentTownLifeActivityResult result,
            AgentTownLifeActivityEvent.Phase eventPhase) {
        state.markActivityResult(result);
        AgentTownLifeEventPublisher.activity(entry, agent, state, eventPhase, nowMs);
        AgentTownLifeEncounterCoordinator.finish(
                entry, agent, result == AgentTownLifeActivityResult.COMPLETED, nowMs);
        AgentTownLifeActivityExtensionRuntime.cancel(entry, agent, state, nowMs);
        entry.capabilityStates().require(AgentTownLifeActivitySequenceState.STATE_KEY).clear();
        AgentFidgetService.clear(entry);
        AgentTownLifeDestinationService.release(agent);
        gateway.stop(entry);
        if (result == AgentTownLifeActivityResult.COMPLETED) {
            rememberSuccessfulPlatformVisit(state, nowMs);
        }
        if (AgentTownLifeFidelityPolicy.rendersAmbientActions(state.fidelity())
                && agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
        }
        state.transition(AgentTownLifeState.Stage.COOLDOWN,
                nowMs + delay(
                        agent, state,
                        ACTIVITY_TRANSITION_MIN_MS,
                        ACTIVITY_TRANSITION_MAX_EXCLUSIVE_MS));
        return true;
    }

    private static AgentTownLifeActivityEvent.Phase eventPhase(
            AgentTownLifeActivityResult result) {
        return switch (result) {
            case COMPLETED -> AgentTownLifeActivityEvent.Phase.COMPLETED;
            case ABANDONED -> AgentTownLifeActivityEvent.Phase.ABANDONED;
            case TIMED_OUT -> AgentTownLifeActivityEvent.Phase.TIMED_OUT;
            case CANCELLED -> AgentTownLifeActivityEvent.Phase.CANCELLED;
            case FAILED -> AgentTownLifeActivityEvent.Phase.FAILED;
            case NONE, ACTIVE -> AgentTownLifeActivityEvent.Phase.PERFORMING;
        };
    }

    private static void requestDefaultGracefulExit(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeState state,
            String reason,
            long nowMs) {
        if (state == null || !state.enabled() || state.exitRequested()) {
            return;
        }
        AgentTownLifeSessionHandle handle = state.sessionHandle(agent.getId());
        if (handle == null) {
            terminateLocal(entry, agent, AgentTownLifeLifecycleEvent.Phase.FORCED, reason, nowMs);
            return;
        }
        AgentTownLifeLifecycleRuntime.requestExit(entry, agent,
                AgentTownLifeExitRequest.graceful(
                        handle, reason, nowMs, nowMs + DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS));
    }

    private static boolean readyForGracefulExit(
            AgentRuntimeEntry entry, AgentTownLifeState state) {
        if (state == null || !state.exitRequested()
                || AgentTownLifeEncounterCoordinator.active(entry)) {
            return false;
        }
        return switch (state.stage()) {
            case SETTLING, CHOOSE_ACTIVITY, RESERVE_DESTINATION, COOLDOWN, EXITING -> true;
            case MOVE_TO_ACTIVITY, DWELL -> state.activityResult().terminal();
            case DISABLED -> false;
        };
    }

    private static long navigationTimeoutMs(AgentTownLifeState state) {
        AgentTownLifeProfile.PlatformPolicy policy = platformPolicy(state);
        return policy == null ? Long.MAX_VALUE : policy.navigationTimeoutMs();
    }

    private static void rememberSuccessfulPlatformVisit(AgentTownLifeState state, long nowMs) {
        AgentTownLifeProfile.PlatformPolicy policy = platformPolicy(state);
        if (policy != null) {
            state.memory().rememberSuccess(
                    policy.destinationKey(), nowMs, policy.successCooldownMs());
        }
    }

    private static AgentTownLifeProfile.PlatformPolicy platformPolicy(
            AgentTownLifeState state) {
        Point target = state == null ? null : state.target();
        return target == null ? null : townProfile(state).platformPolicy(target).orElse(null);
    }

    private static int expressionFor(Character agent, AgentTownLifeState state) {
        return switch (state.activity()) {
            case REST, STROLL, BROWSE -> AgentEmote.HAPPY.getValue();
            case LINGER -> varied(agent, state, 2, 127) == 0
                    ? AgentEmote.GLARE.getValue() : AgentEmote.DISTURBED.getValue();
            case SOCIALIZE -> varied(agent, state, 3, 131) == 0
                    ? AgentEmote.ANNOYED.getValue() : AgentEmote.HAPPY.getValue();
            case SHOW_OFF -> varied(agent, state, 2, 137) == 0
                    ? AgentEmote.GLARE.getValue() : AgentEmote.ANGRY.getValue();
            default -> AgentEmote.HAPPY.getValue();
        };
    }

    private static Point peerPosition(AgentTownLifeState state, Character agent) {
        if (state.targetCharacterId() <= 0) {
            return null;
        }
        AgentRuntimeEntry peerEntry = AgentRuntimeRegistry.findByAgentCharacterId(state.targetCharacterId());
        Character peer = AgentRuntimeIdentityRuntime.bot(peerEntry);
        if (peer == null || peer.getMapId() != agent.getMapId()) {
            return null;
        }
        return new Point(peer.getPosition());
    }

    private static AgentTownLifeProfile townProfile(AgentTownLifeState state) {
        return AgentTownLifeProfileRepository.defaultRepository().require(state.townMapId());
    }

    private static long delay(Character agent,
                              AgentTownLifeState state,
                              int minimumInclusive,
                              int maximumExclusive) {
        return AgentTownLifeTimingPolicy.delay(
                agent, state, minimumInclusive, maximumExclusive);
    }

    private static int varied(Character agent, AgentTownLifeState state, int bound, int salt) {
        return AgentTownLifeTimingPolicy.varied(agent, state, bound, salt);
    }

    private static AgentTownLifeActivityEvent.Phase eventPhase(
            AgentTownLifeActivitySequenceState.Phase phase) {
        return switch (phase) {
            case ORIENT -> AgentTownLifeActivityEvent.Phase.ORIENTING;
            case OPENING -> AgentTownLifeActivityEvent.Phase.OPENING;
            case PERFORMING -> AgentTownLifeActivityEvent.Phase.PERFORMING;
            case REACTION -> AgentTownLifeActivityEvent.Phase.REACTING;
            case CLOSING -> AgentTownLifeActivityEvent.Phase.CLOSING;
            case COMPLETE -> AgentTownLifeActivityEvent.Phase.COMPLETED;
            case IDLE -> AgentTownLifeActivityEvent.Phase.SELECTED;
        };
    }
}
