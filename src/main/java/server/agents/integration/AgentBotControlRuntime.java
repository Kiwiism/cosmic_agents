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
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    entry.setSkillBuffsEnabled(enabled);
                    AgentBotReplyRuntime.replyNow(entry, AgentChatToggleFlow.supportReply(enabled));
                });
            }

            @Override
            public void setHeals(boolean enabled) {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    entry.setSupportHealsEnabled(enabled);
                    AgentBotReplyRuntime.replyNow(entry, AgentChatToggleFlow.healsReply(enabled));
                });
            }

            @Override
            public void setBuffConsumables(boolean enabled) {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    entry.setBuffConsumablesEnabled(enabled);
                    entry.resetLastBuffScan();
                    AgentBotReplyRuntime.replyNow(entry, AgentChatToggleFlow.buffConsumablesReply(
                            enabled, entry.buffCheapMode()));
                });
            }

            @Override
            public void setBuffConsumablesCheapMode(boolean cheapMode) {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    entry.setBuffCheapMode(cheapMode);
                    entry.resetLastBuffScan();
                    AgentBotReplyRuntime.replyNow(entry, AgentChatToggleFlow.buffConsumablesModeReply(cheapMode));
                });
            }

            @Override
            public void setProactiveOffers(boolean enabled) {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    entry.setProactiveUpgradeOffers(enabled);
                    AgentBotReplyRuntime.replyNow(entry, AgentChatToggleFlow.proactiveOffersReply(enabled));
                });
            }
        };
    }

    public static AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks(BotEntry entry) {
        return new AgentChatBuffQueryFlow.BuffQueryCallbacks() {
            @Override
            public void reportBuffList() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    String summary = BotBuffManager.getChatSummary(
                            entry.buffConsumablesEnabled(), entry.buffCheapMode(), entry.bot());
                    AgentBotReplyRuntime.replyNow(entry, summary);
                });
            }

            @Override
            public void reportBuffDebug() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotControlReportRuntime.reportBuffDebug(entry));
            }

            @Override
            public void reportSkillBuffDebug() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotControlReportRuntime.reportSkillBuffDebug(entry));
            }
        };
    }

    public static AgentChatRespecFlow.RespecCallbacks respecCallbacks(BotEntry entry) {
        return new AgentChatRespecFlow.RespecCallbacks() {
            @Override
            public void respecAp() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotReplyRuntime.replyNow(entry, BotBuildManager.respecAp(entry, entry.bot())));
            }

            @Override
            public void respecSp() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotReplyRuntime.replyNow(entry, BotBuildManager.respecSp(entry, entry.bot())));
            }
        };
    }
}
