package server.bots;

import client.Character;
import server.Trade;
import server.agents.capabilities.dialogue.AgentChatUtilityFlow;

final class BotChatUtilityRuntime {
    private BotChatUtilityRuntime() {
    }

    static AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks(BotEntry entry) {
        return new AgentChatUtilityFlow.UtilityCallbacks() {
            @Override
            public void tradeInvite() {
                Character bot = entry.bot;
                Character owner = entry.owner;
                if (owner != null && bot.getTrade() == null && owner.getTrade() == null
                        && entry.pendingTradeCategory == null) {
                    BotManager.after(BotManager.randMs(600, 1000), () -> {
                        BotManager.getInstance().botReply(entry, AgentChatUtilityFlow.tradeInviteReply());
                        BotManager.after(BotManager.randMs(800, 1200), () -> {
                            Trade.startTrade(bot);
                            Trade.inviteTrade(bot, owner);
                        });
                    });
                }
            }

            @Override
            public void sellTrash() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotShopManager.requestSellTrashVisit(entry, entry.bot));
            }

            @Override
            public void makeCrystals() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotMakerManager.handleMakeCrystals(entry));
            }

            @Override
            public void disassembleTrash() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotMakerManager.handleDisassembleTrash(entry));
            }
        };
    }
}
