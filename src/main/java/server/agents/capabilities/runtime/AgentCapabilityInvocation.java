package server.agents.capabilities.runtime;

public final class AgentCapabilityInvocation<C extends AgentCapabilityCommand> {
    private final AgentExecutableCapability<C> capability;
    private final C command;
    private final long timeoutMs;
    private final int maxRetries;

    public AgentCapabilityInvocation(AgentExecutableCapability<C> capability,
                                     C command,
                                     long timeoutMs,
                                     int maxRetries) {
        if (capability == null || command == null) {
            throw new IllegalArgumentException("capability and command are required");
        }
        if (timeoutMs <= 0 || maxRetries < 0) {
            throw new IllegalArgumentException("timeout must be positive and retries cannot be negative");
        }
        this.capability = capability;
        this.command = command;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
    }

    public String capabilityId() {
        return capability.id();
    }

    public String commandType() {
        return command.type();
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    public int maxRetries() {
        return maxRetries;
    }

    AgentCapabilityStep tick(AgentCapabilityContext context) {
        return capability.tick(context, command);
    }

    void onTerminal(AgentCapabilityContext context, AgentCapabilityResult result) {
        capability.onTerminal(context, command, result);
    }
}
