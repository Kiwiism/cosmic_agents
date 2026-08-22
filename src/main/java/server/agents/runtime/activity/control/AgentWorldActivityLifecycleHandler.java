package server.agents.runtime.activity.control;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;

/** Executes non-admission lifecycle directives without leaking child-system internals. */
@FunctionalInterface
public interface AgentWorldActivityLifecycleHandler {
    Result advance(
            AgentWorldDirective directive,
            AgentWorldDirectorSession directorSession,
            AgentRuntimeEntry entry,
            Character agent,
            AgentActivityKind sourceKind,
            String sourceSessionId,
            long nowMs);

    static AgentWorldActivityLifecycleHandler unsupported() {
        return (directive, session, entry, agent, kind, sessionId, nowMs) ->
                Result.rejected("activity lifecycle routing is not configured", kind, sessionId);
    }

    record Result(Status status, String reason, AgentActivityKind activityKind, String sessionId) {
        public Result {
            if (status == null) throw new IllegalArgumentException("lifecycle status is required");
            reason = reason == null ? "" : reason.trim();
            sessionId = sessionId == null ? "" : sessionId.trim();
        }

        public static Result progressed(String reason, AgentActivityKind kind, String sessionId) {
            return new Result(Status.PROGRESSED, reason, kind, sessionId);
        }

        public static Result completed(String reason, AgentActivityKind kind, String sessionId) {
            return new Result(Status.COMPLETED, reason, kind, sessionId);
        }

        public static Result rejected(String reason, AgentActivityKind kind, String sessionId) {
            return new Result(Status.REJECTED, reason, kind, sessionId);
        }

        public enum Status { PROGRESSED, COMPLETED, REJECTED }
    }
}
