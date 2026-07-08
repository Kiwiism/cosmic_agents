package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTradeBatchServiceTest {
    @Test
    void missingRecipientCancelsWithoutInitializingBatch() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        AgentTradeBatchService.openBatch(
                entry,
                mock(Character.class),
                List.of(item(2000000)),
                0,
                () -> null,
                () -> cancelled.set(true),
                () -> {},
                (agent, recipient) -> {},
                () -> "invite",
                message -> {});

        assertTrue(cancelled.get());
        assertTrue(AgentBotPendingTradeStateRuntime.isBetweenBatches(entry));
    }

    @Test
    void availableRecipientInitializesBatchAndInvites() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Character recipient = mock(Character.class);
        Item item = item(2000000);
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicReference<Character> inviteAgent = new AtomicReference<>();
        AtomicReference<Character> inviteRecipient = new AtomicReference<>();
        AtomicReference<String> reply = new AtomicReference<>();

        AgentTradeBatchService.openBatch(
                entry,
                agent,
                List.of(item),
                123,
                () -> recipient,
                () -> {},
                () -> started.set(true),
                (a, r) -> {
                    inviteAgent.set(a);
                    inviteRecipient.set(r);
                },
                () -> "invite",
                reply::set);

        assertEquals(List.of(item), AgentBotPendingTradeStateRuntime.items(entry));
        assertEquals(123, AgentBotPendingTradeStateRuntime.meso(entry));
        assertTrue(started.get());
        assertSame(agent, inviteAgent.get());
        assertSame(recipient, inviteRecipient.get());
        assertTrue(AgentBotPendingTradeStateRuntime.inviteAnnounced(entry));
        assertEquals("invite", reply.get());
    }

    @Test
    void supplyShareCategorySkipsInviteAnnouncement() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentTradeStateService.initializeSequence(entry, "pot_share", 22, true);

        AgentTradeBatchService.openBatch(
                entry,
                mock(Character.class),
                List.of(item(2000000)),
                0,
                () -> mock(Character.class),
                () -> {},
                () -> {},
                (agent, recipient) -> {},
                () -> "invite",
                message -> {});

        assertFalse(AgentBotPendingTradeStateRuntime.inviteAnnounced(entry));
    }

    private static Item item(int itemId) {
        return new Item(itemId, (short) 1, (short) 1);
    }
}
