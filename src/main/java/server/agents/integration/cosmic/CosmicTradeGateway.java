package server.agents.integration.cosmic;

import client.Character;
import server.Trade;
import server.agents.integration.TradeGateway;

public enum CosmicTradeGateway implements TradeGateway {
    INSTANCE;

    @Override
    public void startTrade(Character agent) {
        Trade.startTrade(agent);
    }

    @Override
    public void inviteTrade(Character agent, Character recipient) {
        Trade.inviteTrade(agent, recipient);
    }

    @Override
    public void cancelNoResponse(Character agent) {
        Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE);
    }

    @Override
    public void completeTrade(Character agent) {
        Trade.completeTrade(agent);
    }

    @Override
    public void visitTrade(Character agent, Character inviter) {
        Trade.visitTrade(agent, inviter);
    }
}
