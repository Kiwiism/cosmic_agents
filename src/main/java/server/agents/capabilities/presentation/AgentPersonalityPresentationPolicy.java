package server.agents.capabilities.presentation;

import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.personality.AgentPersonalityState;

import java.awt.Point;

/** Executes pending personality intents only from the serialized Agent capability tick. */
public final class AgentPersonalityPresentationPolicy {
    private AgentPersonalityPresentationPolicy() {
    }

    public static boolean tick(AgentCapabilityContext context,
                               Point destination,
                               int tolerancePx,
                               boolean precise,
                               PrimitiveCapabilityGateway gateway) {
        if (context == null || destination == null || gateway == null) {
            return false;
        }
        AgentPersonalityState personality = context.entry().capabilityStates().require(
                AgentPersonalityState.STATE_KEY);
        if (!personality.presentationEnabled()) {
            return false;
        }
        if (AgentFidgetStateRuntime.trigger(context.entry()) == AgentFidgetTrigger.PROFILE_NAVIGATION) {
            return AgentFidgetService.tryHandleProfileNavigationTick(
                    context.entry(), destination, context.nowMs());
        }

        AgentPresentationState state = context.entry().capabilityStates().require(
                AgentPresentationState.STATE_KEY);
        int observers = context.view().perception().realPlayerObservers();
        if (state.observerBecamePresent(observers)) {
            AgentPersonalityPresentationRuntime.schedule(
                    context.entry(), AgentPresentationTrigger.OBSERVER_PRESENT, context.nowMs());
        }
        AgentPresentationDecision decision = state.takeDue(context.nowMs());
        if (decision == null) {
            return false;
        }
        if (observers <= 0) {
            AgentPresentationTelemetry.recordObserverSuppressed();
            return false;
        }
        AgentPresentationSafetyGate.Result safety = AgentPresentationSafetyGate.evaluate(
                context, decision, destination, tolerancePx, precise, gateway);
        if (safety != AgentPresentationSafetyGate.Result.SAFE) {
            AgentPresentationTelemetry.recordUnsafeBlocked();
            return false;
        }

        gateway.stop(context.entry());
        AgentPresentationTelemetry.recordExecuted(decision.intent());
        return execute(context, destination, decision);
    }

    private static boolean execute(AgentCapabilityContext context,
                                   Point destination,
                                   AgentPresentationDecision decision) {
        switch (decision.intent()) {
            case TURN -> {
                AgentMovementStateRuntime.setFacingDirection(context.entry(),
                        -AgentMovementStateRuntime.facingDirectionSign(context.entry()));
                AgentMovementPoseService.idleOnGround(context.entry(), context.agent());
                AgentMovementBroadcastService.broadcastMovement(context.entry());
                return true;
            }
            case HOP -> {
                AgentJumpActionService.initiateJump(context.entry(), context.agent(), 0);
                return true;
            }
            default -> {
                AgentFidgetMode mode = switch (decision.intent()) {
                    case PRONE -> AgentFidgetMode.PRONE;
                    case PRONE_TAP -> AgentFidgetMode.SPAM_PRONE;
                    case SHUFFLE, COMBAT_REPOSITION -> AgentFidgetMode.SPAM_SIDEWAYS;
                    case WAIT, LINGER, COMBAT_PAUSE -> AgentFidgetMode.WAIT;
                    default -> AgentFidgetMode.WAIT;
                };
                AgentFidgetService.startFidget(context.entry(), mode, context.nowMs(),
                        decision.durationMs(), AgentFidgetTrigger.PROFILE_NAVIGATION);
                return AgentFidgetService.tryHandleProfileNavigationTick(
                        context.entry(), destination, context.nowMs());
            }
        }
    }

    public static void clear(AgentCapabilityContext context) {
        if (context != null) {
            AgentFidgetService.clearProfileNavigation(context.entry());
        }
    }
}
