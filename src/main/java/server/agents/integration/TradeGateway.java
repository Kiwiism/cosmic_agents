package server.agents.integration;

import client.Character;

public interface TradeGateway {
    void startTrade(Character agent);

    void inviteTrade(Character agent, Character recipient);

    void cancelNoResponse(Character agent);

    void completeTrade(Character agent);

    void visitTrade(Character agent, Character inviter);
}
