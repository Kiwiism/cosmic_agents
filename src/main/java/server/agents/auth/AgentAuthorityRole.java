package server.agents.auth;

public enum AgentAuthorityRole {
    OBSERVER,
    OPERATOR,
    ADMINISTRATOR;

    public boolean permits(AgentAuthorityRole required) {
        return ordinal() >= required.ordinal();
    }
}
