package server.agents.plans;

import client.Character;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.bots.BotEntry;
import server.bots.BotManager;

import java.util.List;

public final class AgentScriptRunner {
    private AgentScriptRunner() {}

    public static void tick(BotEntry entry, Character bot, Character owner, List<AgentScript> scripts) {
        AgentScript script = findScript(entry, bot, owner, scripts);
        if (script == null) {
            if (AgentBotScriptTaskStateRuntime.hasScriptId(entry)) {
                BotManager.getInstance().clearScriptTasks(entry);
                AgentBotScriptTaskStateRuntime.resetScript(entry, null);
            }
            return;
        }

        if (!script.id().equals(AgentBotScriptTaskStateRuntime.scriptId(entry))) {
            BotManager.getInstance().clearScriptTasks(entry);
            AgentBotScriptTaskStateRuntime.resetScript(entry, script.id());
        }

        List<AgentScriptStep> steps = script.steps();
        if (AgentBotScriptTaskStateRuntime.scriptStepIndex(entry) >= steps.size()) {
            return;
        }

        AgentScriptContext ctx = new AgentScriptContext(entry, bot, owner, BotManager.getInstance());
        AgentScriptStep step = steps.get(AgentBotScriptTaskStateRuntime.scriptStepIndex(entry));
        if (!AgentBotScriptTaskStateRuntime.scriptStepEntered(entry)) {
            step.enter(ctx);
            AgentBotScriptTaskStateRuntime.markScriptStepEntered(entry);
        }
        step.tick(ctx);
        if (step.complete(ctx)) {
            AgentBotScriptTaskStateRuntime.advanceScriptStep(entry);
        }
    }

    private static AgentScript findScript(BotEntry entry, Character bot, Character owner, List<AgentScript> scripts) {
        for (AgentScript script : scripts) {
            if (script.applies(entry, bot, owner)) {
                return script;
            }
        }
        return null;
    }
}
