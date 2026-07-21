package server.agents.coordination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCoordinationRuntimeTest {
    @AfterEach
    void resetRuntime() {
        AgentCoordinationRuntime.resetForTests();
        System.clearProperty("agents.coordination.routeCapacity");
    }

    @Test
    void publishesTypedSupplyContextWithoutChatParsing() throws Exception {
        AtomicReference<AgentCoordinationMessage> observed = new AtomicReference<>();
        AgentSupplyNeedMessage message = new AgentSupplyNeedMessage(
                200, 100L, 1010100, AgentSupplyNeedMessage.SupplyKind.AMMUNITION,
                12, "CLAW", 1234L);

        try (AutoCloseable ignored = AgentCoordinationRuntime.subscribe(observed::set)) {
            AgentCoordinationRuntime.publish(message);
        }

        assertSame(message, observed.get());
        assertEquals(100L, message.cohortId());
        assertEquals("CLAW", message.equipmentContext());
    }

    @Test
    void routesByCohortWithCorrelationAndDisposition() throws Exception {
        AgentSupplyNeedMessage message = new AgentSupplyNeedMessage(
                200, 100L, 1010100, AgentSupplyNeedMessage.SupplyKind.HP_POTION,
                0, "", 1_234L);
        AtomicReference<AgentCoordinationEnvelope> observed = new AtomicReference<>();

        try (AutoCloseable ignored = AgentCoordinationRuntime.subscribe(
                AgentCoordinationScope.COHORT, 100L, observed::set)) {
            AgentCoordinationPublishResult result = AgentCoordinationRuntime.publishRouted(
                    message, AgentCoordinationScope.COHORT, 100L, 0,
                    30_000L, true, "supply-request:200");

            assertTrue(result.accepted());
            assertSame(result.envelope(), observed.get());
            assertEquals("supply-request:200", result.envelope().correlationId());
            List<AgentCoordinationEnvelope> drained = AgentCoordinationRuntime.drain(
                    AgentCoordinationScope.COHORT, 100L, 1, System.currentTimeMillis());
            assertEquals(List.of(result.envelope()), drained);

            AgentCoordinationReceipt receipt = AgentCoordinationRuntime.recordDisposition(
                    result.envelope().messageId(), 201, AgentCoordinationDisposition.ACCEPTED,
                    System.currentTimeMillis(), "can-share");
            assertEquals(receipt, AgentCoordinationRuntime.receipt(
                    result.envelope().messageId(), 201));
        }
    }

    @Test
    void boundsEachRouteExpiresOldMessagesAndIsolatesListenerFailure() throws Exception {
        System.setProperty("agents.coordination.routeCapacity", "1");
        AgentSupplyNeedMessage message = new AgentSupplyNeedMessage(
                200, 100L, 1010100, AgentSupplyNeedMessage.SupplyKind.MP_POTION,
                0, "", 1_234L);

        try (AutoCloseable ignored = AgentCoordinationRuntime.subscribe(
                AgentCoordinationScope.PARTY, 77L, envelope -> {
                    throw new IllegalStateException("isolated");
                })) {
            AgentCoordinationEnvelope firstEnvelope = new AgentCoordinationEnvelope(
                    1L, "first", AgentCoordinationScope.PARTY, 77L, 0,
                    100L, 200L, false, message);
            AgentCoordinationEnvelope secondEnvelope = new AgentCoordinationEnvelope(
                    2L, "second", AgentCoordinationScope.PARTY, 77L, 0,
                    100L, 300L, false, message);
            AgentCoordinationPublishResult first = AgentCoordinationRuntime.publish(firstEnvelope, 100L);
            AgentCoordinationPublishResult second = AgentCoordinationRuntime.publish(secondEnvelope, 100L);

            assertTrue(first.accepted());
            assertFalse(second.accepted());
            assertTrue(AgentCoordinationRuntime.drain(
                    AgentCoordinationScope.PARTY, 77L, 1, 200L).isEmpty());

            AgentCoordinationRuntimeSnapshot snapshot = AgentCoordinationRuntime.snapshot();
            assertEquals(1, snapshot.listenerFailures());
            assertEquals(1, snapshot.rejectedCapacity());
            assertEquals(1, snapshot.expired());
        }
    }
}
