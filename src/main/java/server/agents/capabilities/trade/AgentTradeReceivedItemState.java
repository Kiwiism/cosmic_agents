package server.agents.capabilities.trade;

import client.inventory.Item;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Items received during the active trade, regardless of who sent them. */
public final class AgentTradeReceivedItemState {
    private final Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());

    public Set<Item> items() { return items; }
    public boolean hasItems() { return !items.isEmpty(); }
    public void add(Item item) { items.add(item); }
    public void clear() { items.clear(); }
}
