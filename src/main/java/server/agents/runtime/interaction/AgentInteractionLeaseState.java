package server.agents.runtime.interaction;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** One bounded foreground interaction layered over a resumable local activity. */
public final class AgentInteractionLeaseState {
    public static final AgentCapabilityStateKey<AgentInteractionLeaseState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.interaction-lease",
                    AgentInteractionLeaseState.class, AgentInteractionLeaseState::new);

    public enum Type {
        CHAT,
        TRADE
    }

    private String interactionId = "";
    private Type type;
    private int participantCharacterId;
    private String parentActivityId = "";
    private String parentSessionId = "";
    private long startedAtMs;
    private long minimumReleaseAtMs;
    private long deadlineMs;
    private boolean operationComplete;

    synchronized String begin(Type nextType,
                              int nextParticipantCharacterId,
                              String nextParentActivityId,
                              String nextParentSessionId,
                              long nowMs,
                              long minimumDurationMs,
                              long timeoutMs) {
        if (active()) {
            if (type == Type.TRADE || nextType == type) {
                deadlineMs = Math.max(deadlineMs, nowMs + timeoutMs);
                participantCharacterId = nextParticipantCharacterId > 0
                        ? nextParticipantCharacterId : participantCharacterId;
                operationComplete = false;
                return interactionId;
            }
        }
        interactionId = "interaction:" + nextType.name().toLowerCase(java.util.Locale.ROOT)
                + ':' + Long.toUnsignedString(nowMs, 36);
        type = nextType;
        participantCharacterId = Math.max(0, nextParticipantCharacterId);
        parentActivityId = normalize(nextParentActivityId);
        parentSessionId = normalize(nextParentSessionId);
        startedAtMs = Math.max(0L, nowMs);
        minimumReleaseAtMs = nowMs + Math.max(0L, minimumDurationMs);
        deadlineMs = nowMs + Math.max(1L, timeoutMs);
        operationComplete = false;
        return interactionId;
    }

    public synchronized boolean active() {
        return !interactionId.isBlank() && type != null;
    }

    synchronized void markComplete() {
        operationComplete = true;
    }

    synchronized boolean readyToRelease(long nowMs) {
        return active() && ((operationComplete && nowMs >= minimumReleaseAtMs)
                || nowMs >= deadlineMs);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(active(), interactionId, type, participantCharacterId,
                parentActivityId, parentSessionId, startedAtMs, minimumReleaseAtMs, deadlineMs,
                operationComplete);
    }

    synchronized void clear() {
        interactionId = "";
        type = null;
        participantCharacterId = 0;
        parentActivityId = "";
        parentSessionId = "";
        startedAtMs = 0L;
        minimumReleaseAtMs = 0L;
        deadlineMs = 0L;
        operationComplete = false;
    }

    public record Snapshot(boolean active,
                           String interactionId,
                           Type type,
                           int participantCharacterId,
                           String parentActivityId,
                           String parentSessionId,
                           long startedAtMs,
                           long minimumReleaseAtMs,
                           long deadlineMs,
                           boolean operationComplete) {
        public String townLifeSessionId() {
            return "town-life".equals(parentActivityId) ? parentSessionId : "";
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
