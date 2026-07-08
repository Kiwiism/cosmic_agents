package server.agents.capabilities.social;


import server.agents.runtime.AgentSchedulerRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatSocialFlow;
import server.agents.capabilities.dialogue.AgentFameDialogueFlow;
import server.agents.capabilities.dialogue.AgentSocialDialogueClassifier;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned social chat callback facade. Live identity and reply delivery
 * remain integration seams.
 */
public final class AgentSocialRuntime {
    private AgentSocialRuntime() {
    }

    public static AgentChatSocialFlow.SocialCallbacks socialCallbacks(AgentRuntimeEntry entry) {
        return targetName -> AgentSchedulerRuntime.afterRandomDelay(
                500, 900, () -> handleFameCommand(entry, targetName));
    }

    public static void handleFameCommand(AgentRuntimeEntry entry, String targetName) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        Character target;
        if (AgentSocialDialogueClassifier.isSelfFameTarget(targetName)) {
            target = AgentRuntimeIdentityRuntime.owner(entry);
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
                AgentReplyRuntime.replyNow(entry, message);
            }
        });
    }
}
