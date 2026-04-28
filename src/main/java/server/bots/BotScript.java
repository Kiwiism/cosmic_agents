package server.bots;

import client.Character;

import java.util.List;

public interface BotScript {
    String id();

    boolean applies(BotEntry entry, Character bot, Character owner);

    List<BotScriptStep> steps();

    default void onExit(BotEntry entry) {
        BotManager.getInstance().clearScriptTasks(entry);
    }
}
