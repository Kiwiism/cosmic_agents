package server.agents.integration;

import client.Character;
import client.inventory.InventoryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AgentEconomicActionGuardRuntimeTest {
    @AfterEach void reset() { AgentEconomicActionGuardRuntime.clear(); }

    @Test
    void propagatesLogicalMarketContextToInstalledOwner() {
        Instant logicalAt = Instant.parse("2026-01-01T00:00:10Z");
        AtomicReference<String> seen = new AtomicReference<>();
        AgentEconomicActionGuardRuntime.install((agent, type, slot, itemId, quantity, venue, at) -> {
            seen.set(venue + ":" + at);
            return AgentEconomicActionGuardRuntime.Decision.denied("PROTECTED_UNREVIEWED");
        });

        var decision = AgentEconomicActionGuardRuntime.withNpcSaleContext(logicalAt, "FM_REMOTE_NPC",
                () -> AgentEconomicActionGuardRuntime.claimNpcSale(mock(Character.class),
                        InventoryType.ETC, (short) 1, 4000000, (short) 1));

        assertFalse(decision.allowed());
        assertEquals("PROTECTED_UNREVIEWED", decision.reason());
        assertEquals("FM_REMOTE_NPC:" + logicalAt, seen.get());
    }

    @Test
    void defaultsToAllowWhenNoEconomyRunOwnsCharacterMutations() {
        assertTrue(AgentEconomicActionGuardRuntime.claimNpcSale(mock(Character.class),
                InventoryType.ETC, (short) 1, 4000000, (short) 1).allowed());
    }
}
