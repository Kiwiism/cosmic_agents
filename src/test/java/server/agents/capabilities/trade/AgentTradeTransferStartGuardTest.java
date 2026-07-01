package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTradeTransferStartGuardTest {
    @Test
    void missingOwnerRepliesOwnerNotFound() {
        AgentTradeTransferStartGuard.Decision decision =
                AgentTradeTransferStartGuard.evaluate(false, false, false);

        assertFalse(decision.proceed());
        assertEquals(AgentDialogueCatalog.tradeOwnerNotFoundReply(), decision.reply());
    }

    @Test
    void agentBusyRepliesBotBusy() {
        AgentTradeTransferStartGuard.Decision decision =
                AgentTradeTransferStartGuard.evaluate(true, true, false);

        assertFalse(decision.proceed());
        assertEquals(AgentDialogueCatalog.tradeBotBusyReply(), decision.reply());
    }

    @Test
    void ownerBusyRepliesOwnerBusy() {
        AgentTradeTransferStartGuard.Decision decision =
                AgentTradeTransferStartGuard.evaluate(true, false, true);

        assertFalse(decision.proceed());
        assertEquals(AgentDialogueCatalog.tradeOwnerBusyReply(), decision.reply());
    }

    @Test
    void clearStateProceeds() {
        AgentTradeTransferStartGuard.Decision decision =
                AgentTradeTransferStartGuard.evaluate(true, false, false);

        assertTrue(decision.proceed());
        assertNull(decision.reply());
    }
}
