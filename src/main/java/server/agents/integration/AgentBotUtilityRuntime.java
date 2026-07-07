package server.agents.integration;

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
public final class AgentBotUtilityRuntime {
    private AgentBotUtilityRuntime() {
    }

    public static AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks(AgentRuntimeEntry entry) {
        return new AgentChatUtilityFlow.UtilityCallbacks() {
            @Override
            public void tradeInvite() {
                Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
                Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
                if (owner != null && bot.getTrade() == null && owner.getTrade() == null
                        && AgentBotPendingTradeStateRuntime.isIdle(entry)) {
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
                Character bot = AgentBotRuntimeIdentityRuntime.bot(entry);
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700,
                        () -> AgentShopService.requestSellTrashVisit(entry, bot));
            }

            @Override
            public void makeCrystals() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700,
                        () -> AgentMakerService.handleMakeCrystals(entry));
            }

            @Override
            public void disassembleTrash() {
                AgentBotSchedulerRuntime.afterRandomDelay(500, 700,
                        () -> AgentMakerService.handleDisassembleTrash(entry));
            }
        };
    }
}
