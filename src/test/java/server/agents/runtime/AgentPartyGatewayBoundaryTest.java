package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPartyGatewayBoundaryTest {
    @Test
    void partyLeaveClientMutationLivesInCosmicGateway() throws IOException {
        String lifecycle = Files.readString(Path.of(
                "src/main/java/server/agents/runtime/AgentPartyLifecycleService.java"));
        String gateway = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicPartyGateway.java"));

        assertFalse(lifecycle.contains("Party.leaveParty("));
        assertTrue(lifecycle.contains("AgentPartyGatewayRuntime.party().leaveCurrentParty(agent)"));
        assertTrue(gateway.contains("Party.leaveParty(agent.getParty(), agent.getClient())"));
    }
}
