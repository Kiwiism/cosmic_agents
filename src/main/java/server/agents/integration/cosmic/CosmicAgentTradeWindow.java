package server.agents.integration.cosmic;

import client.Character;
import client.inventory.Item;
import server.Trade;
import server.agents.capabilities.trade.AgentTradeWindow;

import java.util.List;

public final class CosmicAgentTradeWindow implements AgentTradeWindow {
    private final Trade trade;

    private CosmicAgentTradeWindow(Trade trade) {
        this.trade = trade;
    }

    public static AgentTradeWindow wrap(Trade trade) {
        return trade == null ? null : new CosmicAgentTradeWindow(trade);
    }

    @Override
    public Object identity() {
        return trade;
    }

    @Override
    public Character character() {
        return trade.getChr();
    }

    @Override
    public AgentTradeWindow partner() {
        return wrap(trade.getPartner());
    }

    @Override
    public boolean isFullTrade() {
        return trade.isFullTrade();
    }

    @Override
    public boolean isPartnerConfirmed() {
        return trade.isPartnerConfirmed();
    }

    @Override
    public int number() {
        return trade.getNumber();
    }

    @Override
    public void chat(String message) {
        trade.chat(message);
    }

    @Override
    public boolean addItem(Item item) {
        return trade.addItem(item);
    }

    @Override
    public void setMeso(int meso) {
        trade.setMeso(meso);
    }

    @Override
    public List<Item> items() {
        return trade.getItems();
    }

    @Override
    public boolean hasAnyOffer() {
        return trade.hasAnyOffer();
    }
}
