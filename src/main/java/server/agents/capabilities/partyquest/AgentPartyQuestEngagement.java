package server.agents.capabilities.partyquest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns one Agent party from activity acquisition through lobby, event, and recovery. */
public final class AgentPartyQuestEngagement {
    private static final int MAX_DIAGNOSTICS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.AgentPartyQuestEngagement.MAX_DIAGNOSTICS");
    public enum Mode { PRODUCTION, BACKGROUND_POPULATION, TEST_OBSERVATION }
    public enum State {
        ACQUIRING_AGENTS,
        LOBBY_FORMING,
        LOBBY_READY,
        RESERVING_ENTRY,
        ACTIVE_EVENT,
        POST_RUN_HOLD,
        RECOVERING,
        CLOSED,
        FAILED
    }
    public enum MemberType { AGENT, HUMAN }

    private final String engagementId;
    private final String questKey;
    private final Mode mode;
    private final long seed;
    private final long startedAtMs;
    private final int operatorId;
    private final int requestedPartySize;
    private final Map<Integer, MemberType> members = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();
    private State state = State.ACQUIRING_AGENTS;
    private String lobbyId = "";
    private String activeSessionId = "";
    private long revision;
    private long stateEnteredAtMs;
    private long lastProgressAtMs;
    private String failure = "";
    private long lastRecoveryAttemptAtMs = Long.MIN_VALUE;
    private long lastRecoveryWarningAtMs = Long.MIN_VALUE;

    public AgentPartyQuestEngagement(
            String questKey, Mode mode, long seed, int operatorId,
            int requestedPartySize, long nowMs) {
        if (questKey == null || questKey.isBlank() || mode == null || operatorId <= 0
                || requestedPartySize < 1 || nowMs < 0L) {
            throw new IllegalArgumentException("valid party-quest engagement is required");
        }
        this.engagementId = "pq-" + UUID.randomUUID();
        this.questKey = questKey.trim().toLowerCase();
        this.mode = mode;
        this.seed = seed;
        this.startedAtMs = nowMs;
        this.operatorId = operatorId;
        this.requestedPartySize = requestedPartySize;
        this.stateEnteredAtMs = nowMs;
        this.lastProgressAtMs = nowMs;
    }

    public synchronized void addMember(int characterId, MemberType type, long nowMs) {
        if (characterId <= 0 || type == null) throw new IllegalArgumentException("valid member is required");
        if (members.putIfAbsent(characterId, type) == null) markProgress(nowMs);
    }

    public synchronized void removeMember(int characterId, long nowMs) {
        if (members.remove(characterId) != null) markProgress(nowMs);
    }

    public synchronized void beginLobby(String nextLobbyId, long nowMs) {
        if (nextLobbyId == null || nextLobbyId.isBlank()) {
            throw new IllegalArgumentException("lobby id is required");
        }
        requireState(State.ACQUIRING_AGENTS, State.POST_RUN_HOLD, State.LOBBY_FORMING);
        lobbyId = nextLobbyId;
        activeSessionId = "";
        transition(State.LOBBY_FORMING, nowMs, "");
    }

    public synchronized void lobbyReady(long nowMs) {
        requireState(State.LOBBY_FORMING, State.LOBBY_READY);
        transition(State.LOBBY_READY, nowMs, "");
    }

    public synchronized void reserveEntry(long nowMs) {
        requireState(State.LOBBY_FORMING, State.LOBBY_READY, State.RESERVING_ENTRY);
        transition(State.RESERVING_ENTRY, nowMs, "");
    }

    public synchronized void restoreLobby(String reason, long nowMs) {
        requireState(State.LOBBY_FORMING, State.LOBBY_READY,
                State.RESERVING_ENTRY, State.ACTIVE_EVENT);
        activeSessionId = "";
        transition(State.LOBBY_FORMING, nowMs, reason);
    }

