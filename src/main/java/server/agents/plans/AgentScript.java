package server.agents.plans;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotManager;

import java.util.List;

public interface AgentScript {
    String id();

    boolean applies(BotEntry entry, Character bot, Character owner);

    List<AgentScriptStep> steps();

    default void onExit(BotEntry entry) {
        BotManager.getInstance().clearScriptTasks(entry);
    }
}
