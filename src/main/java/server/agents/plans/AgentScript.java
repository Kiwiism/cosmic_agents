package server.agents.plans;

import client.Character;
import server.agents.runtime.AgentScriptTaskQueueService;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

public interface AgentScript {
    String id();

    boolean applies(AgentRuntimeEntry entry, Character bot, Character owner);

    List<AgentScriptStep> steps();

    default void onExit(AgentRuntimeEntry entry) {
        AgentScriptTaskQueueService.clearTasks(entry);
    }
}
