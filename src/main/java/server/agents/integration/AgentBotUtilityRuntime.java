package server.agents.integration;

import client.Character;
import server.Trade;
import server.agents.capabilities.dialogue.AgentChatUtilityFlow;
import server.bots.BotEntry;
import server.bots.BotMakerManager;
import server.bots.BotShopManager;

/**
 * Agent-owned utility chat callback facade over temporary bot-side trade,
 * shop, and maker side effects.
 */
public final class AgentBotUtilityRuntime {
    private AgentBotUtilityRuntime() {
    }

    public static AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks(BotEntry entry) {
        return new AgentChatUtilityFlow.UtilityCallbacks() {
            @Override
            public void tradeInvite() {
                Character bot = entry.bot();
                Character owner = entry.owner();
                if (owner != null && bot.getTrade() == null && owner.getTrade() == null
                        && entry.pendingTradeCategory() == null) {
                    AgentBotSchedulerRuntime.afterRandomDelay(600, 1000, () -> {
                        AgentBotReplyRuntime.replyNow(entry, AgentChatUtilityFlow.tradeInviteReply());
                        AgentBotSchedulerRuntime.afterRandomDelay(800, 1200, () -> {
                            Trade.startTrade(bot);
                            Trade.inviteTrade(bot, owner);
                        });
                    });
                }
            }

            @Override
            public void sellTrash() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700,
                        () -> BotShopManager.requestSellTrashVisit(entry, entry.bot()));
            }

            @Override
            public void makeCrystals() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700,
                        () -> BotMakerManager.handleMakeCrystals(entry));
            }

            @Override
            public void disassembleTrash() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700,
                        () -> BotMakerManager.handleDisassembleTrash(entry));
            }
        };
    }
}
