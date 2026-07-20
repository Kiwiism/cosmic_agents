package server.agents.objectives;

/** Outcome of reconciling durable intent with its transient runtime handler. */
public enum AgentObjectiveAttachment {
    ATTACHED,
    ALREADY_ATTACHED,
    TERMINAL
}
