package server.agents.capabilities.dialogue;


import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.capabilities.combat.AgentCombatBuffStateRuntime;
import server.agents.capabilities.combat.AgentBuffStateRuntime;

import client.Character;
import server.agents.capabilities.combat.AgentBuffService;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.dialogue.AgentChatReportOperationsRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned control callbacks while reply delivery, scheduling, and live
 * identity lookup stay behind runtime/integration boundaries.
 */
public final class AgentControlRuntime {
    private AgentControlRuntime() {
    }

    public static AgentChatToggleFlow.ToggleCallbacks toggleCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatToggleFlow.ToggleCallbacks() {
            @Override
            public void setSupport(boolean enabled) {
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () -> {
                    AgentCombatBuffStateRuntime.setSkillBuffsEnabled(entry, enabled);
                    AgentReplyRuntime.replyNow(entry, AgentChatToggleFlow.supportReply(enabled));
                });
            }

            @Override
            public void setHeals(boolean enabled) {
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () -> {
                    AgentCombatBuffStateRuntime.setSupportHealsEnabled(entry, enabled);
                    AgentReplyRuntime.replyNow(entry, AgentChatToggleFlow.healsReply(enabled));
                });
            }

            @Override
            public void setBuffConsumables(boolean enabled) {
                if (rejectPartnerInventoryAutomation(entry)) return;
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () -> {
                    AgentBuffStateRuntime.setEnabled(entry, enabled);
                    AgentBuffStateRuntime.resetScan(entry);
                    AgentReplyRuntime.replyNow(entry, AgentChatToggleFlow.buffConsumablesReply(
                            enabled, AgentBuffStateRuntime.cheapMode(entry)));
                });
            }

            @Override
            public void setBuffConsumablesCheapMode(boolean cheapMode) {
                if (rejectPartnerInventoryAutomation(entry)) return;
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () -> {
                    AgentBuffStateRuntime.setCheapMode(entry, cheapMode);
                    AgentBuffStateRuntime.resetScan(entry);
                    AgentReplyRuntime.replyNow(entry, AgentChatToggleFlow.buffConsumablesModeReply(cheapMode));
                });
            }

            @Override
            public void setProactiveOffers(boolean enabled) {
                if (rejectPartnerInventoryAutomation(entry)) return;
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () -> {
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
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () -> {
                    String summary = AgentBuffService.getChatSummary(
                            AgentBuffStateRuntime.enabled(entry),
                            AgentBuffStateRuntime.cheapMode(entry),
                            bot(entry),
                            AgentInventoryGatewayRuntime.inventory());
                    AgentReplyRuntime.replyNow(entry, summary);
                });
            }

            @Override
            public void reportBuffDebug() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () ->
                        AgentChatReportOperationsRuntime.reportBuffDebug(entry, bot(entry)));
            }

            @Override
            public void reportSkillBuffDebug() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () ->
                        AgentChatReportOperationsRuntime.reportSkillBuffDebug(entry, bot(entry)));
            }
        };
    }

    public static AgentChatRespecFlow.RespecCallbacks respecCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatRespecFlow.RespecCallbacks() {
            @Override
            public void respecAp() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () ->
                        AgentReplyRuntime.replyNow(entry, AgentBuildService.respecAp(entry, bot(entry))));
            }

            @Override
            public void respecSp() {
                AgentSchedulerRuntime.afterRandomDelay(entry, 500, 700, () ->
                        AgentReplyRuntime.replyNow(entry, AgentBuildService.respecSp(entry, bot(entry))));
            }
        };
    }

    private static Character bot(AgentRuntimeEntry entry) {
        return AgentRuntimeIdentityRuntime.bot(entry);
    }

    private static boolean rejectPartnerInventoryAutomation(AgentRuntimeEntry entry) {
        if (!entry.isPartnerManaged()) {
            return false;
        }
        AgentReplyRuntime.replyNow(entry,
                "I leave consumables and inventory decisions to you while we're adventuring partners.");
        return true;
    }
}
