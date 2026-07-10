package server.agents.integration;

public record AgentAccountResolution(int accountId, boolean created, String failureMessage) {
    public static AgentAccountResolution created(int accountId) {
        return new AgentAccountResolution(accountId, true, null);
    }

    public static AgentAccountResolution reused(int accountId) {
        return new AgentAccountResolution(accountId, false, null);
    }

    public static AgentAccountResolution failure(String failureMessage) {
        return new AgentAccountResolution(-1, false, failureMessage);
    }

    public boolean isSuccess() {
        return accountId > 0;
    }
}
