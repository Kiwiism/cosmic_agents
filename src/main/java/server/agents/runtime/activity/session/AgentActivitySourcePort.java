package server.agents.runtime.activity.session;

/** Bound source session used by the world-level handoff coordinator. */
public interface AgentActivitySourcePort {
    AgentActivitySessionSnapshot snapshot(long nowMs);

    AgentActivityExitResult requestGracefulExit(String reason, long nowMs, long deadlineMs);
}
