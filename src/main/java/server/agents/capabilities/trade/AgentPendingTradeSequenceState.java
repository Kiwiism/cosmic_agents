package server.agents.capabilities.trade;

import client.inventory.Item;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class AgentPendingTradeSequenceState {
    private String category = null;
    private List<Item> items = null;
    private int recipientId = 0;
    private int meso = 0;
    private int itemIndex = 0;
    private int timerMs = 0;
    private boolean mesoAdded = false;
    private boolean allItemsAdded = false;
    private boolean agentDone = false;
    private boolean singleBatch = false;
    private boolean inviteAnnounced = false;
    private String categoryMessage = null;
    private int shareBudget = 0;
    private final Map<Item, Short> restoreSlots = new IdentityHashMap<>();

    public String category() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<Item> items() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public int recipientId() {
        return recipientId;
    }

    public void setRecipientId(int recipientId) {
        this.recipientId = recipientId;
    }

    public int meso() {
        return meso;
    }

    public void setMeso(int meso) {
        this.meso = meso;
    }

    public int itemIndex() {
        return itemIndex;
    }

    public void setItemIndex(int itemIndex) {
        this.itemIndex = itemIndex;
    }

    public void incrementItemIndex() {
        itemIndex++;
    }

    public int timerMs() {
        return timerMs;
    }

    public void setTimerMs(int timerMs) {
        this.timerMs = timerMs;
    }

    public boolean mesoAdded() {
        return mesoAdded;
    }

    public void setMesoAdded(boolean mesoAdded) {
        this.mesoAdded = mesoAdded;
    }

    public boolean allItemsAdded() {
        return allItemsAdded;
    }

    public void setAllItemsAdded(boolean allItemsAdded) {
        this.allItemsAdded = allItemsAdded;
    }

    public boolean agentDone() {
        return agentDone;
    }

    public void setAgentDone(boolean agentDone) {
        this.agentDone = agentDone;
    }

    public boolean singleBatch() {
        return singleBatch;
    }

    public void setSingleBatch(boolean singleBatch) {
        this.singleBatch = singleBatch;
    }

    public boolean inviteAnnounced() {
        return inviteAnnounced;
    }

    public void setInviteAnnounced(boolean inviteAnnounced) {
        this.inviteAnnounced = inviteAnnounced;
    }

    public String categoryMessage() {
        return categoryMessage;
    }

    public void setCategoryMessage(String categoryMessage) {
        this.categoryMessage = categoryMessage;
    }

    public int shareBudget() {
        return shareBudget;
    }

    public void setShareBudget(int shareBudget) {
        this.shareBudget = shareBudget;
    }

    public Map<Item, Short> restoreSlots() {
        return restoreSlots;
    }
}
