package server.agents.runtime;

import server.bots.BotEntry;

public final class AgentCommandModeService {
    private AgentCommandModeService() {
    }

    public static void runPreparedModeCommand(BotEntry entry,
                                              Runnable clearScriptTasks,
                                              Runnable cancelShopVisit,
                                              Runnable startMode) {
        if (entry == null) {
            return;
        }
        clearScriptTasks.run();
        cancelShopVisit.run();
        startMode.run();
    }
}
