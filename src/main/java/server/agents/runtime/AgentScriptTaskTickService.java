package server.agents.runtime;

import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.agents.plans.AgentTask;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public final class AgentScriptTaskTickService {
    private AgentScriptTaskTickService() {
    }

    public static void tick(AgentRuntimeEntry entry,
                            BiConsumer<AgentRuntimeEntry, AgentTask> startTask,
                            BiPredicate<AgentRuntimeEntry, AgentTask> isTaskComplete) {
        if (!AgentBotRuntimeIdentityRuntime.hasBot(entry)) {
            return;
        }

        while (true) {
            AgentTask activeScriptTask = AgentBotScriptTaskStateRuntime.activeTask(entry);
            if (activeScriptTask == null) {
                activeScriptTask = AgentBotScriptTaskStateRuntime.activateNextTask(entry);
                if (activeScriptTask == null) {
                    return;
                }
                startTask.accept(entry, activeScriptTask);
            }

            if (!isTaskComplete.test(entry, activeScriptTask)) {
                return;
            }
            AgentBotScriptTaskStateRuntime.clearActiveTask(entry);
        }
    }
}
