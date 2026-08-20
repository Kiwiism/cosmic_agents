package server.agents.capabilities.partyquest.lobby;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Durable party-formation state. Presentation and invitations are handled by the lobby runtime. */
public final class AgentPartyQuestLobbySession {
    public enum State { FORMING, READY, RESERVED, HANDING_OFF, CLOSED, FAILED }
    public enum MemberType { AGENT, HUMAN }
    public enum MemberRole { RECRUITING_LEADER, LOOKING_FOR_PARTY, JOINED_MEMBER, READY_MEMBER }

    private final String lobbyId;
    private final String engagementId;
    private final AgentPartyQuestLobbyProfile profile;
    private final long seed;
    private final int ownerCharacterId;
    private final int requestedPartySize;
    private final AgentPartyQuestCandidateScope candidateScope;
    private final Map<Integer, Member> members = new LinkedHashMap<>();
    private State state = State.FORMING;
    private int partyId;
    private int leaderId;
    private int coordinatorAgentId;
    private long rosterRevision;
    private long stateEnteredAtMs;
    private long lastProgressAtMs;
    private String failure = "";
    private boolean paused;

    public AgentPartyQuestLobbySession(
            String engagementId,
            AgentPartyQuestLobbyProfile profile,
            long seed,
            int ownerCharacterId,
            int requestedPartySize,
            AgentPartyQuestCandidateScope candidateScope,
            long nowMs) {
        if (engagementId == null || engagementId.isBlank() || profile == null
                || ownerCharacterId <= 0 || requestedPartySize < 1
                || requestedPartySize > profile.maximumPartySize() || nowMs < 0L) {
            throw new IllegalArgumentException("valid party-quest lobby is required");
        }
        this.lobbyId = "pql-" + UUID.randomUUID();
        this.engagementId = engagementId;
        this.profile = profile;
        this.seed = seed;
        this.ownerCharacterId = ownerCharacterId;
        this.requestedPartySize = requestedPartySize;
        this.candidateScope = candidateScope == null
                ? AgentPartyQuestCandidateScope.OWNER_ONLY : candidateScope;
        this.stateEnteredAtMs = nowMs;
        this.lastProgressAtMs = nowMs;
    }

    public synchronized void addMember(
            int characterId, MemberType type, MemberRole role, long nowMs) {
        if (characterId <= 0 || type == null || role == null) {
            throw new IllegalArgumentException("valid lobby member is required");
        }
        Member old = members.putIfAbsent(characterId, new Member(characterId, type, role));
        if (old == null) markRosterProgress(nowMs);
    }

    public synchronized void removeMember(int characterId, long nowMs) {
        if (members.remove(characterId) != null) markRosterProgress(nowMs);
    }

    public synchronized void setMemberRole(int characterId, MemberRole role, long nowMs) {
        Member member = members.get(characterId);
        if (member != null && role != null && member.role != role) {
            member.role = role;
            markRosterProgress(nowMs);
        }
    }

    public synchronized void reconcileParty(
            int nextPartyId, int nextLeaderId, Set<Integer> actualMemberIds, long nowMs) {
        Set<Integer> actual = actualMemberIds == null ? Set.of() : Set.copyOf(actualMemberIds);
        boolean changed = partyId != nextPartyId || leaderId != nextLeaderId;
        partyId = Math.max(0, nextPartyId);
        leaderId = Math.max(0, nextLeaderId);
        for (Member member : members.values()) {
            MemberRole nextRole;
            if (actual.contains(member.characterId)) {
                nextRole = state == State.READY && member.role == MemberRole.READY_MEMBER
                        ? MemberRole.READY_MEMBER
                        : member.characterId == leaderId && member.type == MemberType.AGENT
                        ? MemberRole.RECRUITING_LEADER : MemberRole.JOINED_MEMBER;
            } else {
                nextRole = member.type == MemberType.AGENT
                        ? MemberRole.LOOKING_FOR_PARTY : MemberRole.JOINED_MEMBER;
            }
            if (member.role != nextRole) {
                member.role = nextRole;
                changed = true;
            }
        }
        if (changed) {
            state = State.FORMING;
            stateEnteredAtMs = nowMs;
            markRosterProgress(nowMs);
        }
    }

    public synchronized void markReady(long nowMs) {
        if (state != State.FORMING && state != State.READY) {
            throw new IllegalStateException("lobby cannot become ready from " + state);
        }
        state = State.READY;
        stateEnteredAtMs = nowMs;
        members.values().stream()
                .filter(member -> member.role == MemberRole.JOINED_MEMBER)
                .forEach(member -> member.role = MemberRole.READY_MEMBER);
        lastProgressAtMs = nowMs;
    }

