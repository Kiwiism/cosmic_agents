package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatBuffQueryFlow;
import server.agents.capabilities.dialogue.AgentChatRespecFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;
import server.agents.capabilities.combat.AgentBuffService;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Temporary Agent-owned bridge for control callbacks while toggle/respec side
 * effects still write into the live Agent runtime entry.
 */
public final class AgentBotControlRuntime {
    private AgentBotControlRuntime() {
    }

    public static AgentChatToggleFlow.ToggleCallbacks toggleCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatToggleFlow.ToggleCallbacks() {
            @Override
            public void setSupport(boolean enabled) {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBotCombatBuffStateRuntime.setSkillBuffsEnabled(entry, enabled);
                    AgentBotReplyRuntime.replyNow(entry, AgentChatToggleFlow.supportReply(enabled));
                });
            }

            @Override
            public void setHeals(boolean enabled) {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBotCombatBuffStateRuntime.setSupportHealsEnabled(entry, enabled);
                    AgentBotReplyRuntime.replyNow(entry, AgentChatToggleFlow.healsReply(enabled));
                });
            }

            @Override
            public void setBuffConsumables(boolean enabled) {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBotBuffStateRuntime.setEnabled(entry, enabled);
                    AgentBotBuffStateRuntime.resetScan(entry);
                    AgentBotReplyRuntime.replyNow(entry, AgentChatToggleFlow.buffConsumablesReply(
                            enabled, AgentBotBuffStateRuntime.cheapMode(entry)));
                });
            }

            @Override
            public void setBuffConsumablesCheapMode(boolean cheapMode) {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBotBuffStateRuntime.setCheapMode(entry, cheapMode);
                    AgentBotBuffStateRuntime.resetScan(entry);
                    AgentBotReplyRuntime.replyNow(entry, AgentChatToggleFlow.buffConsumablesModeReply(cheapMode));
                });
            }

            @Override
            public void setProactiveOffers(boolean enabled) {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    entry.upgradeOfferState().setProactiveUpgradeOffers(enabled);
                    AgentBotReplyRuntime.replyNow(entry, AgentChatToggleFlow.proactiveOffersReply(enabled));
                });
            }
        };
    }

    public static AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatBuffQueryFlow.BuffQueryCallbacks() {
            @Override
            public void reportBuffList() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    String summary = AgentBuffService.getChatSummary(
                            AgentBotBuffStateRuntime.enabled(entry),
                            AgentBotBuffStateRuntime.cheapMode(entry),
                            bot(entry));
                    AgentBotReplyRuntime.replyNow(entry, summary);
                });
            }

            @Override
            public void reportBuffDebug() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotChatReportRuntime.reportBuffDebug(entry, bot(entry)));
            }

            @Override
            public void reportSkillBuffDebug() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotChatReportRuntime.reportSkillBuffDebug(entry, bot(entry)));
            }
        };
    }

    public static AgentChatRespecFlow.RespecCallbacks respecCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatRespecFlow.RespecCallbacks() {
            @Override
            public void respecAp() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotReplyRuntime.replyNow(entry, AgentBuildService.respecAp(entry, bot(entry))));
            }

            @Override
            public void respecSp() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentBotReplyRuntime.replyNow(entry, AgentBuildService.respecSp(entry, bot(entry))));
            }
        };
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentBotRuntimeIdentityRuntime.bot(entry);
    }
}
