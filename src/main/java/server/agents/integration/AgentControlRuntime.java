package server.agents.integration;


import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.capabilities.combat.AgentCombatBuffStateRuntime;
import server.agents.capabilities.combat.AgentBuffStateRuntime;

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
public final class AgentControlRuntime {
    private AgentControlRuntime() {
    }

    public static AgentChatToggleFlow.ToggleCallbacks toggleCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatToggleFlow.ToggleCallbacks() {
            @Override
            public void setSupport(boolean enabled) {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentCombatBuffStateRuntime.setSkillBuffsEnabled(entry, enabled);
                    AgentReplyRuntime.replyNow(entry, AgentChatToggleFlow.supportReply(enabled));
                });
            }

            @Override
            public void setHeals(boolean enabled) {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentCombatBuffStateRuntime.setSupportHealsEnabled(entry, enabled);
                    AgentReplyRuntime.replyNow(entry, AgentChatToggleFlow.healsReply(enabled));
                });
            }

            @Override
            public void setBuffConsumables(boolean enabled) {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBuffStateRuntime.setEnabled(entry, enabled);
                    AgentBuffStateRuntime.resetScan(entry);
                    AgentReplyRuntime.replyNow(entry, AgentChatToggleFlow.buffConsumablesReply(
                            enabled, AgentBuffStateRuntime.cheapMode(entry)));
                });
            }

            @Override
            public void setBuffConsumablesCheapMode(boolean cheapMode) {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    AgentBuffStateRuntime.setCheapMode(entry, cheapMode);
                    AgentBuffStateRuntime.resetScan(entry);
                    AgentReplyRuntime.replyNow(entry, AgentChatToggleFlow.buffConsumablesModeReply(cheapMode));
                });
            }

            @Override
            public void setProactiveOffers(boolean enabled) {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    entry.upgradeOfferState().setProactiveUpgradeOffers(enabled);
                    AgentReplyRuntime.replyNow(entry, AgentChatToggleFlow.proactiveOffersReply(enabled));
                });
            }
        };
    }

    public static AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatBuffQueryFlow.BuffQueryCallbacks() {
            @Override
            public void reportBuffList() {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () -> {
                    String summary = AgentBuffService.getChatSummary(
                            AgentBuffStateRuntime.enabled(entry),
                            AgentBuffStateRuntime.cheapMode(entry),
                            bot(entry));
                    AgentReplyRuntime.replyNow(entry, summary);
                });
            }

            @Override
            public void reportBuffDebug() {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentChatReportRuntime.reportBuffDebug(entry, bot(entry)));
            }

            @Override
            public void reportSkillBuffDebug() {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentChatReportRuntime.reportSkillBuffDebug(entry, bot(entry)));
            }
        };
    }

    public static AgentChatRespecFlow.RespecCallbacks respecCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatRespecFlow.RespecCallbacks() {
            @Override
            public void respecAp() {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentReplyRuntime.replyNow(entry, AgentBuildService.respecAp(entry, bot(entry))));
            }

            @Override
            public void respecSp() {
                AgentSchedulerRuntime.afterRandomDelay(500, 700, () ->
                        AgentReplyRuntime.replyNow(entry, AgentBuildService.respecSp(entry, bot(entry))));
            }
        };
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.bot(entry);
    }
}
