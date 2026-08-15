package server.agents.capabilities.navigation;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/** Execution port separating edge selection from movement side effects. */
@FunctionalInterface
public interface AgentTraversalExecutor {
    AgentTraversalResult execute(
            AgentRuntimeEntry entry, Character agent, AgentTraversalCommand command);
}
