package server.agents.auth;

public record AgentAuthorizationResult(boolean allowed, boolean autoRegistered, String failureMessage) {
    public static AgentAuthorizationResult allowed(boolean autoRegistered) {
        return new AgentAuthorizationResult(true, autoRegistered, null);
    }

    public static AgentAuthorizationResult denied(String failureMessage) {
        return new AgentAuthorizationResult(false, false, failureMessage);
    }
}
