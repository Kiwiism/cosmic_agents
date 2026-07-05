package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatBuffQueryFlow;
import server.agents.capabilities.dialogue.AgentChatRespecFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;
import server.agents.capabilities.combat.AgentBuffService;
import server.agents.capabilities.build.AgentBuildService;
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
                    AgentBotCombatBuffStateRuntime.setSkillBuffsEnabled(entry, enabled);
                    AgentBotControlReplyRuntime.replyNow(entry, AgentChatToggleFlow.supportReply(enabled));
                });
            }

            @Override
            public void setHeals(boolean enabled) {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBotCombatBuffStateRuntime.setSupportHealsEnabled(entry, enabled);
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
                    entry.upgradeOfferState().setProactiveUpgradeOffers(enabled);
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
                    String summary = AgentBuffService.getChatSummary(
                            AgentBotBuffStateRuntime.enabled(entry),
                            AgentBotBuffStateRuntime.cheapMode(entry),
                            bot(entry));
                    AgentBotControlReplyRuntime.replyNow(entry, summary);
                });
            }

            @Override
            public void reportBuffDebug() {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotChatReportRuntime.reportBuffDebug(entry, bot(entry)));
            }

            @Override
            public void reportSkillBuffDebug() {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotChatReportRuntime.reportSkillBuffDebug(entry, bot(entry)));
            }
        };
    }

    public static AgentChatRespecFlow.RespecCallbacks respecCallbacks(BotEntry entry) {
        return new AgentChatRespecFlow.RespecCallbacks() {
            @Override
            public void respecAp() {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotControlReplyRuntime.replyNow(entry, AgentBuildService.respecAp(entry, bot(entry))));
            }

            @Override
            public void respecSp() {
                AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotControlReplyRuntime.replyNow(entry, AgentBuildService.respecSp(entry, bot(entry))));
            }
        };
    }

    private static Character bot(BotEntry entry) {
        return AgentBotRuntimeIdentityRuntime.bot(entry);
    }
}
