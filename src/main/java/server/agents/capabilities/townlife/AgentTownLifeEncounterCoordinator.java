package server.agents.capabilities.townlife;

import client.Character;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Coordinates accepted, capacity-bounded TownLife encounters. */
final class AgentTownLifeEncounterCoordinator {
    enum Activation {
        NONE,
        WAITING,
        ACTIVE,
        CANCELLED
    }

    private static final Object LOCK = new Object();
    private static final long ENCOUNTER_TIMEOUT_MS = config.AgentTuning.longValue("server.agents.capabilities.townlife.AgentTownLifeEncounterCoordinator.ENCOUNTER_TIMEOUT_MS");
    private static final int PRESENTATION_DISTANCE_PX = config.AgentTuning.intValue("server.agents.capabilities.townlife.AgentTownLifeEncounterCoordinator.PRESENTATION_DISTANCE_PX");

    private AgentTownLifeEncounterCoordinator() {
    }

    static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates().find(AgentTownLifeEncounterState.STATE_KEY)
                .map(AgentTownLifeEncounterState::active).orElse(false);
    }

    static boolean begin(AgentRuntimeEntry initiatorEntry,
                         Character initiator,
                         AgentTownLifeState townState,
                         AgentTownLifeEncounterState.Type requestedType,
                         PrimitiveCapabilityGateway gateway,
                         long nowMs) {
        if (initiatorEntry == null || initiator == null || gateway == null
                || townState.targetCharacterId() <= 0 || townState.venueId().isBlank()
                || (townState.activity() != AgentTownLifeState.Activity.SOCIAL
                && townState.activity() != AgentTownLifeState.Activity.WEAPON_FLOURISH)) {
            return false;
        }
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(townState.townMapId());
        AgentTownLifeProfile.Venue venue = profile.venue(townState.venueId()).orElse(null);
        AgentTownLifeEncounterState.Type type = requestedType != null ? requestedType
                : townState.activity() == AgentTownLifeState.Activity.WEAPON_FLOURISH
                ? AgentTownLifeEncounterState.Type.PLAYFUL_SPARRING
                : AgentTownLifeEncounterState.Type.SOCIAL_CHAT;
        if (venue == null || !venue.supports(townState.activity())) {
            return false;
        }
        synchronized (LOCK) {
            if (active(initiatorEntry)) {
                return false;
            }
            List<Character> participants = acceptedParticipants(
                    initiator, townState, type, venue.capacity(), nowMs);
            if (participants.size() < 2) {
                return false;
            }
            Map<Integer, Point> spots = AgentTownLifeVenueReservationService.reserveGroup(
                    participants, venue, townState.sequence());
            if (spots.size() != participants.size()) {
                return false;
            }
            AgentTownLifeMetrics.encounterGroup(participants.size());
            String id = "town-encounter:" + initiator.getId() + ':' + townState.sequence();
            String correlation = townState.decisionCorrelationId().isBlank()
                    ? id : townState.decisionCorrelationId();
            long expiresAt = nowMs + ENCOUNTER_TIMEOUT_MS;
            List<Integer> participantIds = participants.stream().map(Character::getId).toList();
            for (int index = 0; index < participants.size(); index++) {
                Character participant = participants.get(index);
                AgentRuntimeEntry participantEntry = AgentRuntimeRegistry.findByCharacterInstance(participant);
                AgentTownLifeState participantTownState = participantEntry.capabilityStates()
                        .require(AgentTownLifeState.STATE_KEY);
                int peerId = index == 0 ? participants.get(1).getId() : initiator.getId();
                String destinationKey = "encounter:" + id + ':' + participant.getId();
                if (index == 0) {
                    participantTownState.retarget(
                            spots.get(participant.getId()), peerId, destinationKey, venue.id());
                } else {
                    gateway.stop(participantEntry);
                    AgentFidgetService.clear(participantEntry);
                    if (participant.getChair() >= 0) {
                        AgentChairService.stand(participantEntry, participant);
                    }
                    participantTownState.select(townState.activity(), spots.get(participant.getId()),
                            peerId, 0, destinationKey, venue.id(),
                            "encounter:" + townState.decisionSource(), correlation, nowMs);
                    participantTownState.memory().remember(
                            townState.activity(), destinationKey, nowMs);
                    participantEntry.capabilityStates()
                            .require(AgentTownLifeActivitySequenceState.STATE_KEY).clear();
                    AgentTownLifeEventPublisher.activity(
                            participantEntry, participant, participantTownState,
                            AgentTownLifeActivityEvent.Phase.SELECTED, nowMs);
                }
                AgentTownLifeEncounterState encounter = participantEntry.capabilityStates()
                        .require(AgentTownLifeEncounterState.STATE_KEY);
                encounter.begin(id, type,
                        index == 0 ? AgentTownLifeEncounterState.Role.INITIATOR
                                : AgentTownLifeEncounterState.Role.RESPONDER,
                        index == 0 ? AgentTownLifeEncounterState.Phase.APPROACHING
                                : AgentTownLifeEncounterState.Phase.INVITED,
                        peerId, initiator.getId(), participantIds, venue.id(),
                        correlation, expiresAt);
                AgentTownLifeEventPublisher.encounter(
                        participantEntry, participant, encounter.snapshot(), nowMs);
                if (index > 0) {
                    encounter.transition(
                            AgentTownLifeEncounterState.Phase.ACCEPTED, initiator.getId());
                    AgentTownLifeEventPublisher.encounter(
                            participantEntry, participant, encounter.snapshot(), nowMs);
                }
            }
            return true;
        }
    }

    static Activation activate(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentTownLifeEncounterState.Snapshot snapshot = snapshot(entry);
        if (snapshot == null) {
            return Activation.NONE;
        }
        if (nowMs >= snapshot.expiresAtMs()) {
            finish(entry, agent, false, nowMs);
            return Activation.CANCELLED;
        }
        for (int participantId : snapshot.participantAgentIds()) {
            AgentRuntimeEntry participantEntry =
                    AgentRuntimeRegistry.findByAgentCharacterId(participantId);
            Character participant = AgentRuntimeIdentityRuntime.bot(participantEntry);
            AgentTownLifeState participantTownState = participantEntry == null ? null
                    : participantEntry.capabilityStates()
                    .find(AgentTownLifeState.STATE_KEY).orElse(null);
            Point target = participantTownState == null ? null : participantTownState.target();
            if (participant == null || target == null || agent == null
                    || participant.getMapId() != agent.getMapId()) {
                finish(entry, agent, false, nowMs);
                return Activation.CANCELLED;
            }
            if (participant.getPosition().distanceSq(target)
                    > (long) PRESENTATION_DISTANCE_PX * PRESENTATION_DISTANCE_PX) {
                transitionParticipant(participantEntry, participant,
                        AgentTownLifeEncounterState.Phase.APPROACHING,
                        snapshot.turnOwnerAgentId(), nowMs);
                return Activation.WAITING;
            }
        }
        transitionGroup(entry, AgentTownLifeEncounterState.Phase.ACTIVE,
                snapshot.turnOwnerAgentId(), nowMs);
        return Activation.ACTIVE;
    }

    static void requestReaction(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentTownLifeEncounterState.Snapshot snapshot = snapshot(entry);
        if (snapshot != null && snapshot.role() == AgentTownLifeEncounterState.Role.INITIATOR) {
            transitionGroup(entry, AgentTownLifeEncounterState.Phase.REACTING,
                    snapshot.peerAgentId(), nowMs);
        }
    }

    static void beginClosing(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentTownLifeEncounterState.Snapshot snapshot = snapshot(entry);
        if (snapshot != null && snapshot.role() == AgentTownLifeEncounterState.Role.INITIATOR) {
            transitionGroup(entry, AgentTownLifeEncounterState.Phase.CLOSING,
                    snapshot.turnOwnerAgentId(), nowMs);
        }
    }

    static void finish(AgentRuntimeEntry entry, Character agent, boolean completed, long nowMs) {
        AgentTownLifeEncounterState.Snapshot snapshot = snapshot(entry);
        if (snapshot == null
                || (completed && snapshot.role() == AgentTownLifeEncounterState.Role.RESPONDER)) {
            return;
        }
        synchronized (LOCK) {
            AgentTownLifeEncounterState.Phase terminal = completed
                    ? AgentTownLifeEncounterState.Phase.COMPLETED
                    : AgentTownLifeEncounterState.Phase.CANCELLED;
            for (int participantId : snapshot.participantAgentIds()) {
                AgentRuntimeEntry participantEntry =
                        AgentRuntimeRegistry.findByAgentCharacterId(participantId);
                Character participant = AgentRuntimeIdentityRuntime.bot(participantEntry);
                if (participantEntry == null) {
                    continue;
                }
                AgentTownLifeEncounterState participantState = participantEntry.capabilityStates()
                        .require(AgentTownLifeEncounterState.STATE_KEY);
                AgentTownLifeEncounterState.Snapshot participantSnapshot = participantState.snapshot();
                if (participantSnapshot.active()
                        && snapshot.encounterId().equals(participantSnapshot.encounterId())) {
                    participantState.transition(terminal, snapshot.turnOwnerAgentId());
                    AgentTownLifeEventPublisher.encounter(
                            participantEntry, participant, participantState.snapshot(), nowMs);
                    participantState.clear();
                }
            }
            AgentTownLifeVenueReservationService.releaseGroup(snapshot.participantAgentIds());
            rememberEncounter(snapshot, completed, nowMs);
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
        Character peer = AgentRuntimeIdentityRuntime.bot(
                AgentRuntimeRegistry.findByAgentCharacterId(snapshot.peerAgentId()));
        if (peer == null || peer.getMapId() != agent.getMapId()
                || peer.getPosition().distanceSq(agent.getPosition())
                > (long) PRESENTATION_DISTANCE_PX * PRESENTATION_DISTANCE_PX) {
            return;
        }
        gateway.facePosition(agent, new Point(peer.getPosition()));
        if (snapshot.phase() == AgentTownLifeEncounterState.Phase.REACTING
                && encounter.reactionReady(nowMs)
                && !encounter.reactionShown()) {
            int expression = snapshot.type() == AgentTownLifeEncounterState.Type.PLAYFUL_SPARRING
                    ? AgentEmote.ANNOYED.getValue() : AgentEmote.HAPPY.getValue();
            agent.changeFaceExpression(expression);
            encounter.markReactionShown();
        }
    }

    private static List<Character> acceptedParticipants(Character initiator,
                                                        AgentTownLifeState townState,
                                                        AgentTownLifeEncounterState.Type type,
                                                        int venueCapacity,
                                                        long nowMs) {
        int desired = type == AgentTownLifeEncounterState.Type.SOCIAL_CHAT
                ? 2 + AgentTownLifeRolePolicy.variation(
                initiator.getId(), townState.sequence(), 3, 419) : 2;
        desired = Math.min(desired, Math.min(4, venueCapacity));
        List<Character> eligible = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .filter(AgentTownLifeRuntime::active)
                .filter(entry -> !active(entry))
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(candidate -> eligible(candidate, initiator))
                .toList();
        List<Character> available = eligible.stream()
                .filter(candidate -> mutuallyAvailable(initiator, candidate, townState, nowMs))
                .toList();
        List<Character> candidates = (available.isEmpty() ? eligible : available).stream()
                .sorted(Comparator.<Character>comparingInt(candidate ->
                        -townState.memory().peerPreferenceScore(
                                candidate.getId(), traits(initiator), nowMs))
                        .thenComparingInt(Character::getId))
                .toList();
        List<Character> participants = new ArrayList<>();
        participants.add(initiator);
        Character requested = candidates.stream()
                .filter(candidate -> candidate.getId() == townState.targetCharacterId())
                .findFirst().orElse(null);
        if (requested != null && accepts(requested, initiator, townState.sequence(), type)) {
            participants.add(requested);
        } else if (requested != null) {
            rememberPair(initiator, requested, type, townState.venueId(),
                    AgentTownLifeMemory.SocialOutcome.DECLINED, nowMs);
        }
        for (Character candidate : candidates) {
            if (participants.size() >= desired) {
                break;
            }
            if (!participants.contains(candidate)
                    && accepts(candidate, initiator, townState.sequence(), type)) {
                participants.add(candidate);
            }
        }
        return participants;
    }

    private static boolean mutuallyAvailable(Character initiator,
                                             Character candidate,
                                             AgentTownLifeState initiatorState,
                                             long nowMs) {
        AgentRuntimeEntry candidateEntry = AgentRuntimeRegistry.findByCharacterInstance(candidate);
        AgentTownLifeState candidateState = candidateEntry == null ? null
                : candidateEntry.capabilityStates().find(AgentTownLifeState.STATE_KEY).orElse(null);
        return initiatorState.memory().peerAvailable(candidate.getId(), nowMs)
                && candidateState != null
                && candidateState.memory().peerAvailable(initiator.getId(), nowMs);
    }

    private static AgentPersonalityProfile.Traits traits(Character agent) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(agent);
        return entry == null ? null : entry.capabilityStates()
                .find(AgentPersonalityState.STATE_KEY)
                .map(AgentPersonalityState::profile)
                .map(AgentPersonalityProfile::traits)
                .orElse(null);
    }

    private static void rememberEncounter(AgentTownLifeEncounterState.Snapshot snapshot,
                                          boolean completed,
                                          long nowMs) {
        AgentTownLifeMemory.SocialOutcome outcome = completed
                ? AgentTownLifeMemory.SocialOutcome.COMPLETED
                : AgentTownLifeMemory.SocialOutcome.CANCELLED;
        for (int participantId : snapshot.participantAgentIds()) {
            AgentRuntimeEntry participantEntry =
                    AgentRuntimeRegistry.findByAgentCharacterId(participantId);
            AgentTownLifeState townState = participantEntry == null ? null
                    : participantEntry.capabilityStates()
                    .find(AgentTownLifeState.STATE_KEY).orElse(null);
            if (townState == null) {
                continue;
            }
            for (int peerId : snapshot.participantAgentIds()) {
                if (peerId != participantId) {
                    townState.memory().rememberSocial(
                            peerId, snapshot.type(), snapshot.venueId(), outcome, nowMs);
                }
            }
        }
    }

    private static void rememberPair(Character first,
                                     Character second,
                                     AgentTownLifeEncounterState.Type type,
                                     String venueId,
                                     AgentTownLifeMemory.SocialOutcome outcome,
                                     long nowMs) {
        rememberPeer(first, second.getId(), type, venueId, outcome, nowMs);
        rememberPeer(second, first.getId(), type, venueId, outcome, nowMs);
    }

    private static void rememberPeer(Character owner,
                                     int peerId,
                                     AgentTownLifeEncounterState.Type type,
                                     String venueId,
                                     AgentTownLifeMemory.SocialOutcome outcome,
                                     long nowMs) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(owner);
        if (entry != null) {
            entry.capabilityStates().find(AgentTownLifeState.STATE_KEY).ifPresent(state ->
                    state.memory().rememberSocial(peerId, type, venueId, outcome, nowMs));
        }
    }

    private static boolean eligible(Character candidate, Character initiator) {
        if (candidate == null || candidate == initiator
                || candidate.getMapId() != initiator.getMapId()) {
            return false;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(candidate);
        AgentTownLifeState state = entry == null ? null
                : entry.capabilityStates().find(AgentTownLifeState.STATE_KEY).orElse(null);
        return state != null && state.enabled()
                && (state.stage() == AgentTownLifeState.Stage.CHOOSE_ACTIVITY
                || state.stage() == AgentTownLifeState.Stage.DWELL);
    }

    private static boolean accepts(Character candidate,
                                   Character initiator,
                                   int sequence,
                                   AgentTownLifeEncounterState.Type type) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(candidate);
        int sociability = entry == null ? 50 : entry.capabilityStates()
                .find(AgentPersonalityState.STATE_KEY)
                .map(AgentPersonalityState::profile)
                .map(profile -> profile.traits().sociability())
                .orElse(50);
        int threshold = type == AgentTownLifeEncounterState.Type.SOCIAL_CHAT
                ? 40 + sociability / 2 : 35 + sociability / 3;
        int roll = AgentTownLifeRolePolicy.variation(
                candidate.getId() ^ initiator.getId(), sequence, 100, 431);
        return roll < threshold;
    }

    private static void transitionGroup(AgentRuntimeEntry entry,
                                        AgentTownLifeEncounterState.Phase phase,
                                        int turnOwnerAgentId,
                                        long nowMs) {
        AgentTownLifeEncounterState.Snapshot snapshot = snapshot(entry);
        if (snapshot == null) {
            return;
        }
        synchronized (LOCK) {
            for (int participantId : snapshot.participantAgentIds()) {
                AgentRuntimeEntry participantEntry =
                        AgentRuntimeRegistry.findByAgentCharacterId(participantId);
                Character participant = AgentRuntimeIdentityRuntime.bot(participantEntry);
                transitionParticipant(participantEntry, participant, phase, turnOwnerAgentId, nowMs);
            }
        }
    }

    private static void transitionParticipant(AgentRuntimeEntry entry,
                                              Character agent,
                                              AgentTownLifeEncounterState.Phase phase,
                                              int turnOwnerAgentId,
                                              long nowMs) {
        if (entry == null) {
            return;
        }
        AgentTownLifeEncounterState state = entry.capabilityStates()
                .require(AgentTownLifeEncounterState.STATE_KEY);
        if (!state.active() || state.snapshot().phase() == phase) {
            return;
        }
        long reactionAtMs = phase == AgentTownLifeEncounterState.Phase.REACTING
                ? nowMs + AgentTownLifeRolePolicy.variation(
                agent == null ? 0 : agent.getId(),
                state.snapshot().participantAgentIds().size(), 1_201, 443)
                : 0L;
        state.transition(phase, turnOwnerAgentId, reactionAtMs);
        AgentTownLifeEventPublisher.encounter(entry, agent, state.snapshot(), nowMs);
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
