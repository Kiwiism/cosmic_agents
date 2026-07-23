package server.agents.capabilities.townlife;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.List;

/** Per-participant copy of a bounded social encounter contract. */
public final class AgentTownLifeEncounterState {
    public static final AgentCapabilityStateKey<AgentTownLifeEncounterState> STATE_KEY =
            new AgentCapabilityStateKey<>("town-life.encounter", AgentTownLifeEncounterState.class,
                    AgentTownLifeEncounterState::new);

    public enum Type {
        SOCIAL_CHAT,
        PLAYFUL_SPARRING
    }

    public enum Role {
        INITIATOR,
        RESPONDER
    }

    public enum Phase {
        INVITED,
        ACCEPTED,
        APPROACHING,
        ACTIVE,
        REACTING,
        CLOSING,
        COMPLETED,
        CANCELLED
    }

    private String encounterId = "";
    private Type type;
    private Role role;
    private Phase phase;
    private int peerAgentId;
    private int turnOwnerAgentId;
    private List<Integer> participantAgentIds = List.of();
    private String venueId = "";
    private String correlationId = "";
    private long expiresAtMs;
    private long reactionAtMs;
    private boolean reactionShown;

    synchronized void begin(String id,
                            Type nextType,
                            Role nextRole,
                            Phase nextPhase,
                            int nextPeerAgentId,
                            int nextTurnOwnerAgentId,
                            String nextVenueId,
                            String nextCorrelationId,
                            long nextExpiresAtMs) {
        begin(id, nextType, nextRole, nextPhase, nextPeerAgentId, nextTurnOwnerAgentId,
                List.of(nextPeerAgentId, nextTurnOwnerAgentId).stream().distinct().toList(),
                nextVenueId, nextCorrelationId, nextExpiresAtMs);
    }

    synchronized void begin(String id,
                            Type nextType,
                            Role nextRole,
                            Phase nextPhase,
                            int nextPeerAgentId,
                            int nextTurnOwnerAgentId,
                            List<Integer> nextParticipantAgentIds,
                            String nextVenueId,
                            String nextCorrelationId,
                            long nextExpiresAtMs) {
        encounterId = id;
        type = nextType;
        role = nextRole;
        phase = nextPhase;
        peerAgentId = nextPeerAgentId;
        turnOwnerAgentId = nextTurnOwnerAgentId;
        participantAgentIds = List.copyOf(nextParticipantAgentIds == null
                ? List.of() : nextParticipantAgentIds);
        venueId = nextVenueId == null ? "" : nextVenueId;
        correlationId = nextCorrelationId == null || nextCorrelationId.isBlank()
                ? id : nextCorrelationId;
        expiresAtMs = nextExpiresAtMs;
        reactionAtMs = 0L;
        reactionShown = false;
    }

    synchronized void transition(Phase nextPhase, int nextTurnOwnerAgentId) {
        transition(nextPhase, nextTurnOwnerAgentId, 0L);
    }

    synchronized void transition(Phase nextPhase,
                                 int nextTurnOwnerAgentId,
                                 long nextReactionAtMs) {
        if (!active()) {
            return;
        }
        phase = nextPhase;
        turnOwnerAgentId = nextTurnOwnerAgentId;
        if (nextPhase == Phase.REACTING) {
            reactionAtMs = Math.max(0L, nextReactionAtMs);
            reactionShown = false;
        }
    }

    public synchronized boolean active() {
        return !encounterId.isBlank() && phase != Phase.COMPLETED && phase != Phase.CANCELLED;
    }

    synchronized boolean expired(long nowMs) {
        return active() && nowMs >= expiresAtMs;
    }

    synchronized boolean reactionShown() {
        return reactionShown;
    }

    synchronized boolean reactionReady(long nowMs) {
        return nowMs >= reactionAtMs;
    }

    synchronized void markReactionShown() {
        reactionShown = true;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(active(), encounterId, type, role, phase, peerAgentId,
                turnOwnerAgentId, participantAgentIds, venueId, correlationId, expiresAtMs);
    }

    synchronized void clear() {
        encounterId = "";
        type = null;
        role = null;
        phase = null;
        peerAgentId = 0;
        turnOwnerAgentId = 0;
        participantAgentIds = List.of();
        venueId = "";
        correlationId = "";
        expiresAtMs = 0L;
        reactionShown = false;
        reactionAtMs = 0L;
    }

    public record Snapshot(boolean active,
                           String encounterId,
                           Type type,
                           Role role,
                           Phase phase,
                           int peerAgentId,
                           int turnOwnerAgentId,
                           List<Integer> participantAgentIds,
                           String venueId,
                           String correlationId,
                           long expiresAtMs) {
        public Snapshot {
            participantAgentIds = List.copyOf(
                    participantAgentIds == null ? List.of() : participantAgentIds);
        }
    }
}
