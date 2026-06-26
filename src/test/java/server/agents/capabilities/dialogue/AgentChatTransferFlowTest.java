package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatTransferFlowTest {
    @Test
    void shouldMatchTradeCommandsBeforeChoiceCommands() {
        AgentChatTransferFlow.TransferCommand command = AgentChatTransferFlow.matchTransferCommand("trade me scrolls");

        assertEquals(AgentChatTransferFlow.TransferMode.TRADE, command.mode());
        assertEquals("scrolls", command.category());
    }

    @Test
    void shouldMatchChoiceCommands() {
        AgentChatTransferFlow.TransferCommand command = AgentChatTransferFlow.matchTransferCommand("drop scrolls");

        assertEquals(AgentChatTransferFlow.TransferMode.CHOICE, command.mode());
        assertEquals("scrolls", command.category());
    }

    @Test
    void shouldIgnoreNonTransferCommands() {
        assertNull(AgentChatTransferFlow.matchTransferCommand("follow me"));
    }

    @Test
    void shouldRouteItemQueries() {
        TestItemQueryCallbacks callbacks = new TestItemQueryCallbacks();

        assertTrue(AgentChatTransferFlow.handleItemQuery("do you have orange potion?", callbacks));

        assertEquals("orange potion", callbacks.itemName);
    }

    @Test
    void shouldIgnoreNonItemQueries() {
        TestItemQueryCallbacks callbacks = new TestItemQueryCallbacks();

        assertFalse(AgentChatTransferFlow.handleItemQuery("follow me", callbacks));

        assertNull(callbacks.itemName);
    }

    @Test
    void shouldReplyWhenTransferCommandHasNoItems() {
        AgentChatTransferFlow.TransferResultDecision decision = AgentChatTransferFlow.transferResult(
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "scrolls"),
                false,
                0);
        var possibleReplies = AgentDialogueCatalog.noItemsReplies().stream()
                .map(template -> AgentInventoryDialogueReporter.noItemsReply("scrolls", java.util.List.of(template)))
                .toList();

        assertEquals(AgentChatTransferFlow.TransferResultAction.REPLY, decision.action());
        assertTrue(possibleReplies.contains(decision.reply()));
        assertNull(decision.category());
    }

    @Test
    void shouldStartTradeWhenTradeCommandHasItems() {
        AgentChatTransferFlow.TransferResultDecision decision = AgentChatTransferFlow.transferResult(
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "scrolls"),
                true,
                0);

        assertEquals(AgentChatTransferFlow.TransferResultAction.START_TRADE, decision.action());
        assertNull(decision.reply());
        assertNull(decision.category());
    }

    @Test
    void shouldPromptChoiceWhenChoiceCommandHasItems() {
        AgentChatTransferFlow.TransferResultDecision decision = AgentChatTransferFlow.transferResult(
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.CHOICE, "scrolls"),
                true,
                3);

        assertEquals(AgentChatTransferFlow.TransferResultAction.PROMPT_ITEM_CHOICE, decision.action());
        assertEquals("scrolls", decision.category());
        assertTrue(decision.reply().contains("3 scrolls"));
    }

    @Test
    void shouldMapItemQueryResultsToChoicePromptOrNoItemsReply() {
        AgentChatTransferFlow.TransferResultDecision found =
                AgentChatTransferFlow.itemQueryResult("name:orange potion", 2);
        AgentChatTransferFlow.TransferResultDecision missing =
                AgentChatTransferFlow.itemQueryResult("name:orange potion", 0);

        assertEquals(AgentChatTransferFlow.TransferResultAction.PROMPT_ITEM_CHOICE, found.action());
        assertEquals("name:orange potion", found.category());
        assertTrue(found.reply().contains("2 orange potion"));
        assertEquals(AgentChatTransferFlow.TransferResultAction.REPLY, missing.action());
        assertTrue(missing.reply().contains("orange potion"));
    }

    @Test
    void shouldIdentifyWeirdTrashTransferRequests() {
        AgentChatTransferFlow.TransferCommand command =
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "trash");

        assertTrue(AgentChatTransferFlow.shouldReplyWithWeirdTransfer(command, "show junk"));
        assertEquals(AgentDialogueCatalog.weirdTransferReply(), AgentChatTransferFlow.weirdTransferReply());
    }

    @Test
    void shouldNotTreatOtherTransferRequestsAsWeird() {
        AgentChatTransferFlow.TransferCommand tradeScrolls =
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "scrolls");
        AgentChatTransferFlow.TransferCommand choiceTrash =
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.CHOICE, "trash");

        assertFalse(AgentChatTransferFlow.shouldReplyWithWeirdTransfer(tradeScrolls, "show junk"));
        assertFalse(AgentChatTransferFlow.shouldReplyWithWeirdTransfer(choiceTrash, "show junk"));
        assertFalse(AgentChatTransferFlow.shouldReplyWithWeirdTransfer(choiceTrash, null));
    }

    private static final class TestItemQueryCallbacks implements AgentChatTransferFlow.ItemQueryCallbacks {
        private String itemName;

        @Override
        public void queryItem(String itemName) {
            this.itemName = itemName;
        }
    }
}
