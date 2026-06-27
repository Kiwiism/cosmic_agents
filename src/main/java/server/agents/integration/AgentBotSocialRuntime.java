package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatSocialFlow;
import server.agents.capabilities.dialogue.AgentFameDialogueFlow;
import server.agents.capabilities.dialogue.AgentSocialDialogueClassifier;
import server.bots.BotEntry;

/**
 * Agent-owned social chat callback facade over temporary bot-side fame side
 * effects.
 */
public final class AgentBotSocialRuntime {
    private AgentBotSocialRuntime() {
    }

    public static AgentChatSocialFlow.SocialCallbacks socialCallbacks(BotEntry entry) {
        return targetName -> AgentBotSchedulerRuntime.afterRandomDelay(
                500, 900, () -> handleFameCommand(entry, targetName));
    }

    public static void handleFameCommand(BotEntry entry, String targetName) {
        Character bot = entry.bot();
        Character target;
        if (AgentSocialDialogueClassifier.isSelfFameTarget(targetName)) {
            target = entry.owner();
        } else {
            target = bot.getMap().getCharacters().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(targetName))
                    .findFirst().orElse(null);
        }
        Character fameTarget = target;
        AgentFameDialogueFlow.handle(targetName, new AgentFameDialogueFlow.FameCallbacks() {
            @Override
            public boolean targetExists() {
                return fameTarget != null;
            }

            @Override
            public boolean targetIsSelf() {
                return fameTarget != null && fameTarget.getId() == bot.getId();
            }

            @Override
            public int agentLevel() {
                return bot.getLevel();
            }

            @Override
            public Character.FameStatus fameStatus() {
                return bot.canGiveFame(fameTarget);
            }

            @Override
            public boolean gainFame() {
                return fameTarget.gainFame(1, bot, 1);
            }

            @Override
            public void markFameGiven() {
                bot.hasGivenFame(fameTarget);
            }

            @Override
            public String targetDisplayName() {
                return fameTarget.getName();
            }

            @Override
            public void reply(String message) {
                AgentBotReplyRuntime.replyNow(entry, message);
            }
        });
    }
}
