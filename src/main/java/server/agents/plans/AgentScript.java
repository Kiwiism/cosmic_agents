package server.agents.plans;

import client.Character;
import server.agents.runtime.AgentScriptTaskQueueService;
import server.bots.BotEntry;

import java.util.List;

public interface AgentScript {
    String id();

    boolean applies(BotEntry entry, Character bot, Character owner);

    List<AgentScriptStep> steps();

    default void onExit(BotEntry entry) {
        AgentScriptTaskQueueService.clearTasks(entry);
    }
}
