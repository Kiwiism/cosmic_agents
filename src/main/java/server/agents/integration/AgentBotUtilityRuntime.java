package server.agents.integration;

import client.Character;
import server.Trade;
import server.agents.capabilities.dialogue.AgentChatUtilityFlow;
import server.bots.BotEntry;
import server.agents.capabilities.build.AgentMakerService;
import server.agents.capabilities.shop.AgentShopService;

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
                Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
                Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
                if (owner != null && bot.getTrade() == null && owner.getTrade() == null
                        && AgentBotPendingTradeStateRuntime.isIdle(entry)) {
                    AgentBotUtilitySchedulerRuntime.afterRandomDelay(600, 1000, () -> {
                        AgentBotUtilityReplyRuntime.replyNow(entry, AgentChatUtilityFlow.tradeInviteReply());
                        AgentBotUtilitySchedulerRuntime.afterRandomDelay(800, 1200, () -> {
                            Trade.startTrade(bot);
                            Trade.inviteTrade(bot, owner);
                        });
                    });
                }
            }

            @Override
            public void sellTrash() {
                Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
                AgentBotUtilitySchedulerRuntime.afterRandomDelay(500, 700,
                        () -> AgentShopService.requestSellTrashVisit(entry, bot));
            }

            @Override
            public void makeCrystals() {
                AgentBotUtilitySchedulerRuntime.afterRandomDelay(500, 700,
                        () -> AgentMakerService.handleMakeCrystals(entry));
            }

            @Override
            public void disassembleTrash() {
                AgentBotUtilitySchedulerRuntime.afterRandomDelay(500, 700,
                        () -> AgentMakerService.handleDisassembleTrash(entry));
            }
        };
    }
}
