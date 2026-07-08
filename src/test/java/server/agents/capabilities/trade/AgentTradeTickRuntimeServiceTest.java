package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentTradeTickRuntimeServiceTest {
    @Test
    void idleStateDoesNotReadCurrentTrade() {
        AtomicInteger tradeLookups = new AtomicInteger();

        AgentTradeTickRuntimeService.tickTrade(
                entry(),
                mock(Character.class),
                runtimeCallbacks(tradeLookups),
                inventoryCallbacks(),
                lifecycleCallbacks());

        assertEquals(0, tradeLookups.get());
    }

    @Test
    void queuedRetryRunsBeforeCurrentTradeLookup() {
        AgentRuntimeEntry entry = entry();
        AtomicInteger retryRuns = new AtomicInteger();
        AtomicInteger tradeLookups = new AtomicInteger();
        AgentBotPendingTradeStateRuntime.queueRetry(entry, retryRuns::incrementAndGet, 0);

        AgentTradeTickRuntimeService.tickTrade(
                entry,
                mock(Character.class),
                runtimeCallbacks(tradeLookups),
                inventoryCallbacks(),
                lifecycleCallbacks());

        assertEquals(1, retryRuns.get());
        assertEquals(0, tradeLookups.get());
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), null, null);
    }

    private static AgentTradeTickRuntimeService.RuntimeCallbacks runtimeCallbacks(AtomicInteger tradeLookups) {
        return AgentTradeTickRuntimeService.RuntimeCallbacks.of(
                remaining -> remaining - 100,
                agent -> {
                    tradeLookups.incrementAndGet();
                    return null;
                },
                duration -> duration + 100,
                () -> 100,
                ignored -> null,
                (agent, owner) -> {
                },
                (entry, agent) -> null,
                ignored -> false);
    }

    private static AgentInventoryTradeRuntimeService.RuntimeCallbacks inventoryCallbacks() {
        return AgentInventoryTradeRuntimeService.RuntimeCallbacks.of(
                (owner, agent) -> List.of(),
                ignored -> WeaponType.NOT_A_WEAPON,
                ignored -> 0,
                ignored -> false,
                () -> false,
                () -> false,
                ignored -> Set.of(),
                ignored -> false,
                () -> null);
    }

    private static AgentTradeLifecycleRuntimeService.RuntimeCallbacks lifecycleCallbacks() {
        return AgentTradeLifecycleRuntimeService.RuntimeCallbacks.of(
                (entry, agent) -> {
                },
                (entry, agent) -> {
                },
                ignored -> null,
                (agent, owner) -> {
                },
                (minMs, maxMs) -> minMs,
                () -> "thanks",
                () -> "freebie");
    }
}
