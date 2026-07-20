package server.agents.capabilities.presentation;

import client.Character;
import server.agents.capabilities.movement.AgentJumpActionService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Executes pending personality intents from the serialized Agent live-mode tick. */
public final class AgentPersonalityPresentationPolicy {
    private AgentPersonalityPresentationPolicy() {
    }

    public static boolean tick(AgentRuntimeEntry entry,
                               Character agent,
                               long nowMs,
                               int realPlayerObservers,
                               Point destination,
                               PrimitiveCapabilityGateway gateway) {
        if (entry == null || agent == null || gateway == null) {
            return false;
        }
        AgentPersonalityState personality = entry.capabilityStates().require(
                AgentPersonalityState.STATE_KEY);
        if (!personality.presentationEnabled()) {
            return false;
        }
        AgentPresentationState state = entry.capabilityStates().require(
                AgentPresentationState.STATE_KEY);
        boolean observerBecamePresent = state.observerBecamePresent(realPlayerObservers);
        Point presentationTarget = destination == null ? gateway.position(agent) : destination;
        if (AgentFidgetStateRuntime.trigger(entry)
                == AgentFidgetTrigger.PERSONALITY_PRESENTATION) {
            if (realPlayerObservers <= 0) {
                AgentFidgetService.clearPersonalityPresentation(entry);
                return false;
            }
            return AgentFidgetService.tryHandlePersonalityPresentationTick(
                    entry, presentationTarget, nowMs);
        }

        if (observerBecamePresent) {
            AgentPersonalityPresentationRuntime.schedule(
                    entry, AgentPresentationTrigger.OBSERVER_PRESENT, nowMs);
        }
        AgentPresentationDecision decision = state.takeDue(nowMs);
        if (decision == null) {
            return false;
        }
        if (realPlayerObservers <= 0) {
            AgentPresentationTelemetry.recordObserverSuppressed();
            return false;
        }
        AgentPresentationSafetyGate.Result safety = AgentPresentationSafetyGate.evaluate(
                entry, agent, decision, destination, gateway);
        if (safety != AgentPresentationSafetyGate.Result.SAFE) {
            AgentPresentationTelemetry.recordUnsafeBlocked();
            return false;
        }

        gateway.stop(entry);
        AgentPresentationTelemetry.recordExecuted(decision.intent());
        return execute(entry, agent, presentationTarget, nowMs, decision);
    }

    private static boolean execute(AgentRuntimeEntry entry,
                                   Character agent,
                                   Point destination,
                                   long nowMs,
                                   AgentPresentationDecision decision) {
        switch (decision.intent()) {
            case TURN -> {
                AgentMovementStateRuntime.setFacingDirection(entry,
                        -AgentMovementStateRuntime.facingDirectionSign(entry));
                AgentMovementPoseService.idleOnGround(entry, agent);
                AgentMovementBroadcastService.broadcastMovement(entry);
                return true;
            }
            case HOP -> {
                AgentJumpActionService.initiateJump(entry, agent, 0);
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
                AgentFidgetService.startFidget(entry, mode, nowMs,
                        decision.durationMs(), AgentFidgetTrigger.PERSONALITY_PRESENTATION);
                return AgentFidgetService.tryHandlePersonalityPresentationTick(
                        entry, destination, nowMs);
            }
        }
    }

    public static void clear(AgentRuntimeEntry entry) {
        AgentFidgetService.clearPersonalityPresentation(entry);
    }
}
