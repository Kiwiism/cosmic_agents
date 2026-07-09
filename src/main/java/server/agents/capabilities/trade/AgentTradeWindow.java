package server.agents.capabilities.trade;

import client.Character;

public interface AgentTradeWindow {
    Object identity();

    Character character();

    AgentTradeWindow partner();

    boolean isFullTrade();

    boolean isPartnerConfirmed();

    int number();

    void chat(String message);
}
