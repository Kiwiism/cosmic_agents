package server.agents.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.economy.EconomyOperationContext;
import server.economy.EconomyOperationMetadata;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExclusiveControlRuntimeTest {
    @AfterEach
    void clear() {
        AgentExclusiveControlRuntime.clearForTests();
    }

    @Test
    void leaseIsIdempotentForOwnerAndRejectsCompetitors() {
        AgentExclusiveControlRuntime.claim(7, "economy:one");
        AgentExclusiveControlRuntime.claim(7, "economy:one");

        assertTrue(AgentExclusiveControlRuntime.claimed(7));
        assertTrue(AgentExclusiveControlRuntime.ownedBy(7, "economy:one"));
        assertThrows(IllegalStateException.class,
                () -> AgentExclusiveControlRuntime.claim(7, "economy:two"));

        AgentExclusiveControlRuntime.release("economy:one");
        assertFalse(AgentExclusiveControlRuntime.claimed(7));
    }

    @Test
    void carriesEconomyAttributionIntoAsynchronousWork() {
        UUID runId = UUID.randomUUID();
        EconomyOperationMetadata metadata = new EconomyOperationMetadata(runId,
                Instant.parse("2026-01-01T00:00:05Z"), "decision", null,
                "config", "catalog", "MARKET_CYCLE", true, false);
        AgentExclusiveControlRuntime.claim(7, "economy:" + runId);
        AgentExclusiveControlRuntime.attribute(7, metadata);

        EconomyOperationMetadata observed = AgentExclusiveControlRuntime.withAttribution(
                7, EconomyOperationContext::currentMetadata);

        assertEquals(metadata, observed);
        assertNull(EconomyOperationContext.currentMetadata().runId());
    }
}
