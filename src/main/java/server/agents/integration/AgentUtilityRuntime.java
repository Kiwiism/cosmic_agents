package server.agents.integration;

import server.agents.capabilities.trade.AgentPendingTradeStateRuntime;

import client.Character;
import server.Trade;
import server.agents.capabilities.dialogue.AgentChatUtilityFlow;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.build.AgentMakerService;
import server.agents.capabilities.shop.AgentShopService;

/**
 * Agent-owned utility chat callback facade over temporary bot-side trade,
 * shop, and maker side effects.
 */
public final class AgentUtilityRuntime {
    private AgentUtilityRuntime() {
    }

    public static AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatUtilityFlow.UtilityCallbacks() {
            @Override
            public void tradeInvite() {
                Character bot = AgentRuntimeIdentityRuntime.bot(entry);
                Character owner = AgentRuntimeIdentityRuntime.owner(entry);
                if (owner != null && bot.getTrade() == null && owner.getTrade() == null
                        && AgentPendingTradeStateRuntime.isIdle(entry)) {
                    AgentSchedulerRuntime.afterRandomDelay(600, 1000, () -> {
                        AgentReplyRuntime.replyNow(entry, AgentChatUtilityFlow.tradeInviteReply());
                        AgentSchedulerRuntime.afterRandomDelay(800, 1200, () -> {
                            Trade.startTrade(bot);
                            Trade.inviteTrade(bot, owner);
                        });
                    });
                }
            }

            @Override
            public void sellTrash() {
                Character bot = AgentRuntimeIdentityRuntime.bot(entry);
                AgentSchedulerRuntime.afterRandomDelay(500, 700,
                        () -> AgentShopService.requestSellTrashVisit(entry, bot));
            }

            @Override
            public void makeCrystals() {
                AgentSchedulerRuntime.afterRandomDelay(500, 700,
                        () -> AgentMakerService.handleMakeCrystals(entry));
            }

            @Override
            public void disassembleTrash() {
                AgentSchedulerRuntime.afterRandomDelay(500, 700,
                        () -> AgentMakerService.handleDisassembleTrash(entry));
            }
        };
    }
}
