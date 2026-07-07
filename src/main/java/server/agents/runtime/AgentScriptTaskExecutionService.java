package server.agents.runtime;

import client.Character;
import client.inventory.InventoryType;
import server.agents.integration.AgentBotMovementCommandRuntime;
import server.agents.plans.AgentScriptItemActionService;
import server.agents.plans.AgentTask;

import java.util.function.IntFunction;

public final class AgentScriptTaskExecutionService {
    private AgentScriptTaskExecutionService() {
    }

    public static void start(AgentRuntimeEntry entry, AgentTask task) {
        AgentScriptTaskStartService.start(
                entry,
                task,
                new AgentScriptTaskStartService.StartHooks(
                        (point, precise) -> AgentBotMovementCommandRuntime.moveTo(entry, point, precise),
                        target -> AgentBotMovementCommandRuntime.follow(entry, target),
                        targetResolver(entry),
                        () -> AgentBotMovementCommandRuntime.grind(entry),
                        () -> AgentBotMovementCommandRuntime.stop(entry),
                        (type, itemId, quantity) -> dropItem(entry, type, itemId, quantity)));
    }

    public static boolean isComplete(AgentRuntimeEntry entry, AgentTask task, int normalMoveArrivalDistance) {
        return AgentScriptTaskCompletionService.isComplete(
                entry,
                task,
                normalMoveArrivalDistance,
                targetResolver(entry));
    }

    private static IntFunction<Character> targetResolver(AgentRuntimeEntry entry) {
        return targetCharacterId ->
                AgentFollowAnchorService.resolveTargetFromRuntimeRegistry(entry, targetCharacterId);
    }

    private static boolean dropItem(AgentRuntimeEntry entry, InventoryType type, int itemId, short quantity) {
        return AgentScriptItemActionService.dropItem(entry, type, itemId, quantity);
    }

}
