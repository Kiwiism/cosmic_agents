package server.bots;

import server.agents.capabilities.dialogue.AgentChatBuffQueryFlow;
import server.agents.capabilities.dialogue.AgentChatRespecFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;

final class BotChatControlRuntime {
    private BotChatControlRuntime() {
    }

    static AgentChatToggleFlow.ToggleCallbacks toggleCallbacks(BotEntry entry) {
        return new AgentChatToggleFlow.ToggleCallbacks() {
            @Override
            public void setSupport(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.skillBuffsEnabled = enabled;
                    BotManager.getInstance().botReply(entry, AgentChatToggleFlow.supportReply(enabled));
                });
            }

            @Override
            public void setHeals(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.supportHealsEnabled = enabled;
                    BotManager.getInstance().botReply(entry, AgentChatToggleFlow.healsReply(enabled));
                });
            }

            @Override
            public void setBuffConsumables(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.buffConsumablesEnabled = enabled;
                    entry.lastBuffScanMs = 0;
                    BotManager.getInstance().botReply(entry, AgentChatToggleFlow.buffConsumablesReply(
                            enabled, entry.buffCheapMode));
                });
            }

            @Override
            public void setBuffConsumablesCheapMode(boolean cheapMode) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.buffCheapMode = cheapMode;
                    entry.lastBuffScanMs = 0;
                    BotManager.getInstance().botReply(entry, AgentChatToggleFlow.buffConsumablesModeReply(cheapMode));
                });
            }

            @Override
            public void setProactiveOffers(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.proactiveUpgradeOffers = enabled;
                    BotManager.getInstance().botReply(entry, AgentChatToggleFlow.proactiveOffersReply(enabled));
                });
            }
        };
    }

    static AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks(BotEntry entry) {
        return new AgentChatBuffQueryFlow.BuffQueryCallbacks() {
            @Override
            public void reportBuffList() {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    String summary = BotBuffManager.getChatSummary(
                            entry.buffConsumablesEnabled, entry.buffCheapMode, entry.bot);
                    BotManager.getInstance().botReply(entry, summary);
                });
            }

            @Override
            public void reportBuffDebug() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotChatReportRuntime.reportBuffDebug(entry, entry.bot));
            }

            @Override
            public void reportSkillBuffDebug() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotChatReportRuntime.reportSkillBuffDebug(entry, entry.bot));
            }
        };
    }

    static AgentChatRespecFlow.RespecCallbacks respecCallbacks(BotEntry entry) {
        return new AgentChatRespecFlow.RespecCallbacks() {
            @Override
            public void respecAp() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotManager.getInstance().botReply(entry, BotBuildManager.respecAp(entry, entry.bot)));
            }

            @Override
            public void respecSp() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotManager.getInstance().botReply(entry, BotBuildManager.respecSp(entry, entry.bot)));
            }
        };
    }
}
