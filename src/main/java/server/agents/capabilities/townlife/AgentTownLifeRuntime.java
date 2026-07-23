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
import server.agents.capabilities.navigation.AgentRouteOutcome;
import server.agents.capabilities.navigation.AgentRouteStatus;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

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
    private static final int SHOP_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.SHOP_DWELL_MIN_MS");
    private static final int SHOP_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.SHOP_DWELL_MAX_EXCLUSIVE_MS");
    private static final int RETURN_SETTLE_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.RETURN_SETTLE_MIN_MS");
    private static final int RETURN_SETTLE_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.RETURN_SETTLE_MAX_EXCLUSIVE_MS");
    private static final int MIN_FIDGET_DURATION_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.MIN_FIDGET_DURATION_MS");
    private static final int REST_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.REST_DWELL_MIN_MS");
    private static final int REST_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.REST_DWELL_MAX_EXCLUSIVE_MS");
    private static final int SOCIAL_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.SOCIAL_DWELL_MIN_MS");
    private static final int SOCIAL_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.SOCIAL_DWELL_MAX_EXCLUSIVE_MS");
    private static final int NPC_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.NPC_DWELL_MIN_MS");
    private static final int NPC_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.NPC_DWELL_MAX_EXCLUSIVE_MS");
    private static final int ROAM_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.ROAM_DWELL_MIN_MS");
    private static final int ROAM_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.ROAM_DWELL_MAX_EXCLUSIVE_MS");
    private static final int FLOURISH_DWELL_MIN_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.FLOURISH_DWELL_MIN_MS");
    private static final int FLOURISH_DWELL_MAX_EXCLUSIVE_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.FLOURISH_DWELL_MAX_EXCLUSIVE_MS");
    private static final int DEFAULT_DWELL_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.DEFAULT_DWELL_MS");
    private static final int BACKGROUND_DWELL_MAX_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.BACKGROUND_DWELL_MAX_MS");
    private static final int BACKGROUND_DWELL_MULTIPLIER = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeRuntime.BACKGROUND_DWELL_MULTIPLIER");

    private AgentTownLifeRuntime() {
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates()
                .find(AgentTownLifeState.STATE_KEY)
                .map(AgentTownLifeState::enabled)
                .orElse(false);
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
        if (entry == null || request == null) {
            return;
        }
        entry.capabilityStates().require(AgentTownLifeState.STATE_KEY)
                .start(nowMs, identitySeed, request);
        AgentForegroundPauseRuntime.pause(entry, PLAN_PAUSE_REASON, nowMs);
        AgentTravelVariationRuntime.configure(entry,
                new AgentTravelVariationSettings(
                        Integer.toUnsignedLong(identitySeed), true, 1.30d,
                        false, 0.0d, 3_000L, 0L));
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
        if (state.freeTimeExpired(nowMs)
                && !AgentTownLifeEncounterCoordinator.active(entry)) {
            stop(entry, agent);
            return true;
        }
        if (AgentTownLifeFidelityPolicy.rendersAmbientActions(fidelity)) {
            AgentTownLifeEncounterCoordinator.tickPassive(entry, agent, state, gateway, nowMs);
        }
        AgentTownLifeArrivalExtension arrival =
                AgentTownLifeArrivalExtensionRepository.forTown(state.townMapId());
        if (state.stage() == AgentTownLifeState.Stage.TRAVEL_TO_TOWN) {
            return arrival.tickTravel(entry, agent, state, nowMs, gateway);
        }
        if (agent.getMapId() != state.townMapId()
                && state.stage() != AgentTownLifeState.Stage.VISIT_SHOP
                && state.stage() != AgentTownLifeState.Stage.RETURN_FROM_SHOP
                && !(state.stage() == AgentTownLifeState.Stage.DWELL
                && state.activity() == AgentTownLifeState.Activity.SHOP_VISIT)) {
            AgentTownLifeDestinationService.release(agent);
            AgentFidgetService.clear(entry);
            state.transition(AgentTownLifeState.Stage.RETURN_FROM_SHOP, nowMs);
        }
        return switch (state.stage()) {
            case DISABLED -> false;
            case TRAVEL_TO_TOWN -> true;
            case COMPLETE_ARRIVAL -> arrival.tickArrival(entry, agent, state, nowMs, gateway);
            case SETTLING -> tickSettling(entry, agent, state, nowMs, gateway);
            case CHOOSE_ACTIVITY -> chooseActivity(entry, agent, state, nowMs, gateway);
            case MOVE_TO_ACTIVITY -> moveToActivity(entry, agent, state, nowMs, gateway);
            case DWELL -> tickDwell(entry, agent, state, nowMs, gateway);
            case VISIT_SHOP -> visitShop(entry, agent, state, nowMs, gateway);
            case RETURN_FROM_SHOP -> returnFromShop(entry, agent, state, nowMs, gateway);
        };
    }

    public static void stop(AgentRuntimeEntry entry, Character agent) {
        if (entry == null) {
            return;
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        AgentTownLifeEncounterCoordinator.finish(entry, agent, false, System.currentTimeMillis());
        entry.capabilityStates().require(AgentTownLifeActivitySequenceState.STATE_KEY).clear();
        state.stop();
        AgentForegroundPauseRuntime.resume(entry, PLAN_PAUSE_REASON, System.currentTimeMillis());
        AgentTownLifeDestinationService.release(agent);
        AgentFidgetService.clear(entry);
        if (agent != null && agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
        }
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
    }

    private static boolean tickSettling(AgentRuntimeEntry entry,
                                        Character agent,
                                        AgentTownLifeState state,
                                        long nowMs,
                                        PrimitiveCapabilityGateway gateway) {
        gateway.stop(entry);
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

    private static boolean chooseActivity(AgentRuntimeEntry entry,
                                          Character agent,
                                          AgentTownLifeState state,
                                          long nowMs,
                                          PrimitiveCapabilityGateway gateway) {
        if (nowMs < state.nextActionAtMs()) {
            return true;
        }
        if (agent.getChair() >= 0) {
            AgentChairService.stand(entry, agent);
        }
        AgentFidgetService.clear(entry);
        AgentTownLifeRolePolicy.resolve(entry, agent, state, nowMs);
        AgentTownLifeDecision decision = AgentTownLifeControllerRuntime.choose(entry, agent, state, nowMs);
        if (!AgentTownLifeFidelityPolicy.createsEncounters(state.fidelity())
                && (decision.activity() == AgentTownLifeState.Activity.SOCIAL
                || decision.activity() == AgentTownLifeState.Activity.WEAPON_FLOURISH)) {
            decision = AgentTownLifeDecision.deterministic(AgentTownLifeState.Activity.ROAM);
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
                destination.destinationMapId(), destination.key(), destination.venueId(),
                decision.source(), decision.correlationId(), nowMs);
        state.memory().remember(destination.activity(), destination.key(), nowMs);
        entry.capabilityStates().require(AgentTownLifeActivitySequenceState.STATE_KEY).clear();
        AgentTownLifeEventPublisher.activity(
                entry, agent, state, AgentTownLifeActivityEvent.Phase.SELECTED, nowMs);
        if ((destination.activity() == AgentTownLifeState.Activity.SOCIAL
                || destination.activity() == AgentTownLifeState.Activity.WEAPON_FLOURISH)
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

    private static boolean moveToActivity(AgentRuntimeEntry entry,
                                          Character agent,
                                          AgentTownLifeState state,
                                          long nowMs,
                                          PrimitiveCapabilityGateway gateway) {
        Point target = state.target();
        if (target == null) {
            AgentTownLifeDestinationService.release(agent);
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
                    state.progressWatchdog().observe(agent.getPosition(), nowMs);
            if (progress != AgentTownLifeProgressWatchdog.Result.PROGRESSING) {
                AgentTownLifeMetrics.navigationAbandon();
                abandonDestination(entry, agent, state, nowMs, gateway);
                return true;
            }
            gateway.navigate(entry, target, false);
            return false;
        }
        gateway.stop(entry);
        if (state.activity() == AgentTownLifeState.Activity.ROAM) {
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

    private static boolean tickDwell(AgentRuntimeEntry entry,
                                     Character agent,
                                     AgentTownLifeState state,
                                     long nowMs,
                                     PrimitiveCapabilityGateway gateway) {
        AgentTownLifeEncounterState.Snapshot encounter = entry.capabilityStates()
                .require(AgentTownLifeEncounterState.STATE_KEY).snapshot();
        if (nowMs >= state.nextActionAtMs() && encounter.active()
                && encounter.role() == AgentTownLifeEncounterState.Role.RESPONDER) {
            state.beginDwell(nowMs + ACTIVITY_TRANSITION_MIN_MS);
            return true;
        }
        if (nowMs >= state.nextActionAtMs()) {
            AgentTownLifeEventPublisher.activity(
                    entry, agent, state, AgentTownLifeActivityEvent.Phase.COMPLETED, nowMs);
            AgentTownLifeEncounterCoordinator.finish(entry, agent, true, nowMs);
            entry.capabilityStates().require(AgentTownLifeActivitySequenceState.STATE_KEY).clear();
            AgentFidgetService.clear(entry);
            AgentTownLifeDestinationService.release(agent);
            if (agent.getChair() >= 0) {
                AgentChairService.stand(entry, agent);
            }
            if (state.activity() == AgentTownLifeState.Activity.SHOP_VISIT
                    && agent.getMapId() != state.townMapId()) {
                state.transition(AgentTownLifeState.Stage.RETURN_FROM_SHOP, nowMs);
            } else {
                state.transition(AgentTownLifeState.Stage.CHOOSE_ACTIVITY,
                        nowMs + delay(
                                agent,
                                state,
                                ACTIVITY_TRANSITION_MIN_MS,
                                ACTIVITY_TRANSITION_MAX_EXCLUSIVE_MS));
            }
            return true;
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
                && state.activity() == AgentTownLifeState.Activity.WEAPON_FLOURISH
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

    private static boolean visitShop(AgentRuntimeEntry entry,
                                     Character agent,
                                     AgentTownLifeState state,
                                     long nowMs,
                                     PrimitiveCapabilityGateway gateway) {
        AgentRouteOutcome outcome = gateway.travelTo(
                entry, agent, state.destinationMapId(), nowMs);
        if (outcome.status() == AgentRouteStatus.ARRIVED) {
            gateway.stop(entry);
            state.beginDwell(nowMs + delay(
                    agent, state, SHOP_DWELL_MIN_MS, SHOP_DWELL_MAX_EXCLUSIVE_MS));
            entry.capabilityStates().require(AgentTownLifeActivitySequenceState.STATE_KEY)
                    .start(nowMs, state.nextActionAtMs());
            AgentTownLifeEventPublisher.activity(
                    entry, agent, state, AgentTownLifeActivityEvent.Phase.ORIENTING, nowMs);
            return true;
        }
        return outcome.status() != AgentRouteStatus.MOVING;
    }

    private static boolean returnFromShop(AgentRuntimeEntry entry,
                                          Character agent,
                                          AgentTownLifeState state,
                                          long nowMs,
                                          PrimitiveCapabilityGateway gateway) {
        AgentRouteOutcome outcome = gateway.travelTo(
                entry, agent, state.townMapId(), nowMs);
        if (outcome.status() == AgentRouteStatus.ARRIVED) {
            gateway.stop(entry);
            state.transition(AgentTownLifeState.Stage.SETTLING,
                    nowMs + delay(
                            agent,
                            state,
                            RETURN_SETTLE_MIN_MS,
                            RETURN_SETTLE_MAX_EXCLUSIVE_MS));
            return true;
        }
        return outcome.status() != AgentRouteStatus.MOVING;
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
        long duration = switch (state.activity()) {
            case REST -> delay(agent, state, REST_DWELL_MIN_MS, REST_DWELL_MAX_EXCLUSIVE_MS);
            case SOCIAL -> delay(
                    agent, state, SOCIAL_DWELL_MIN_MS, SOCIAL_DWELL_MAX_EXCLUSIVE_MS);
            case NPC_PAUSE -> delay(agent, state, NPC_DWELL_MIN_MS, NPC_DWELL_MAX_EXCLUSIVE_MS);
            case ROAM -> delay(agent, state, ROAM_DWELL_MIN_MS, ROAM_DWELL_MAX_EXCLUSIVE_MS);
            case WEAPON_FLOURISH -> delay(
                    agent, state, FLOURISH_DWELL_MIN_MS, FLOURISH_DWELL_MAX_EXCLUSIVE_MS);
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
        AgentTownLifeDestinationService.release(agent);
        state.memory().rememberFailure(state.destinationKey(), nowMs);
        AgentTownLifeEventPublisher.activity(
                entry, agent, state, AgentTownLifeActivityEvent.Phase.ABANDONED, nowMs);
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

    private static int expressionFor(Character agent, AgentTownLifeState state) {
        return switch (state.activity()) {
            case REST, ROAM, SHOP_VISIT -> AgentEmote.HAPPY.getValue();
            case NPC_PAUSE -> varied(agent, state, 2, 127) == 0
                    ? AgentEmote.GLARE.getValue() : AgentEmote.DISTURBED.getValue();
            case SOCIAL -> varied(agent, state, 3, 131) == 0
                    ? AgentEmote.ANNOYED.getValue() : AgentEmote.HAPPY.getValue();
            case WEAPON_FLOURISH -> varied(agent, state, 2, 137) == 0
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
