package server.agents.capabilities.partyquest.lmpq;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLmpqScriptContractTest {
    @Test
    void eventAndClearScriptsRetainTheManagedCoordinatorContract() throws Exception {
        String event = Files.readString(Path.of("scripts/event/LudiMazePQ.js"));
        String clear = Files.readString(Path.of("scripts/npc/9103000.js"));
        assertTrue(event.contains("var minPlayers = 3, maxPlayers = 6"));
        assertTrue(event.contains("var minLevel = 51, maxLevel = 70"));
        assertTrue(event.contains("var eventTime = 15"));
        assertTrue(event.contains("Math.floor(Math.random() * 15)"));
        assertTrue(clear.contains("cm.isEventLeader()"));
        assertTrue(clear.contains("cm.hasItem(4001106, 30)"));
        assertTrue(clear.contains("isEventTeamTogether()"));
        assertTrue(clear.contains("giveEventPlayersExp(50 * qty)"));
    }
}
