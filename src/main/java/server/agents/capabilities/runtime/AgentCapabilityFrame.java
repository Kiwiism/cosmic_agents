package server.agents.capabilities.runtime;

final class AgentCapabilityFrame {
    final AgentCapabilityInvocation<?> invocation;
    AgentCapabilityFrameState state = AgentCapabilityFrameState.STARTING;
    long startedAtMs;
    long deadlineMs;
    int retryCount;
    AgentCapabilityResult childResult;
    final AgentCapabilityMemory memory = new AgentCapabilityMemory();

    AgentCapabilityFrame(AgentCapabilityInvocation<?> invocation) {
        this.invocation = invocation;
    }
}
