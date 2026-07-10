package server.agents.capabilities.party;

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
                "src/main/java/server/agents/capabilities/party/AgentPartyLifecycleService.java"));
        String gateway = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicPartyGateway.java"));
        String replies = Files.readString(Path.of(
                "src/main/java/server/agents/integration/AgentReplyRuntime.java"));
        String relation = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/dialogue/llm/AgentSenderRelation.java"));
        String situation = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/dialogue/llm/AgentSituationBuilder.java"));
        String followCandidates = Files.readString(Path.of(
                "src/main/java/server/agents/runtime/AgentFollowTargetCandidateService.java"));
        String followAnchor = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/follow/AgentFollowAnchorService.java"));
        String tradeRecipient = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/trade/AgentTradeRecipientService.java"));
        String questSync = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/quest/AgentPartyQuestSyncService.java"));

        assertFalse(lifecycle.contains("Party.leaveParty("));
        assertFalse(lifecycle.contains("Party.createParty("));
        assertFalse(lifecycle.contains("Party.joinParty("));
        assertFalse(lifecycle.contains("new PartyCharacter("));
        assertFalse(lifecycle.contains("PartyOperation.LOG_ONOFF"));
        assertTrue(lifecycle.contains("AgentPartyGatewayRuntime.party().leaveCurrentParty(agent)"));
        assertTrue(lifecycle.contains("AgentPartyGatewayRuntime.party().createAgentParty(leader)"));
        assertTrue(lifecycle.contains("AgentPartyGatewayRuntime.party().joinAgentParty(agent, leaderParty.id())"));
        assertTrue(lifecycle.contains("AgentPartyGatewayRuntime.party().publishAgentOnline(agent, leaderParty.id())"));
        assertTrue(gateway.contains("Party.leaveParty(agent.getParty(), agent.getClient())"));
        assertTrue(gateway.contains("Party.createParty(leader, true)"));
        assertTrue(gateway.contains("Party.joinParty(agent, partyId, true)"));
        assertTrue(gateway.contains("new PartyCharacter(agent)"));
        assertTrue(gateway.contains("PartyOperation.LOG_ONOFF"));
        assertFalse(replies.contains("import net.server.world.Party"));
        assertFalse(replies.contains("getWorldServer().partyChat("));
        assertTrue(replies.contains("AgentPartyGatewayRuntime.party().sendPartyChat("));
        assertTrue(gateway.contains("getWorldServer().partyChat(party, message, speaker.getName())"));
        assertFalse(lifecycle.contains("import net.server.world.Party"));
        assertFalse(relation.contains("import net.server.world.Party"));
        assertFalse(situation.contains("import net.server.world.Party"));
        assertTrue(lifecycle.contains("AgentPartyGatewayRuntime.party().snapshot("));
        assertTrue(relation.contains("AgentPartyGatewayRuntime.party().snapshot(agent)"));
        assertTrue(situation.contains("AgentPartyGatewayRuntime.party().snapshot(bot)"));
        assertFalse(followCandidates.contains("leader.getParty()"));
        assertFalse(followAnchor.contains("leader.getParty()"));
        assertFalse(tradeRecipient.contains("owner.getParty()"));
        assertFalse(questSync.contains("source.getParty()"));
        assertTrue(followCandidates.contains("AgentPartyGatewayRuntime.party().onlineMembers(leader)"));
        assertTrue(followAnchor.contains("AgentPartyGatewayRuntime.party().onlineMembers(leader)"));
        assertTrue(tradeRecipient.contains("AgentPartyGatewayRuntime.party().onlineMembers(owner)"));
        assertTrue(questSync.contains("AgentPartyGatewayRuntime.party().onlineMembers(source)"));
    }
}
