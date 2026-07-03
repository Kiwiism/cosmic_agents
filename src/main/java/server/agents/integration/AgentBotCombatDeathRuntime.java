package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

public final class AgentBotCombatDeathRuntime {
    private AgentBotCombatDeathRuntime() {
    }

    public static void enterDeadState(BotEntry entry, Character bot,
                                      boolean announceDeath,
                                      AgentCombatConfig.Config config) {
        AgentBotCombatActionStateRuntime.clearActionState(entry);
        BotPhysicsEngine.markDead(entry, bot);
        AgentMovementBroadcastService.broadcastMovement(entry);
        AgentBotDeathStateRuntime.enterDeadState(entry, System.currentTimeMillis(), config.BOT_DEAD_MS);
        if (announceDeath) {
            AgentBotCombatRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.combatDeathReplies()));
        }
    }
}
