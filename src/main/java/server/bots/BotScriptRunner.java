package server.bots;

import client.Character;

import java.util.List;

public final class BotScriptRunner {
    private BotScriptRunner() {}

    public static void tick(BotEntry entry, Character bot, Character owner, List<BotScript> scripts) {
        BotScript script = findScript(entry, bot, owner, scripts);
        if (script == null) {
            if (entry.script.scriptId != null) {
                BotManager.getInstance().clearScriptTasks(entry);
                entry.script.reset(null);
            }
            return;
        }

        if (!script.id().equals(entry.script.scriptId)) {
            BotManager.getInstance().clearScriptTasks(entry);
            entry.script.reset(script.id());
        }

        List<BotScriptStep> steps = script.steps();
        if (entry.script.stepIndex >= steps.size()) {
            return;
        }

        BotScriptContext ctx = new BotScriptContext(entry, bot, owner, BotManager.getInstance());
        BotScriptStep step = steps.get(entry.script.stepIndex);
        if (!entry.script.stepEntered) {
            step.enter(ctx);
            entry.script.stepEntered = true;
        }
        step.tick(ctx);
        if (step.complete(ctx)) {
            entry.script.stepIndex++;
            entry.script.stepEntered = false;
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
