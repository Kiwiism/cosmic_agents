package server.agents.capabilities.townlife;

import client.Character;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.awt.Point;

/** Coordinates bounded pair encounters without using visible chat for Agent-to-Agent control. */
final class AgentTownLifeEncounterCoordinator {
    private static final Object LOCK = new Object();
    private static final long ENCOUNTER_TIMEOUT_MS = 45_000L;
    private static final int PRESENTATION_DISTANCE_PX = 150;

    private AgentTownLifeEncounterCoordinator() {
    }

    static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates().find(AgentTownLifeEncounterState.STATE_KEY)
                .map(AgentTownLifeEncounterState::active).orElse(false);
    }

    static void begin(AgentRuntimeEntry initiatorEntry,
                      Character initiator,
                      AgentTownLifeState townState,
                      AgentTownLifeEncounterState.Type requestedType,
                      long nowMs) {
        if (initiatorEntry == null || initiator == null || townState.targetCharacterId() <= 0
                || (townState.activity() != AgentTownLifeState.Activity.SOCIAL
                && townState.activity() != AgentTownLifeState.Activity.WEAPON_FLOURISH)) {
            return;
        }
        AgentRuntimeEntry responderEntry = AgentRuntimeRegistry.findByAgentCharacterId(
                townState.targetCharacterId());
        Character responder = AgentRuntimeIdentityRuntime.bot(responderEntry);
        if (responder == null || responder.getMapId() != initiator.getMapId()
                || !AgentTownLifeRuntime.active(responderEntry)) {
            return;
        }
        synchronized (LOCK) {
            AgentTownLifeEncounterState initiatorEncounter = initiatorEntry.capabilityStates()
                    .require(AgentTownLifeEncounterState.STATE_KEY);
            AgentTownLifeEncounterState responderEncounter = responderEntry.capabilityStates()
                    .require(AgentTownLifeEncounterState.STATE_KEY);
            if (initiatorEncounter.active() || responderEncounter.active()) {
                return;
            }
            String id = "town-encounter:" + Math.min(initiator.getId(), responder.getId()) + ':'
                    + Math.max(initiator.getId(), responder.getId()) + ':' + townState.sequence();
            String correlation = townState.decisionCorrelationId().isBlank()
                    ? id : townState.decisionCorrelationId();
            AgentTownLifeEncounterState.Type type = requestedType != null ? requestedType
                    : townState.activity() == AgentTownLifeState.Activity.WEAPON_FLOURISH
                    ? AgentTownLifeEncounterState.Type.PLAYFUL_SPARRING
                    : AgentTownLifeEncounterState.Type.SOCIAL_CHAT;
            long expiresAt = nowMs + ENCOUNTER_TIMEOUT_MS;
            initiatorEncounter.begin(id, type, AgentTownLifeEncounterState.Role.INITIATOR,
                    AgentTownLifeEncounterState.Phase.APPROACHING, responder.getId(),
                    initiator.getId(), townState.venueId(), correlation, expiresAt);
            responderEncounter.begin(id, type, AgentTownLifeEncounterState.Role.RESPONDER,
                    AgentTownLifeEncounterState.Phase.INVITED, initiator.getId(),
                    initiator.getId(), townState.venueId(), correlation, expiresAt);
            AgentTownLifeEventPublisher.encounter(
                    initiatorEntry, initiator, initiatorEncounter.snapshot(), nowMs);
            AgentTownLifeEventPublisher.encounter(
                    responderEntry, responder, responderEncounter.snapshot(), nowMs);
        }
    }

    static boolean activate(AgentRuntimeEntry initiatorEntry, Character initiator, long nowMs) {
        AgentTownLifeEncounterState.Snapshot snapshot = snapshot(initiatorEntry);
        if (snapshot == null) {
            return true;
        }
        AgentRuntimeEntry peerEntry = AgentRuntimeRegistry.findByAgentCharacterId(snapshot.peerAgentId());
        Character peer = AgentRuntimeIdentityRuntime.bot(peerEntry);
        if (peer == null || initiator == null || peer.getMapId() != initiator.getMapId()
                || peer.getPosition().distanceSq(initiator.getPosition())
                > (long) PRESENTATION_DISTANCE_PX * PRESENTATION_DISTANCE_PX) {
            finish(initiatorEntry, initiator, false, nowMs);
            return false;
        }
        transitionPair(initiatorEntry, initiator, AgentTownLifeEncounterState.Phase.ACTIVE,
                initiator.getId(), nowMs);
        return true;
    }

    static void requestReaction(AgentRuntimeEntry initiatorEntry, Character initiator, long nowMs) {
        AgentTownLifeEncounterState.Snapshot snapshot = snapshot(initiatorEntry);
        if (snapshot == null || snapshot.role() != AgentTownLifeEncounterState.Role.INITIATOR) {
            return;
        }
        transitionPair(initiatorEntry, initiator, AgentTownLifeEncounterState.Phase.REACTING,
                snapshot.peerAgentId(), nowMs);
    }

    static void beginClosing(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentTownLifeEncounterState.Snapshot snapshot = snapshot(entry);
        if (snapshot != null && snapshot.role() == AgentTownLifeEncounterState.Role.INITIATOR) {
            transitionPair(entry, agent, AgentTownLifeEncounterState.Phase.CLOSING,
                    snapshot.turnOwnerAgentId(), nowMs);
        }
    }

    static void finish(AgentRuntimeEntry entry, Character agent, boolean completed, long nowMs) {
        if (entry == null) {
            return;
        }
        synchronized (LOCK) {
            AgentTownLifeEncounterState own = entry.capabilityStates()
                    .require(AgentTownLifeEncounterState.STATE_KEY);
            AgentTownLifeEncounterState.Snapshot ownSnapshot = own.snapshot();
            if (!ownSnapshot.active()) {
                return;
            }
            if (completed && ownSnapshot.role() == AgentTownLifeEncounterState.Role.RESPONDER) {
                return;
            }
            AgentRuntimeEntry peerEntry = AgentRuntimeRegistry.findByAgentCharacterId(
                    ownSnapshot.peerAgentId());
            Character peer = AgentRuntimeIdentityRuntime.bot(peerEntry);
            AgentTownLifeEncounterState.Phase terminal = completed
                    ? AgentTownLifeEncounterState.Phase.COMPLETED
                    : AgentTownLifeEncounterState.Phase.CANCELLED;
            own.transition(terminal, ownSnapshot.turnOwnerAgentId());
            AgentTownLifeEventPublisher.encounter(entry, agent, own.snapshot(), nowMs);
            if (peerEntry != null) {
                AgentTownLifeEncounterState peerState = peerEntry.capabilityStates()
                        .require(AgentTownLifeEncounterState.STATE_KEY);
                if (peerState.active() && ownSnapshot.encounterId().equals(peerState.snapshot().encounterId())) {
                    peerState.transition(terminal, ownSnapshot.turnOwnerAgentId());
                    AgentTownLifeEventPublisher.encounter(peerEntry, peer, peerState.snapshot(), nowMs);
                    peerState.clear();
                }
            }
            own.clear();
        }
    }

    static void tickPassive(AgentRuntimeEntry entry,
                            Character agent,
                            AgentTownLifeState townState,
                            PrimitiveCapabilityGateway gateway,
                            long nowMs) {
        AgentTownLifeEncounterState encounter = entry.capabilityStates()
                .require(AgentTownLifeEncounterState.STATE_KEY);
        if (encounter.expired(nowMs)) {
            finish(entry, agent, false, nowMs);
            return;
        }
        AgentTownLifeEncounterState.Snapshot snapshot = encounter.snapshot();
        if (!snapshot.active() || snapshot.role() != AgentTownLifeEncounterState.Role.RESPONDER
                || (snapshot.phase() != AgentTownLifeEncounterState.Phase.ACTIVE
                && snapshot.phase() != AgentTownLifeEncounterState.Phase.REACTING)
                || townState.stage() == AgentTownLifeState.Stage.MOVE_TO_ACTIVITY
                || townState.stage() == AgentTownLifeState.Stage.VISIT_SHOP
                || townState.stage() == AgentTownLifeState.Stage.RETURN_FROM_SHOP) {
            return;
        }
        AgentRuntimeEntry peerEntry = AgentRuntimeRegistry.findByAgentCharacterId(snapshot.peerAgentId());
        Character peer = AgentRuntimeIdentityRuntime.bot(peerEntry);
        if (peer == null || peer.getMapId() != agent.getMapId()
                || peer.getPosition().distanceSq(agent.getPosition())
                > (long) PRESENTATION_DISTANCE_PX * PRESENTATION_DISTANCE_PX) {
            return;
        }
        Point peerPosition = new Point(peer.getPosition());
        gateway.facePosition(agent, peerPosition);
        if (snapshot.phase() == AgentTownLifeEncounterState.Phase.REACTING
                && !encounter.reactionShown()) {
            int expression = snapshot.type() == AgentTownLifeEncounterState.Type.PLAYFUL_SPARRING
                    ? AgentEmote.ANNOYED.getValue() : AgentEmote.HAPPY.getValue();
            agent.changeFaceExpression(expression);
            encounter.markReactionShown();
        }
    }

    private static void transitionPair(AgentRuntimeEntry entry,
                                       Character agent,
                                       AgentTownLifeEncounterState.Phase phase,
                                       int turnOwnerAgentId,
                                       long nowMs) {
        if (entry == null || agent == null || turnOwnerAgentId <= 0) {
            return;
        }
        synchronized (LOCK) {
            AgentTownLifeEncounterState own = entry.capabilityStates()
                    .require(AgentTownLifeEncounterState.STATE_KEY);
            AgentTownLifeEncounterState.Snapshot snapshot = own.snapshot();
            if (!snapshot.active()) {
                return;
            }
            own.transition(phase, turnOwnerAgentId);
            AgentTownLifeEventPublisher.encounter(entry, agent, own.snapshot(), nowMs);
            AgentRuntimeEntry peerEntry = AgentRuntimeRegistry.findByAgentCharacterId(snapshot.peerAgentId());
            Character peer = AgentRuntimeIdentityRuntime.bot(peerEntry);
            if (peerEntry != null) {
                AgentTownLifeEncounterState peerState = peerEntry.capabilityStates()
                        .require(AgentTownLifeEncounterState.STATE_KEY);
                if (peerState.active() && snapshot.encounterId().equals(peerState.snapshot().encounterId())) {
                    peerState.transition(phase, turnOwnerAgentId);
                    AgentTownLifeEventPublisher.encounter(peerEntry, peer, peerState.snapshot(), nowMs);
                }
            }
        }
    }

    private static AgentTownLifeEncounterState.Snapshot snapshot(AgentRuntimeEntry entry) {
        if (entry == null) {
            return null;
        }
        AgentTownLifeEncounterState.Snapshot snapshot = entry.capabilityStates()
                .require(AgentTownLifeEncounterState.STATE_KEY).snapshot();
        return snapshot.active() ? snapshot : null;
    }
}
