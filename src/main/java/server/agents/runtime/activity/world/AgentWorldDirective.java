package server.agents.runtime.activity.world;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Map;

/**
 * Immutable command contract. Acceptance and execution are deliberately kept
 * outside this value so persisted directives can be replayed idempotently.
 */
public record AgentWorldDirective(
        int schemaVersion,
        String directiveId,
        int agentId,
        AgentWorldDirectiveType type,
        AgentWorldDirectiveSource source,
        AgentWorldDirectorMode requestedMode,
        AgentActivityKind targetActivityKind,
        AgentWorldActivityRequestType requestType,
        String requestId,
        Map<String, String> parameters,
        AgentWorldInterruptionPolicy interruptionPolicy,
        AgentWorldCompletionPolicy completionPolicy,
        int priority,
        long createdAtMs,
        long expiresAtMs,
        String reason) {

    public AgentWorldDirective {
        directiveId = normalize(directiveId);
        requestId = normalize(requestId);
        reason = normalize(reason);
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
        if (schemaVersion != 1 || directiveId.isEmpty() || agentId <= 0 || type == null
                || source == null || interruptionPolicy == null || completionPolicy == null
                || priority < 0 || createdAtMs < 0L
                || (expiresAtMs > 0L && expiresAtMs <= createdAtMs)) {
            throw new IllegalArgumentException("valid World Director directive fields are required");
        }
        validatePayload(type, requestedMode, targetActivityKind, requestType, requestId);
    }

    public boolean expiredAt(long nowMs) {
        return expiresAtMs > 0L && nowMs >= expiresAtMs;
    }

    private static void validatePayload(
            AgentWorldDirectiveType type,
            AgentWorldDirectorMode requestedMode,
            AgentActivityKind targetActivityKind,
            AgentWorldActivityRequestType requestType,
            String requestId) {
        if (type == AgentWorldDirectiveType.SET_MODE && requestedMode == null) {
            throw new IllegalArgumentException("SET_MODE requires a requested mode");
        }
        if ((type == AgentWorldDirectiveType.START_ACTIVITY
                || type == AgentWorldDirectiveType.TRANSFER_ACTIVITY)
                && (targetActivityKind == null || requestType == null || requestId.isEmpty())) {
            throw new IllegalArgumentException("activity directives require a target and request identity");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
