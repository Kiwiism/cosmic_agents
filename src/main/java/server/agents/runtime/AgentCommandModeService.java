package server.agents.runtime;

import java.util.function.BooleanSupplier;

public final class AgentCommandModeService {
    private AgentCommandModeService() {
    }

    public static void runPreparedModeCommand(AgentRuntimeEntry entry,
                                              Runnable clearScriptTasks,
                                              Runnable cancelShopVisit,
                                              Runnable startMode) {
        runPreparedModeCommand(entry, () -> true, clearScriptTasks, cancelShopVisit, startMode);
    }

    public static void runPreparedModeCommand(AgentRuntimeEntry entry,
                                              BooleanSupplier canStart,
                                              Runnable clearScriptTasks,
                                              Runnable cancelShopVisit,
                                              Runnable startMode) {
        if (entry == null) {
            return;
        }
        if (!canStart.getAsBoolean()) {
            return;
        }
        clearScriptTasks.run();
        cancelShopVisit.run();
        startMode.run();
    }
}
