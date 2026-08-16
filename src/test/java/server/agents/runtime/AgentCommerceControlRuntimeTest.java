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

class AgentCommerceControlRuntimeTest {
    @AfterEach
    void clear() {
        AgentCommerceControlRuntime.clearForTests();
    }

    @Test
    void leaseIsIdempotentForOwnerAndRejectsCompetitors() {
        AgentCommerceControlRuntime.claim(7, "economy:one");
        AgentCommerceControlRuntime.claim(7, "economy:one");

        assertTrue(AgentCommerceControlRuntime.claimed(7));
        assertTrue(AgentCommerceControlRuntime.ownedBy(7, "economy:one"));
        assertThrows(IllegalStateException.class,
                () -> AgentCommerceControlRuntime.claim(7, "economy:two"));

        AgentCommerceControlRuntime.release("economy:one");
        assertFalse(AgentCommerceControlRuntime.claimed(7));
    }

    @Test
    void carriesEconomyAttributionIntoAsynchronousWork() {
        UUID runId = UUID.randomUUID();
        EconomyOperationMetadata metadata = new EconomyOperationMetadata(runId,
                Instant.parse("2026-01-01T00:00:05Z"), "decision", null,
                "config", "catalog", "MARKET_CYCLE", true, false);
        AgentCommerceControlRuntime.claim(7, "economy:" + runId);
        AgentCommerceControlRuntime.attribute(7, metadata);

        EconomyOperationMetadata observed = AgentCommerceControlRuntime.withAttribution(
                7, EconomyOperationContext::currentMetadata);

        assertEquals(metadata, observed);
        assertNull(EconomyOperationContext.currentMetadata().runId());
    }
}
