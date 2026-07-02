package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickPreflightServiceTest {
    @Test
    void consumesTickWhenAirshowIsActiveBeforeResolvingAgent() {
        AtomicInteger removals = new AtomicInteger();

        AgentTickPreflightService.Result result = AgentTickPreflightService.runPreflight(
                new BotEntry(null, null, null),
                123,
                1_000L,
                hooks(true, false, false, removals, new AtomicBoolean(), new AtomicBoolean()));

        assertTrue(result.consumedTick());
        assertEquals(0, removals.get());
    }

    @Test
    void removesAgentWhenMapIsMissing() {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(null);
        BotEntry entry = new BotEntry(agent, mock(Character.class), null);
        AtomicInteger removedId = new AtomicInteger();

        AgentTickPreflightService.Result result = AgentTickPreflightService.runPreflight(
                entry,
                456,
                1_000L,
                hooks(false, false, true, removedId, new AtomicBoolean(), new AtomicBoolean()));

        assertTrue(result.consumedTick());
        assertSame(agent, result.agent());
        assertEquals(456, removedId.get());
    }

    @Test
    void runsHeartbeatOfferExpiryAndAiPreparationWhenPreflightPasses() {
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(mock(MapleMap.class));
        BotEntry entry = new BotEntry(agent, mock(Character.class), null);
        AtomicBoolean heartbeatRan = new AtomicBoolean();
        AtomicBoolean offerExpired = new AtomicBoolean();
        AtomicReference<Long> tickAtSeen = new AtomicReference<>();

        AgentTickPreflightService.Result result = AgentTickPreflightService.runPreflight(
                entry,
                789,
                12_345L,
                new AgentTickPreflightService.Hooks(
                        ignored -> false,
                        (ignored, movementTickMs) -> false,
                        ignored -> {
                            throw new AssertionError("remove should not run");
                        },
                        (tickEntry, tickAgent, nowMs, intervalMs) -> {
                            assertSame(entry, tickEntry);
                            assertSame(agent, tickAgent);
                            assertEquals(12_345L, nowMs);
                            assertEquals(600_000L, intervalMs);
                            heartbeatRan.set(true);
                        },
                        tickEntry -> {
                            assertSame(entry, tickEntry);
                            offerExpired.set(true);
                        },
                        (tickEntry, movementTickMs, aiTickMs, tickAtMs) -> {
                            assertSame(entry, tickEntry);
                            assertEquals(100, movementTickMs);
                            assertEquals(300, aiTickMs);
                            tickAtSeen.set(tickAtMs);
                            return true;
                        },
                        100,
                        300,
                        600_000L));

        assertFalse(result.consumedTick());
        assertSame(agent, result.agent());
        assertTrue(result.runAiTick());
        assertTrue(heartbeatRan.get());
        assertTrue(offerExpired.get());
        assertEquals(12_345L, tickAtSeen.get());
    }

    private static AgentTickPreflightService.Hooks hooks(boolean airshowActive,
                                                         boolean skipDelay,
                                                         boolean allowRemove,
                                                         AtomicInteger removedId,
                                                         AtomicBoolean heartbeatRan,
                                                         AtomicBoolean offerExpired) {
        return new AgentTickPreflightService.Hooks(
                ignored -> airshowActive,
                (ignored, movementTickMs) -> skipDelay,
                agentCharId -> {
                    if (!allowRemove) {
                        throw new AssertionError("remove should not run");
                    }
                    removedId.set(agentCharId);
                },
                (entry, agent, nowMs, intervalMs) -> heartbeatRan.set(true),
                entry -> offerExpired.set(true),
                (entry, movementTickMs, aiTickMs, tickAtMs) -> true,
                100,
                300,
                600_000L);
    }
}
