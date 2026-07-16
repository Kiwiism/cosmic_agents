package server.agents.plans;

import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentScriptTaskQueueServiceTest {
    @Test
    void partnerManagedEntryRejectsQueuedMovementAndInventoryTasks() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        entry.markPartnerManaged();

        AgentScriptTaskQueueService.queueTask(entry, AgentTask.grind());
        AgentScriptTaskQueueService.queueMoveThenDropItem(
                entry,
                new Point(10, 20),
                true,
                InventoryType.ETC,
                4000000,
                (short) 1);

        assertFalse(AgentScriptTaskQueueService.hasQueuedTasks(entry));
    }
}
