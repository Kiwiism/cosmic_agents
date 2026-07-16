package server.agents.plans;

import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentScriptTaskStartServiceTest {
    @Test
    void partnerManagedEntryDoesNotStartQueuedInventoryMutation() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AtomicInteger callbacks = new AtomicInteger();
        entry.markPartnerManaged();

        AgentScriptTaskStartService.StartHooks hooks = new AgentScriptTaskStartService.StartHooks(
                (Point point, Boolean precise) -> callbacks.incrementAndGet(),
                target -> callbacks.incrementAndGet(),
                ignored -> null,
                callbacks::incrementAndGet,
                callbacks::incrementAndGet,
                (type, itemId, quantity) -> {
                    callbacks.incrementAndGet();
                    return true;
                });

        AgentScriptTaskStartService.start(
                entry,
                AgentTask.dropItem(InventoryType.ETC, 4000000, (short) 1),
                hooks);

        assertEquals(0, callbacks.get());
    }
}
