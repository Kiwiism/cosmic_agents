package server.agents.plans;

import client.Character;
import client.inventory.InventoryType;
import server.agents.runtime.AgentScriptTaskQueueService;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.*;

public final class AgentScriptContext {
    @FunctionalInterface
    public interface CheapMoveTargetCheck {
        boolean isCheap(AgentRuntimeEntry entry, Point point, int maxPathCost, int fallbackRangeX, int fallbackRangeY);
    }

    @FunctionalInterface
    public interface DropItemAction {
        boolean drop(AgentRuntimeEntry entry, InventoryType type, int itemId, short quantity);
    }

    private final AgentRuntimeEntry entry;
    private final Character bot;
    private final Character owner;
    private final CheapMoveTargetCheck cheapMoveTargetCheck;
    private final DropItemAction dropItemAction;

    public AgentScriptContext(AgentRuntimeEntry entry,
                              Character bot,
                              Character owner,
                              CheapMoveTargetCheck cheapMoveTargetCheck,
                              DropItemAction dropItemAction) {
        this.entry = entry;
        this.bot = bot;
        this.owner = owner;
        this.cheapMoveTargetCheck = cheapMoveTargetCheck;
        this.dropItemAction = dropItemAction;
    }

    public AgentRuntimeEntry entry() {
        return entry;
    }

    public Character bot() {
        return bot;
    }

    public Character owner() {
        return owner;
    }

    public int getInt(String key) {
        return AgentScriptTaskStateRuntime.scriptInt(entry, key);
    }

    public void setInt(String key, int value) {
        AgentScriptTaskStateRuntime.setScriptInt(entry, key, value);
    }

    public void waitMs(long ms) {
        AgentScriptTaskStateRuntime.waitScriptUntil(entry, System.currentTimeMillis() + ms);
    }

    public boolean waitDone() {
        return AgentScriptTaskStateRuntime.scriptWaitDone(entry, System.currentTimeMillis());
    }

    public void queueMoveTo(Point point, boolean precise) {
        AgentScriptTaskQueueService.queueMoveTo(entry, point, precise);
    }

    public void queueMoveToWithLocalCombat(Point point, boolean precise) {
        AgentScriptTaskQueueService.queueMoveTo(entry, point, precise, AgentTask.MoveCombatMode.LOCAL_OPPORTUNITY);
    }

    public void queueFollowUntilNearOwner(int nearPx) {
        AgentScriptTaskQueueService.queueTask(entry, AgentTask.followUntilNear(owner, nearPx));
    }

    public void queueGrind() {
        AgentScriptTaskQueueService.queueTask(entry, AgentTask.grind());
    }

    public void queueStop() {
        AgentScriptTaskQueueService.queueTask(entry, AgentTask.stop());
    }

    public void queueDrop(InventoryType type, int itemId, short quantity) {
        AgentScriptTaskQueueService.queueTask(entry, AgentTask.dropItem(type, itemId, quantity));
    }

    public boolean dropItem(InventoryType type, int itemId, short quantity) {
        return dropItemAction.drop(entry, type, itemId, quantity);
    }

    public boolean tasksDone() {
        return !AgentScriptTaskQueueService.hasQueuedTasks(entry);
    }

    public boolean isCheapMoveTarget(Point point, int maxPathCost, int fallbackRangeX, int fallbackRangeY) {
        return cheapMoveTargetCheck.isCheap(entry, point, maxPathCost, fallbackRangeX, fallbackRangeY);
    }
}
