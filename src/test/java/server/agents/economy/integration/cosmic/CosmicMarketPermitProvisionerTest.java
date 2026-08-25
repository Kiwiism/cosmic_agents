package server.agents.economy.integration.cosmic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicMarketPermitProvisionerTest {
    @Test
    void selectionIsDeterministicAndLimitedToVerifiedPool() {
        List<Integer> permits = List.of(5140000, 5140001, 5140002, 5140003, 5140004, 5140006);
        CosmicMarketPermitProvisioner provisioner = new CosmicMarketPermitProvisioner(
                UUID.randomUUID(), 4815162342L, "GRANT_RANDOM_REAL_PERMIT_ON_ENTRY", permits);
        UUID request = UUID.randomUUID();

        int first = provisioner.selectedPermit("agent-17", request);
        int replay = provisioner.selectedPermit("agent-17", request);

        assertEquals(first, replay);
        assertTrue(permits.contains(first));
    }
}
