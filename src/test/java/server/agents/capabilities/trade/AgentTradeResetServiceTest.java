package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AgentTradeResetServiceTest {
    @Test
    void resetClearsSequenceAndRefillsWhenRestoreSlotsExisted() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        List<String> calls = new ArrayList<>();
        AgentTradeStateService.initializeSequence(entry, "scrolls", 12, true);
        AgentPendingTradeStateRuntime.rememberRestoreSlot(entry, new Item(1002000, (short) 1, (short) 1), (short) -1);

        AgentTradeResetService.reset(
                entry,
                agent,
                () -> {
                    calls.add("restore");
                    AgentPendingTradeStateRuntime.clearRestoreSlots(entry);
                },
                () -> calls.add("manual"),
                () -> calls.add("refill"));

        assertEquals(List.of("restore", "manual", "refill"), calls);
        assertFalse(AgentPendingTradeStateRuntime.hasActiveSequence(entry));
        assertFalse(AgentPendingTradeStateRuntime.hasRestoreSlots(entry));
    }

    @Test
    void resetSkipsRefillWhenNoRestoreSlotsExisted() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        List<String> calls = new ArrayList<>();
        AgentTradeStateService.initializeSequence(entry, "scrolls", 12, true);

        AgentTradeResetService.reset(
                entry,
                agent,
                () -> calls.add("restore"),
                () -> calls.add("manual"),
                () -> calls.add("refill"));

        assertEquals(List.of("restore", "manual"), calls);
        assertFalse(AgentPendingTradeStateRuntime.hasActiveSequence(entry));
    }
}
