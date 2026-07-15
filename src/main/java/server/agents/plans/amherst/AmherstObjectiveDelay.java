package server.agents.plans.amherst;

import server.agents.profiles.AgentBehaviorProfileRuntime;
import server.agents.runtime.AgentRuntimeEntry;

@FunctionalInterface
public interface AmherstObjectiveDelay {
    AmherstObjectiveDelay NONE = () -> 0L;

    long nextDelayMs();

    static AmherstObjectiveDelay profile(AgentRuntimeEntry entry) {
        return () -> AgentBehaviorProfileRuntime.sampleBetweenObjectivesDelayMs(entry);
    }
}