    public synchronized void activateSession(String sessionId, long nowMs) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("active session id is required");
        }
        requireState(State.RESERVING_ENTRY, State.LOBBY_READY, State.LOBBY_FORMING);
        activeSessionId = sessionId;
        lobbyId = "";
        transition(State.ACTIVE_EVENT, nowMs, "");
    }

    public synchronized void finishRun(boolean success, String reason, long nowMs) {
        requireState(State.ACTIVE_EVENT);
        activeSessionId = "";
        if (mode == Mode.TEST_OBSERVATION || success) {
            transition(State.POST_RUN_HOLD, nowMs, reason);
        } else {
            transition(State.RECOVERING, nowMs, reason);
        }
    }

    public synchronized void beginRecovery(String reason, long nowMs) {
        if (state == State.RECOVERING) {
            appendDiagnostic(reason);
            return;
        }
        if (state == State.CLOSED || state == State.FAILED) return;
        activeSessionId = "";
        lobbyId = "";
        transition(State.RECOVERING, nowMs, reason);
    }

    public synchronized void close(long nowMs) {
        activeSessionId = "";
        lobbyId = "";
        transition(State.CLOSED, nowMs, "");
    }

    public synchronized void fail(String reason, long nowMs) {
        failure = reason == null || reason.isBlank() ? "unknown party-quest failure" : reason;
        activeSessionId = "";
        lobbyId = "";
        transition(State.FAILED, nowMs, failure);
    }

    public synchronized void addDiagnostic(String message, long nowMs) {
        if (message == null || message.isBlank()) return;
        appendDiagnostic(message);
        markProgress(nowMs);
    }

    public synchronized boolean claimRecoveryAttempt(long nowMs, long minimumIntervalMs) {
        if (state != State.RECOVERING) return false;
        if (lastRecoveryAttemptAtMs != Long.MIN_VALUE
                && nowMs - lastRecoveryAttemptAtMs < Math.max(1L, minimumIntervalMs)) return false;
        lastRecoveryAttemptAtMs = nowMs;
        return true;
    }

    public synchronized boolean claimRecoveryWarning(long nowMs, long minimumIntervalMs) {
        if (state != State.RECOVERING) return false;
        if (lastRecoveryWarningAtMs != Long.MIN_VALUE
                && nowMs - lastRecoveryWarningAtMs < Math.max(1L, minimumIntervalMs)) return false;
        lastRecoveryWarningAtMs = nowMs;
        return true;
    }

    private void transition(State next, long nowMs, String diagnostic) {
        state = next;
        stateEnteredAtMs = nowMs;
        markProgress(nowMs);
        appendDiagnostic(diagnostic);
    }

    private void appendDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.isBlank()) return;
        String normalized = diagnostic.trim();
        if (!diagnostics.isEmpty() && diagnostics.getLast().equals(normalized)) return;
        int maximum = Math.max(1, MAX_DIAGNOSTICS);
        if (diagnostics.size() >= maximum) diagnostics.removeFirst();
        diagnostics.add(normalized);
    }

    private void markProgress(long nowMs) {
        lastProgressAtMs = Math.max(lastProgressAtMs, nowMs);
        revision++;
    }

    private void requireState(State... allowed) {
        for (State candidate : allowed) if (state == candidate) return;
        throw new IllegalStateException("party-quest transition is invalid from " + state);
    }

    public synchronized String engagementId() { return engagementId; }
    public synchronized String questKey() { return questKey; }
    public synchronized Mode mode() { return mode; }
    public synchronized long seed() { return seed; }
    public synchronized long startedAtMs() { return startedAtMs; }
    public synchronized int operatorId() { return operatorId; }
    public synchronized int requestedPartySize() { return requestedPartySize; }
    public synchronized State state() { return state; }
    public synchronized String lobbyId() { return lobbyId; }
    public synchronized String activeSessionId() { return activeSessionId; }
    public synchronized long revision() { return revision; }
    public synchronized long stateEnteredAtMs() { return stateEnteredAtMs; }
    public synchronized long lastProgressAtMs() { return lastProgressAtMs; }
    public synchronized String failure() { return failure; }
    public synchronized Map<Integer, MemberType> members() { return Map.copyOf(members); }
    public synchronized List<Integer> memberIds() { return List.copyOf(members.keySet()); }
    public synchronized List<Integer> agentIds() {
        return members.entrySet().stream().filter(entry -> entry.getValue() == MemberType.AGENT)
                .map(Map.Entry::getKey).toList();
    }
    public synchronized List<String> diagnostics() { return List.copyOf(diagnostics); }
    public synchronized boolean ownsAgent(int characterId) {
        return members.get(characterId) == MemberType.AGENT && state != State.CLOSED && state != State.FAILED;
    }
}
