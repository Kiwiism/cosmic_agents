package server.bots;

import client.Character;
import client.inventory.InventoryType;

import java.awt.*;

public final class BotScriptContext {
    public final BotEntry entry;
    public final Character bot;
    public final Character owner;
    public final BotManager manager;

    BotScriptContext(BotEntry entry, Character bot, Character owner, BotManager manager) {
        this.entry = entry;
        this.bot = bot;
        this.owner = owner;
        this.manager = manager;
    }

    public int getInt(String key) {
        return entry.script.ints.getOrDefault(key, 0);
    }

    public void setInt(String key, int value) {
        entry.script.ints.put(key, value);
    }

    public void waitMs(long ms) {
        entry.script.waitUntilMs = System.currentTimeMillis() + ms;
    }

    public boolean waitDone() {
        return System.currentTimeMillis() >= entry.script.waitUntilMs;
    }

    public void queueMoveTo(Point point, boolean precise) {
        manager.queueTask(entry, BotTask.moveTo(point, precise));
    }

    public void queueFollowUntilNearOwner(int nearPx) {
        manager.queueTask(entry, BotTask.followUntilNear(owner, nearPx));
    }

    public void queueGrind() {
        manager.queueTask(entry, BotTask.grind());
    }

    public void queueStop() {
        manager.queueTask(entry, BotTask.stop());
    }

    public void queueDrop(InventoryType type, int itemId, short quantity) {
        manager.queueTask(entry, BotTask.dropItem(type, itemId, quantity));
    }

    public boolean tasksDone() {
        return !manager.hasQueuedTasks(entry);
    }
}
