package server.agents.integration;

import server.agents.capabilities.dialogue.AgentChatBuffQueryFlow;
import server.agents.capabilities.dialogue.AgentChatRespecFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;
import server.bots.BotBuffManager;
import server.bots.BotBuildManager;
import server.bots.BotEntry;

/**
 * Temporary Agent-owned bridge for control callbacks while toggle/respec side
 * effects still write into the bot runtime entry.
 */
public final class AgentBotControlRuntime {
    private AgentBotControlRuntime() {
    }

    public static AgentChatToggleFlow.ToggleCallbacks toggleCallbacks(BotEntry entry) {
        return new AgentChatToggleFlow.ToggleCallbacks() {
            @Override
            public void setSupport(boolean enabled) {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    entry.setSkillBuffsEnabled(enabled);
                    AgentBotControlReplyRuntime.replyNow(entry, AgentChatToggleFlow.supportReply(enabled));
                });
            }

            @Override
            public void setHeals(boolean enabled) {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    entry.setSupportHealsEnabled(enabled);
                    AgentBotControlReplyRuntime.replyNow(entry, AgentChatToggleFlow.healsReply(enabled));
                });
            }

            @Override
            public void setBuffConsumables(boolean enabled) {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBotBuffStateRuntime.setEnabled(entry, enabled);
                    AgentBotBuffStateRuntime.resetScan(entry);
                    AgentBotControlReplyRuntime.replyNow(entry, AgentChatToggleFlow.buffConsumablesReply(
                            enabled, AgentBotBuffStateRuntime.cheapMode(entry)));
                });
            }

            @Override
            public void setBuffConsumablesCheapMode(boolean cheapMode) {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBotBuffStateRuntime.setCheapMode(entry, cheapMode);
                    AgentBotBuffStateRuntime.resetScan(entry);
                    AgentBotControlReplyRuntime.replyNow(entry, AgentChatToggleFlow.buffConsumablesModeReply(cheapMode));
                });
            }

            @Override
            public void setProactiveOffers(boolean enabled) {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    entry.setProactiveUpgradeOffers(enabled);
                    AgentBotControlReplyRuntime.replyNow(entry, AgentChatToggleFlow.proactiveOffersReply(enabled));
                });
            }
        };
    }

    public static AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks(BotEntry entry) {
        return new AgentChatBuffQueryFlow.BuffQueryCallbacks() {
            @Override
            public void reportBuffList() {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    String summary = BotBuffManager.getChatSummary(
                            AgentBotBuffStateRuntime.enabled(entry), AgentBotBuffStateRuntime.cheapMode(entry), entry.bot());
                    AgentBotControlReplyRuntime.replyNow(entry, summary);
                });
            }

            @Override
            public void reportBuffDebug() {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotControlReportRuntime.reportBuffDebug(entry));
            }

            @Override
            public void reportSkillBuffDebug() {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotControlReportRuntime.reportSkillBuffDebug(entry));
            }
        };
    }

    public static AgentChatRespecFlow.RespecCallbacks respecCallbacks(BotEntry entry) {
        return new AgentChatRespecFlow.RespecCallbacks() {
            @Override
            public void respecAp() {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotControlReplyRuntime.replyNow(entry, BotBuildManager.respecAp(entry, entry.bot())));
            }

            @Override
            public void respecSp() {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotControlReplyRuntime.replyNow(entry, BotBuildManager.respecSp(entry, entry.bot())));
            }
        };
    }
}
