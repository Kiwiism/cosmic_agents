package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;
import scripting.event.EventManager;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

class AgentKpqIsolationTest {
    @Test
    void concurrentEventInstancesOwnDifferentMapManagers() throws Exception {
        EventManager manager = mock(EventManager.class, RETURNS_DEEP_STUBS);
        EventInstanceManager first = new EventInstanceManager(manager, "Kerning0");
        EventInstanceManager second = new EventInstanceManager(manager, "Kerning1");
        Field maps = EventInstanceManager.class.getDeclaredField("mapManager");
        maps.setAccessible(true);

        assertNotSame(maps.get(first), maps.get(second));
    }

    @Test
    void kpqPackageDoesNotReachIntoTownLifeOrHuntingImplementations() throws Exception {
        Path packagePath = Path.of("src/main/java/server/agents/capabilities/partyquest/kpq");
        try (var files = Files.list(packagePath)) {
            String source = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (java.io.IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                    }).collect(java.util.stream.Collectors.joining("\n"));
            assertFalse(source.contains("server.agents.capabilities.townlife"));
            assertFalse(source.contains("server.agents.runtime.field"));
            assertTrue(source.contains("AgentAbstractExecutionScope.TOWN_LIFE"));
        }
    }

    @Test
    void kerningEventUsesTheKpqOwnedTwoLobbyPolicy() throws Exception {
        String script = Files.readString(Path.of("scripts/event/KerningPQ.js"));
        assertTrue(script.contains("AgentKpqLobbyPolicy.maxLobbies()"));
        org.junit.jupiter.api.Assertions.assertEquals(2, AgentKpqLobbyPolicy.maxLobbies());
    }

    @Test
    void clotoCountsOnlyTheLivePartyAndConsumesEveryHeldStageOnePass() throws Exception {
        String npc = Files.readString(Path.of("scripts/npc/9020001.js"));
        String event = Files.readString(Path.of("scripts/event/KerningPQ.js"));

        assertTrue(npc.contains("cm.getParty().getMembers().size() - 1"));
        assertTrue(npc.contains("!isLivePartyMember(eim, players.get(i))"));
        assertTrue(npc.contains("status == 0 && !managedParticipant && !isLivePartyMember(eim, cm.getPlayer())"));
        assertTrue(npc.contains("cm.gainItem(4001008, -heldPasses)"));
        assertTrue(event.contains("var itemSet = [4001007, 4001008]"));
    }

    @Test
    void clotoLetsOnlyGmSixObserversFollowAfterStageFiveWithoutAReward() throws Exception {
        String npc = Files.readString(Path.of("scripts/npc/9020001.js"));

        assertTrue(npc.contains("function canGmObserverFollowThrough"));
        assertTrue(npc.contains("player.gmLevel() >= 6"));
        assertTrue(npc.contains("!isLivePartyMember(eim, player)"));
        assertTrue(npc.contains("!AgentKpqSessionRegistry.isRegisteredParticipant(player)"));
        assertTrue(npc.contains("AgentKpqSessionRegistry.beginRewardClaim(player)"));
        assertTrue(npc.contains("player.getMapId() == 103000804"));
        assertTrue(npc.contains("eim.getProperty(\"5stageclear\") != null"));
        assertTrue(npc.contains("if (canGmObserverFollowThrough(eim, cm.getPlayer())) {\n"
                + "                cm.warp(103000805, \"st00\");"));
    }
}
