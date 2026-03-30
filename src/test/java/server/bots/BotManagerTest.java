package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotManagerTest {
    @Test
    void shouldParseTransferBotCommands() {
        BotManager.BotTransferCommand command = BotManager.matchBotTransferCommand("transfer Jason to Bob");

        assertNotNull(command);
        assertEquals("Jason", command.botName());
        assertEquals("Bob", command.targetName());
    }

    @Test
    void shouldStillAllowTransferWithoutTo() {
        BotManager.BotTransferCommand command = BotManager.matchBotTransferCommand("transfer Jason Bob");

        assertNotNull(command);
        assertEquals("Jason", command.botName());
        assertEquals("Bob", command.targetName());
    }

    @Test
    void shouldNotTreatGivePhrasesAsBotTransfers() {
        assertNull(BotManager.matchBotTransferCommand("give Jason Bob"));
        assertNull(BotManager.matchBotTransferCommand("give me flaming feather"));
        assertNull(BotManager.matchBotTransferCommand("give flaming feather"));
    }

    @Test
    void shouldKeepExistingGrindTargetOutsideSeekRangeOnNonAiTicks() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mock(Character.class);
        Monster target = mock(Monster.class);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.grindTarget = target;

        when(bot.getMap()).thenReturn(map);
        when(target.isAlive()).thenReturn(true);
        when(target.getMap()).thenReturn(map);

        Monster resolved = BotManager.resolveGrindTarget(entry, bot, false);

        assertSame(target, resolved);
    }
}
