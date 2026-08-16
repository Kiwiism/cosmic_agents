package server.agents.runtime.activity.session;

/** Optional terminal evidence projection for selectors, operators, and durable journals. */
@FunctionalInterface
public interface AgentActivityOutcomePort {
    AgentActivityTerminalOutcome terminalOutcome(long nowMs);
}
