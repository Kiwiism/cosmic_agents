package server.agents.runtime.activity.session;

/** Bound destination request. Travel must already have completed before admission is attempted. */
@FunctionalInterface
public interface AgentActivityTargetPort {
    AgentActivityAdmissionResult requestEntry(long nowMs);
}
