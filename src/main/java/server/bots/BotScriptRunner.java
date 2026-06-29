package server.bots;

import client.Character;
import server.agents.integration.AgentBotScriptTaskStateRuntime;

import java.util.List;

public final class BotScriptRunner {
    private BotScriptRunner() {}

    public static void tick(BotEntry entry, Character bot, Character owner, List<BotScript> scripts) {
        BotScript script = findScript(entry, bot, owner, scripts);
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

        List<BotScriptStep> steps = script.steps();
        if (AgentBotScriptTaskStateRuntime.scriptStepIndex(entry) >= steps.size()) {
            return;
        }

        BotScriptContext ctx = new BotScriptContext(entry, bot, owner, BotManager.getInstance());
        BotScriptStep step = steps.get(AgentBotScriptTaskStateRuntime.scriptStepIndex(entry));
        if (!AgentBotScriptTaskStateRuntime.scriptStepEntered(entry)) {
            step.enter(ctx);
            AgentBotScriptTaskStateRuntime.markScriptStepEntered(entry);
        }
        step.tick(ctx);
        if (step.complete(ctx)) {
            AgentBotScriptTaskStateRuntime.advanceScriptStep(entry);
        }
    }

    private static BotScript findScript(BotEntry entry, Character bot, Character owner, List<BotScript> scripts) {
        for (BotScript script : scripts) {
            if (script.applies(entry, bot, owner)) {
                return script;
            }
        }
        return null;
    }
}
