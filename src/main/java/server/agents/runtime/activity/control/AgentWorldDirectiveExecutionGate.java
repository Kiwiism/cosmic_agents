package server.agents.runtime.activity.control;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.control.rollout.AgentWorldDirectorRolloutGateResult;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;

@FunctionalInterface
public interface AgentWorldDirectiveExecutionGate {
    AgentWorldDirectorRolloutGateResult inspect(
            AgentWorldDirectorSession session,
            AgentWorldDirective directive,
            AgentRuntimeEntry entry,
            Character agent,
            long nowMs);

    static AgentWorldDirectiveExecutionGate disabled() {
        return (session, directive, entry, agent, nowMs) ->
                AgentWorldDirectorRolloutGateResult.block("live Director execution is disabled");
    }
}
