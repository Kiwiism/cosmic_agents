package server.agents.integration;

import client.Character;
import server.agents.capabilities.trade.AgentTradeWindow;

public interface TradeGateway {
    void startTrade(Character agent);

    void inviteTrade(Character agent, Character recipient);

    void cancelNoResponse(Character agent);

    void completeTrade(Character agent);

    void visitTrade(Character agent, Character inviter);

    AgentTradeWindow currentWindow(Character agent);
}
