package server.agents.capabilities.looting;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentPassiveLootRuntimeServiceTest {
    @Test
    void lootInhibitTicksAndSkipsMapInspection() {
        Character agent = mock(Character.class);
        AtomicBoolean inhibited = new AtomicBoolean();
        AtomicBoolean inhibitTicked = new AtomicBoolean();

        AgentPassiveLootRuntimeService.tickPassiveLoot(
                entry(agent),
                agent,
                callbacks(true, inhibitTicked, inhibited, false));

        assertTrue(inhibitTicked.get());
        verify(agent, org.mockito.Mockito.never()).getMap();
    }

    @Test
    void activeTradeSequenceSkipsMapInspectionBeforeCooldownTick() {
        Character agent = mock(Character.class);
        AtomicBoolean cooldownTicked = new AtomicBoolean();

        AgentPassiveLootRuntimeService.tickPassiveLoot(
                entry(agent),
                agent,
                callbacks(false, new AtomicBoolean(), cooldownTicked, true));

        assertFalse(cooldownTicked.get());
        verify(agent, org.mockito.Mockito.never()).getMap();
    }

    private static AgentRuntimeEntry entry(Character agent) {
        return new AgentRuntimeEntry(agent, null, null);
    }

    private static AgentPassiveLootRuntimeService.RuntimeCallbacks callbacks(boolean hasLootInhibit,
                                                                            AtomicBoolean inhibitTicked,
                                                                            AtomicBoolean cooldownTicked,
                                                                            boolean activeTradeSequence) {
        return AgentPassiveLootRuntimeService.RuntimeCallbacks.of(
                ignored -> hasLootInhibit,
                ignored -> inhibitTicked.set(true),
                ignored -> activeTradeSequence,
                ignored -> cooldownTicked.set(true),
                System::currentTimeMillis,
                () -> 100,
                ignored -> false,
                (entry, message) -> {
                },
                () -> 1000,
                (entry, cooldown) -> {
                },
                ignored -> null,
                ignored -> null,
                (agent, item) -> false,
                (agent, owner, pendingLootOfferItem) -> {
                },
                (entry, agent, item, delayMs) -> {
                },
                (agent, drop) -> {
                },
                Character::pickupItem);
    }
}
