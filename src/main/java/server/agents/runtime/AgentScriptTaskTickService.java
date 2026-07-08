package server.agents.runtime;

import server.agents.plans.AgentScriptTaskStateRuntime;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.plans.AgentTask;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public final class AgentScriptTaskTickService {
    private AgentScriptTaskTickService() {
    }

    public static void tick(AgentRuntimeEntry entry,
                            BiConsumer<AgentRuntimeEntry, AgentTask> startTask,
                            BiPredicate<AgentRuntimeEntry, AgentTask> isTaskComplete) {
        if (!AgentRuntimeIdentityRuntime.hasBot(entry)) {
            return;
        }

        while (true) {
            AgentTask activeScriptTask = AgentScriptTaskStateRuntime.activeTask(entry);
            if (activeScriptTask == null) {
                activeScriptTask = AgentScriptTaskStateRuntime.activateNextTask(entry);
                if (activeScriptTask == null) {
                    return;
                }
                startTask.accept(entry, activeScriptTask);
            }

            if (!isTaskComplete.test(entry, activeScriptTask)) {
                return;
            }
            AgentScriptTaskStateRuntime.clearActiveTask(entry);
        }
    }
}
