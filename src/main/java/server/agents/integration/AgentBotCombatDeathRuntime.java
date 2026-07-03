package server.agents.integration;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.bots.BotEntry;

public final class AgentBotCombatDeathRuntime {
    private AgentBotCombatDeathRuntime() {
    }

    public static void enterDeadState(BotEntry entry, Character bot,
                                      boolean announceDeath,
                                      AgentCombatConfig.Config config) {
        AgentBotCombatActionStateRuntime.clearActionState(entry);
        AgentMovementPoseService.markDead(entry, bot);
        AgentMovementBroadcastService.broadcastMovement(entry);
        AgentBotDeathStateRuntime.enterDeadState(entry, System.currentTimeMillis(), config.BOT_DEAD_MS);
        if (announceDeath) {
            AgentBotCombatRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.combatDeathReplies()));
        }
    }
}