    public synchronized void reserve(long nowMs) {
        if (state != State.READY && state != State.FORMING) {
            throw new IllegalStateException("lobby cannot reserve from " + state);
        }
        state = State.RESERVED;
        stateEnteredAtMs = nowMs;
        lastProgressAtMs = nowMs;
    }

    public synchronized void beginHandoff(long nowMs) {
        if (state != State.RESERVED) throw new IllegalStateException("lobby must be reserved first");
        state = State.HANDING_OFF;
        stateEnteredAtMs = nowMs;
        lastProgressAtMs = nowMs;
    }

    public synchronized void restoreForming(String reason, long nowMs) {
        state = State.FORMING;
        stateEnteredAtMs = nowMs;
        lastProgressAtMs = nowMs;
        failure = reason == null ? "" : reason.trim();
        members.values().forEach(member -> {
            if (member.role == MemberRole.READY_MEMBER) member.role = MemberRole.JOINED_MEMBER;
        });
    }

    public synchronized void close(long nowMs) {
        state = State.CLOSED;
        stateEnteredAtMs = nowMs;
        lastProgressAtMs = nowMs;
    }

    public synchronized void fail(String reason, long nowMs) {
        failure = reason == null || reason.isBlank() ? "unknown lobby failure" : reason.trim();
        state = State.FAILED;
        stateEnteredAtMs = nowMs;
        lastProgressAtMs = nowMs;
    }

    public synchronized void setCoordinatorAgentId(int id) { coordinatorAgentId = Math.max(0, id); }
    public synchronized void setPaused(boolean value) { paused = value; }

    private void markRosterProgress(long nowMs) {
        if (state == State.READY) {
            state = State.FORMING;
            stateEnteredAtMs = nowMs;
            members.values().forEach(member -> {
                if (member.role == MemberRole.READY_MEMBER) member.role = MemberRole.JOINED_MEMBER;
            });
        }
        rosterRevision++;
        lastProgressAtMs = Math.max(lastProgressAtMs, nowMs);
    }

    public synchronized String lobbyId() { return lobbyId; }
    public synchronized String engagementId() { return engagementId; }
    public synchronized AgentPartyQuestLobbyProfile profile() { return profile; }
    public synchronized long seed() { return seed; }
    public synchronized int ownerCharacterId() { return ownerCharacterId; }
    public synchronized int requestedPartySize() { return requestedPartySize; }
    public synchronized AgentPartyQuestCandidateScope candidateScope() { return candidateScope; }
    public synchronized State state() { return state; }
    public synchronized int partyId() { return partyId; }
    public synchronized int leaderId() { return leaderId; }
    public synchronized int coordinatorAgentId() { return coordinatorAgentId; }
    public synchronized long rosterRevision() { return rosterRevision; }
    public synchronized long stateEnteredAtMs() { return stateEnteredAtMs; }
    public synchronized long lastProgressAtMs() { return lastProgressAtMs; }
    public synchronized String failure() { return failure; }
    public synchronized boolean paused() { return paused; }
    public synchronized List<MemberSnapshot> members() {
        return members.values().stream().map(Member::snapshot).toList();
    }
    public synchronized List<Integer> memberIds() { return List.copyOf(members.keySet()); }
    public synchronized List<Integer> agentIds() {
        return members.values().stream().filter(member -> member.type == MemberType.AGENT)
                .map(member -> member.characterId).toList();
    }
    public synchronized List<Integer> waiterIds() {
        return members.values().stream()
                .filter(member -> member.type == MemberType.AGENT
                        && member.role == MemberRole.LOOKING_FOR_PARTY)
                .map(member -> member.characterId).toList();
    }
    public synchronized List<Integer> recruiterIds() {
        return members.values().stream()
                .filter(member -> member.type == MemberType.AGENT
                        && member.role == MemberRole.RECRUITING_LEADER)
                .map(member -> member.characterId).toList();
    }
    public synchronized boolean contains(int characterId) { return members.containsKey(characterId); }
    public synchronized boolean readyFor(Set<Integer> actualIds) {
        Set<Integer> actual = actualIds == null ? Set.of() : new LinkedHashSet<>(actualIds);
        return actual.size() == requestedPartySize
                && members.keySet().containsAll(actual)
                && actual.containsAll(members.keySet());
    }
    public synchronized boolean active() {
        return state != State.CLOSED && state != State.FAILED;
    }

    private static final class Member {
        private final int characterId;
        private final MemberType type;
        private MemberRole role;

        private Member(int characterId, MemberType type, MemberRole role) {
            this.characterId = characterId;
            this.type = type;
            this.role = role;
        }

        private MemberSnapshot snapshot() { return new MemberSnapshot(characterId, type, role); }
    }

    public record MemberSnapshot(int characterId, MemberType type, MemberRole role) {
    }
}
