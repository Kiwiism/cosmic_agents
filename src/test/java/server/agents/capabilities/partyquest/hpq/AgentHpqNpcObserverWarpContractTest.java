package server.agents.capabilities.partyquest.hpq;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHpqNpcObserverWarpContractTest {
    @Test
    void toryOffersOnlyGm6AChannelLocalWarpToTheActiveHpqLeader() throws IOException {
        String source = Files.readString(Path.of("scripts", "npc", "1012112.js"));

        assertTrue(source.contains("AgentHpqSessionRegistry.sessions()"));
        assertTrue(source.contains("cm.getPlayer().gmLevel() >= 6"));
        assertTrue(source.contains("selection == 4 && cm.getPlayer().gmLevel() >= 6"));
        assertTrue(source.contains("leader.getClient().getChannel() != player.getClient().getChannel()"));
        assertTrue(source.contains("leader.getEventInstance() == null"));
        assertTrue(source.contains("player.forceChangeMap(leader.getMap()"));
    }

    @Test
    void gm6ObserverCanFollowHpqTransitionsWithoutReceivingPartyRewards() throws IOException {
        String tory = Files.readString(Path.of("scripts", "npc", "1012112.js"));
        String tommy = Files.readString(Path.of("scripts", "npc", "1012113.js"));
        String growlie = Files.readString(Path.of("scripts", "npc", "1012114.js"));

        assertTrue(growlie.contains("canGmObserverFollowThrough"));
        assertTrue(growlie.contains("eim.getProperty(\"1stageclear\") != null"));
        assertTrue(growlie.contains("cm.warp(910010100, \"st00\")"));
        assertTrue(tommy.contains("eim.setProperty(\"hpqBonusEntered\", \"true\")"));
        assertTrue(tommy.contains("cm.warp(910010200, \"st00\")"));
        assertTrue(tory.contains("isRewardObserver(cm.getEventInstance(), cm.getPlayer())"));
        assertTrue(tory.contains("AgentHpqSessionRegistry.isRegisteredParticipant(player)"));
        assertTrue(tory.contains("AgentHpqSessionRegistry.beginRewardClaim(player)"));
        assertTrue(tory.contains("cm.warp(100000200)"));
    }
}
