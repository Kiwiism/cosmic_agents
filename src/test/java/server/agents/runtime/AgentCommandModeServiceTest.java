package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentCommandModeServiceTest {
    @Test
    void skipsWhenEntryIsMissing() {
        AtomicInteger calls = new AtomicInteger();

        AgentCommandModeService.runPreparedModeCommand(
                null, calls::incrementAndGet, calls::incrementAndGet, calls::incrementAndGet);

        assertEquals(0, calls.get());
    }

    @Test
    void clearsTasksCancelsShopThenStartsMode() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger order = new AtomicInteger();

        AgentCommandModeService.runPreparedModeCommand(
                entry,
                () -> assertEquals(0, order.getAndIncrement()),
                () -> assertEquals(1, order.getAndIncrement()),
                () -> assertEquals(2, order.getAndIncrement()));

        assertEquals(3, order.get());
    }
}
