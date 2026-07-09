package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;

import java.util.List;

public interface AgentTradeWindow {
    Object identity();

    Character character();

    AgentTradeWindow partner();

    boolean isFullTrade();

    boolean isPartnerConfirmed();

    int number();

    void chat(String message);

    boolean addItem(Item item);

    void setMeso(int meso);

    List<Item> items();

    boolean hasAnyOffer();
}
