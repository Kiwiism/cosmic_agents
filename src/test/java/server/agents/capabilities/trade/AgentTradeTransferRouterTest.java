package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService.PreparedTradeItems;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTradeTransferRouterTest {
    @Test
    void mesoCategoryBypassesOwnerGuard() {
        TraceCallbacks callbacks = new TraceCallbacks();

        AgentTradeTransferRouter.routeCategoryTransfer(
                "mesos", false, true, true, 0L, callbacks);

        assertEquals(List.of("meso"), callbacks.events);
    }

    @Test
    void busyAgentRepliesBeforeCategoryRouting() {
        TraceCallbacks callbacks = new TraceCallbacks();

        AgentTradeTransferRouter.routeCategoryTransfer(
                "scrolls", true, true, false, 0L, callbacks);

        assertEquals(1, callbacks.replies.size());
        assertTrue(callbacks.events.isEmpty());
    }

    @Test
    void routesEquipAmmoReservedAndPreparedCategories() {
        assertRoute("equips", "equips");
        assertRoute("ammo", "ammo");
        assertRoute("equips:reserved:2", "reserved");
        assertRoute("scrolls", "prepare", "prepared");
    }

    private static void assertRoute(String category, String... expected) {
        TraceCallbacks callbacks = new TraceCallbacks();

        AgentTradeTransferRouter.routeCategoryTransfer(
                category, true, false, false, 0L, callbacks);

        assertEquals(List.of(expected), callbacks.events);
    }

    private static final class TraceCallbacks implements AgentTradeTransferRouter.TransferCallbacks {
        final List<String> events = new ArrayList<>();
        final List<String> replies = new ArrayList<>();
        final AtomicReference<PreparedTradeItems> prepared = new AtomicReference<>();

        @Override public void startMesoTransfer() { events.add("meso"); }
        @Override public void startEquipsGroupTransfer() { events.add("equips"); }
        @Override public void startReservedEquipTransfer() { events.add("reserved"); }
        @Override public void startAmmoGroupTransfer() { events.add("ammo"); }

        @Override
        public PreparedTradeItems prepareTradeItems() {
            events.add("prepare");
            return new PreparedTradeItems(List.of(), null);
        }

        @Override
        public void startPreparedTransfer(PreparedTradeItems prepared) {
            this.prepared.set(prepared);
            events.add("prepared");
        }

        @Override public void reply(String message) { replies.add(message); }
        @Override public void logSlowCommand(String operation, long startedAt) {}
    }
}
