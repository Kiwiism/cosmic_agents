package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.Trade;

import java.util.List;

public final class AgentServerTradeWindow implements AgentTradeWindow {
    private final Trade trade;

    private AgentServerTradeWindow(Trade trade) {
        this.trade = trade;
    }

    public static AgentTradeWindow wrap(Trade trade) {
        return trade == null ? null : new AgentServerTradeWindow(trade);
    }

    public static Trade unwrap(AgentTradeWindow window) {
        if (window instanceof AgentServerTradeWindow serverWindow) {
            return serverWindow.trade;
        }
        return null;
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
