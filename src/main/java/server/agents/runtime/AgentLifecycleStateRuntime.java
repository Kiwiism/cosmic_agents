package server.agents.runtime;

public final class AgentLifecycleStateRuntime {
    private AgentLifecycleStateRuntime() {
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.lifecycleState().phase() == AgentLifecyclePhase.ACTIVE;
    }

    public static void transition(AgentRuntimeEntry entry, AgentLifecyclePhase phase, String reason) {
        AgentLifecycleTransitionService.transition(entry, phase, reason);
    }
}
