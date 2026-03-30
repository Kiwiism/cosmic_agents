package server.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
