package server.agents.capabilities.runtime;

final class AgentCapabilityFrame {
    final AgentCapabilityInvocation<?> invocation;
    final String lockOwnerId;
    final java.util.Set<AgentCapabilityResource> requiredResources;
    AgentCapabilityFrameState state = AgentCapabilityFrameState.STARTING;
    long startedAtMs;
    long deadlineMs;
    boolean locksAcquired;
    int retryCount;
    AgentCapabilityResult childResult;
    final AgentCapabilityMemory memory = new AgentCapabilityMemory();

    AgentCapabilityFrame(AgentCapabilityInvocation<?> invocation) {
        this(invocation, null);
    }

    AgentCapabilityFrame(AgentCapabilityInvocation<?> invocation, String inheritedLockOwnerId) {
        this.invocation = invocation;
        String correlationId = invocation.metadata().correlationId();
        this.lockOwnerId = inheritedLockOwnerId != null && !inheritedLockOwnerId.isBlank()
                ? inheritedLockOwnerId
                : correlationId == null || correlationId.isBlank()
                ? invocation.capabilityId() + '@'
                    + Integer.toHexString(System.identityHashCode(this))
                : correlationId;
        this.requiredResources = invocation.requiredResources();
    }
}
