package server.agents.plans;

import client.Character;
import server.agents.integration.AgentScriptMoveTargetRuntime;
import server.agents.integration.AgentScriptTaskStateRuntime;
import server.agents.runtime.AgentScriptTaskQueueService;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

public final class AgentScriptRunner {
    private AgentScriptRunner() {}

    public static void tick(AgentRuntimeEntry entry, Character bot, Character owner, List<AgentScript> scripts) {
        AgentScript script = findScript(entry, bot, owner, scripts);
        if (script == null) {
            if (AgentScriptTaskStateRuntime.hasScriptId(entry)) {
                AgentScriptTaskQueueService.clearTasks(entry);
                AgentScriptTaskStateRuntime.resetScript(entry, null);
            }
            return;
        }

        if (!script.id().equals(AgentScriptTaskStateRuntime.scriptId(entry))) {
            AgentScriptTaskQueueService.clearTasks(entry);
            AgentScriptTaskStateRuntime.resetScript(entry, script.id());
        }

        List<AgentScriptStep> steps = script.steps();
        if (AgentScriptTaskStateRuntime.scriptStepIndex(entry) >= steps.size()) {
            return;
        }

        AgentScriptContext ctx = new AgentScriptContext(
                entry,
                bot,
                owner,
                AgentScriptMoveTargetRuntime::isCheapMoveTarget,
                AgentScriptItemActionService::dropItem);
        AgentScriptStep step = steps.get(AgentScriptTaskStateRuntime.scriptStepIndex(entry));
        if (!AgentScriptTaskStateRuntime.scriptStepEntered(entry)) {
            step.enter(ctx);
            AgentScriptTaskStateRuntime.markScriptStepEntered(entry);
        }
        step.tick(ctx);
        if (step.complete(ctx)) {
            AgentScriptTaskStateRuntime.advanceScriptStep(entry);
        }
    }

    private static AgentScript findScript(AgentRuntimeEntry entry, Character bot, Character owner, List<AgentScript> scripts) {
        for (AgentScript script : scripts) {
            if (script.applies(entry, bot, owner)) {
                return script;
            }
        }
        return null;
    }
}
