package server.agents.capabilities.objective;

import server.agents.profiles.AgentBehaviorProfileRuntime;
import server.agents.runtime.AgentRuntimeEntry;

@FunctionalInterface
public interface AmherstNpcInteractionDelay {
    AmherstNpcInteractionDelay NONE = () -> 0L;

    long nextDelayMs();

    static AmherstNpcInteractionDelay profile(AgentRuntimeEntry entry) {
        return () -> AgentBehaviorProfileRuntime.sampleNpcInteractionDelayMs(entry);
    }
}
