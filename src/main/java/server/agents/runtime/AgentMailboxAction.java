package server.agents.runtime;

/** Immutable intent executed against one live Agent session by its tick path. */
@FunctionalInterface
public interface AgentMailboxAction<R> {
    R execute(AgentRuntimeEntry entry);
}
