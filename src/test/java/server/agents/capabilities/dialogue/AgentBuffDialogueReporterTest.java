package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentBuffDialogueReporterTest {
    @Test
    void shouldComposeBuffChatSummaryLikeLegacyChat() {
        assertEquals(
                "buff pots on (cheap): active cider [watk+20]; bag warrior pill x2 [watk+5]",
                AgentBuffDialogueReporter.chatSummary(true, true,
                        "cider [watk+20]", "warrior pill x2 [watk+5]"));

        assertEquals(
                "buff pots off (max): active none; bag none in bag",
                AgentBuffDialogueReporter.chatSummary(false, false, "none", "none in bag"));
    }

    @Test
    void shouldComposeBuffDebugLinesLikeLegacyChat() {
        assertEquals(List.of(
                        "buff on(cheap); active: cider [watk+20]",
                        "bag: warrior pill x2 [watk+5]"),
                AgentBuffDialogueReporter.debugLines(true, true,
                        "cider [watk+20]", "warrior pill x2 [watk+5]"));

        assertEquals("buff off(best)", AgentBuffDialogueReporter.debugState(false, false));
    }
}
