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
        assertFalse(lifecycle.contains("Party.createParty("));
        assertFalse(lifecycle.contains("Party.joinParty("));
        assertTrue(lifecycle.contains("AgentPartyGatewayRuntime.party().leaveCurrentParty(agent)"));
        assertTrue(lifecycle.contains("AgentPartyGatewayRuntime.party().createAgentParty(leader)"));
        assertTrue(lifecycle.contains("AgentPartyGatewayRuntime.party().joinAgentParty(agent, leaderParty.getId())"));
        assertTrue(gateway.contains("Party.leaveParty(agent.getParty(), agent.getClient())"));
        assertTrue(gateway.contains("Party.createParty(leader, true)"));
        assertTrue(gateway.contains("Party.joinParty(agent, partyId, true)"));
    }
}
