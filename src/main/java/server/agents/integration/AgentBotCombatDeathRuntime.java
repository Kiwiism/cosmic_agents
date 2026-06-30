package server.agents.integration;

import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.bots.BotEntry;
import server.bots.BotManager;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;

public final class AgentBotCombatDeathRuntime {
    private AgentBotCombatDeathRuntime() {
    }

    public static void enterDeadState(BotEntry entry, Character bot,
                                      boolean announceDeath,
                                      AgentCombatConfig.Config config) {
        AgentBotCombatActionStateRuntime.clearActionState(entry);
        BotPhysicsEngine.markDead(entry, bot);
        BotMovementManager.broadcastMovement(entry);
        AgentBotDeathStateRuntime.enterDeadState(entry, System.currentTimeMillis(), config.BOT_DEAD_MS);
        if (announceDeath) {
            AgentBotCombatRuntime.sayMapNow(bot, BotManager.randomReply(AgentDialogueCatalog.combatDeathReplies()));
        }
    }
}
