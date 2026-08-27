package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLpqRewardScriptContractTest {
    @Test
    void arturoRewardsRegisteredMembersOnceAndLetsObserversExitWithoutReward() throws Exception {
        String npc = Files.readString(Path.of("scripts/npc/2040035.js"));
        String event = Files.readString(Path.of("scripts/event/LudiPQ.js"));

        assertTrue(npc.contains("AgentLpqSessionRegistry.isRegisteredParticipant(player)"));
        assertTrue(npc.contains("AgentLpqSessionRegistry.beginRewardClaim(player)"));
        assertTrue(npc.contains("AgentLpqSessionRegistry.cancelRewardClaim(player)"));
        assertTrue(npc.contains("AgentLpqSessionRegistry.completeRewardClaim(player)"));
        assertTrue(npc.contains("only registered members receive a reward"));
        assertTrue(event.contains("AgentLpqSessionRegistry.forfeitUnclaimedReward(player)"));
    }
}
