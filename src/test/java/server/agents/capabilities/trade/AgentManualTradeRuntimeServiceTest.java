package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentManualTradeRuntimeServiceTest {
    @Test
    void activeTradeSequenceSuppressesManualTradeInspection() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        AtomicBoolean activeSequenceChecked = new AtomicBoolean();

        AgentManualTradeRuntimeService.tickManualTrade(
                entry,
                agent,
                mock(Character.class),
                callbacks(activeSequenceChecked, true),
                lifecycleCallbacks());

        assertTrue(activeSequenceChecked.get());
        verify(agent, org.mockito.Mockito.never()).getTrade();
    }

    @Test
    void missingManualTradeStopsBeforeOwnerTradeInspection() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        when(agent.getTrade()).thenReturn(null);

        AgentManualTradeRuntimeService.tickManualTrade(
                entry,
                agent,
                owner,
                callbacks(new AtomicBoolean(), false),
                lifecycleCallbacks());

        verify(agent).getTrade();
        verify(owner, org.mockito.Mockito.never()).getTrade();
    }

    private static AgentManualTradeRuntimeService.RuntimeCallbacks callbacks(AtomicBoolean activeSequenceChecked,
                                                                            boolean activeSequence) {
        return AgentManualTradeRuntimeService.RuntimeCallbacks.of(
                () -> {
                    activeSequenceChecked.set(true);
                    return activeSequence;
                },
                delayMs -> delayMs,
                () -> 100,
                ignored -> false,
                (peerId, ownerId) -> false,
                () -> "hello",
                (agent, owner) -> {
                });
    }

    private static AgentTradeLifecycleService.LifecycleCallbacks lifecycleCallbacks() {
        return AgentTradeLifecycleService.LifecycleCallbacks.of(
                (entry, agent) -> {
                },
                (entry, agent) -> {
                },
                entry -> null,
                (agent, owner) -> {
                },
                (minMs, maxMs) -> minMs,
                () -> "thanks",
                () -> "free",
                () -> 100,
                () -> false);
    }
}
