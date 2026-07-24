package server.agents.coordination.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentInteractionSessionRegistryTest {
    @AfterEach
    void reset() {
        AgentInteractionSessionRegistry.resetForTests();
    }

    @Test
    void proposalRequiresAllParticipantsBeforeActivation() {
        AgentInteractionSessionSnapshot proposed = AgentInteractionSessionRegistry.propose(
                AgentInteractionSessionType.TRADE, 10, Set.of(20),
                100L, 1_000L, "trade:10:20", Map.of("itemId", "2000000"));
        assertEquals(AgentInteractionSessionStatus.PROPOSED, proposed.status());

        AgentInteractionSessionSnapshot active =
                AgentInteractionSessionRegistry.accept(proposed.sessionId(), 20, 200L);
        assertEquals(AgentInteractionSessionStatus.ACTIVE, active.status());
        assertEquals(2, active.acceptedCharacterIds().size());

        assertEquals(AgentInteractionSessionStatus.COMPLETED,
                AgentInteractionSessionRegistry.complete(
                        active.sessionId(), 300L, "exchange committed").status());
    }

    @Test
    void expiresBoundedProtocols() {
        AgentInteractionSessionSnapshot proposed = AgentInteractionSessionRegistry.propose(
                AgentInteractionSessionType.PARTY_INVITE, 10, Set.of(20),
                100L, 50L, "", Map.of());

        assertEquals(AgentInteractionSessionStatus.EXPIRED,
                AgentInteractionSessionRegistry.find(proposed.sessionId(), 150L).status());
    }
}
