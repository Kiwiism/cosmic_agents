package server.agents.capabilities.trade;

import client.inventory.Item;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Deprecated(forRemoval = true)
public final class AgentOwnerGivenTradeItemState {
    private final Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());

    public Set<Item> items() {
        return items;
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }

    public void add(Item item) {
        items.add(item);
    }

    public void clear() {
        items.clear();
    }
}
