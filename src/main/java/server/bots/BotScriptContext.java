package server.bots;

import client.Character;
import client.inventory.InventoryType;
import server.agents.integration.AgentBotScriptTaskStateRuntime;

import java.awt.*;

public final class BotScriptContext {
    private final BotEntry entry;
    private final Character bot;
    private final Character owner;
    private final BotManager manager;

    BotScriptContext(BotEntry entry, Character bot, Character owner, BotManager manager) {
        this.entry = entry;
        this.bot = bot;
        this.owner = owner;
        this.manager = manager;
    }

    public BotEntry entry() {
        return entry;
    }

    public Character bot() {
        return bot;
    }

    public Character owner() {
        return owner;
    }

    public BotManager manager() {
        return manager;
    }

    public int getInt(String key) {
        return AgentBotScriptTaskStateRuntime.scriptInt(entry, key);
    }

    public void setInt(String key, int value) {
        AgentBotScriptTaskStateRuntime.setScriptInt(entry, key, value);
    }

    public void waitMs(long ms) {
        AgentBotScriptTaskStateRuntime.waitScriptUntil(entry, System.currentTimeMillis() + ms);
    }

    public boolean waitDone() {
        return AgentBotScriptTaskStateRuntime.scriptWaitDone(entry, System.currentTimeMillis());
    }

    public void queueMoveTo(Point point, boolean precise) {
        manager.queueTask(entry, BotTask.moveTo(point, precise));
    }

    public void queueMoveToWithLocalCombat(Point point, boolean precise) {
        manager.queueTask(entry, BotTask.moveTo(point, precise, BotTask.MoveCombatMode.LOCAL_OPPORTUNITY));
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

    public boolean isCheapMoveTarget(Point point, int maxPathCost, int fallbackRangeX, int fallbackRangeY) {
        return manager.isCheapScriptMoveTarget(entry, point, maxPathCost, fallbackRangeX, fallbackRangeY);
    }
}
