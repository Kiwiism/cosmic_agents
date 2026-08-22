package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentIdentityGatewayTest {
    @Test
    void activeStatusControlsDurableClassification() throws Exception {
        ReadOnlyIdentityGateway active = characterId -> Optional.of(new AgentIdentityRecord(
                characterId, AgentIdentityStatus.ACTIVE, AgentIdentityOrigin.PROVISIONED, false));
        ReadOnlyIdentityGateway retired = characterId -> Optional.of(new AgentIdentityRecord(
                characterId, AgentIdentityStatus.RETIRED, AgentIdentityOrigin.PROVISIONED, false));
        ReadOnlyIdentityGateway missing = characterId -> Optional.empty();

        assertTrue(active.isActiveAgent(7));
        assertFalse(retired.isActiveAgent(7));
        assertFalse(missing.isActiveAgent(7));
    }

    @Test
    void recordRejectsInvalidIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new AgentIdentityRecord(
                0, AgentIdentityStatus.ACTIVE, AgentIdentityOrigin.PROVISIONED, false));
    }

    private interface ReadOnlyIdentityGateway extends AgentIdentityGateway {
        @Override
        default void register(int characterId,
                              AgentIdentityOrigin origin,
                              boolean interactiveAllowed) {
            throw new UnsupportedOperationException();
        }
    }
}
