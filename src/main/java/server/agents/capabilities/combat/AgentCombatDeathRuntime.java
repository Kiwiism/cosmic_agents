package server.agents.capabilities.combat;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import client.Character;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.runtime.AgentDeathStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentCombatDeathRuntime {
    private AgentCombatDeathRuntime() {
    }

    public static void enterDeadState(AgentRuntimeEntry entry, Character bot,
                                      boolean announceDeath,
                                      AgentCombatConfig.Config config) {
        AgentCombatActionStateRuntime.clearActionState(entry);
        AgentMovementPoseService.markDead(entry, bot);
        AgentMovementBroadcastService.broadcastMovement(entry);
        AgentDeathStateRuntime.enterDeadState(entry, System.currentTimeMillis(), config.BOT_DEAD_MS);
        if (announceDeath) {
            AgentCombatRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.combatDeathReplies()));
        }
    }
}
