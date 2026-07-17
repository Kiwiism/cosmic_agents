package server.agents.coordination;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentCoordinationRuntimeTest {
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
}
